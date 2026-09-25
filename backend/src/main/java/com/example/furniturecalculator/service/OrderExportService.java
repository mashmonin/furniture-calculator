package com.example.furniturecalculator.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.furniturecalculator.dto.OrderLineExportRequestDto;

// Выгрузка всего заказа (несколько позиций из корзины, см. change update-order-export-flat-layout,
// order-export-api) одним .xlsx, одним листом — тот же состав данных и та же структура «строка конфигурации,
// сразу под ней детализация по компонентам», что и таблица корзины на фронте (см. frontend/src/CartScreen.tsx):
// главные колонки «№»/«Конфигурация»/«Параметры»/«Цена за ед.»/«Количество»/«Сумма» (без «Действия» — это
// кнопки, в файле не нужны), под каждой строкой — своя мини-таблица детализации из 9 колонок. Расчёт
// переиспользует SpecificationExportService.resolveConfigurationBreakdown — та же логика (и то же
// «backend пересчитывает заново, не доверяя фронту»), что и у выгрузки одиночной спецификации.
@Service
public class OrderExportService {

    private static final String[] MAIN_COLUMNS = {"№", "Конфигурация", "Параметры", "Цена за ед., ₽", "Количество", "Сумма, ₽"};
    private static final String[] DETAIL_COLUMNS = {
            "Элемент", "Наименование", "Размеры", "Цвет", "Кол-во", "Цена дилер, ₽", "Цена клиенту, ₽", "Сумма дилер, ₽", "Сумма клиенту, ₽"
    };

    private final SpecificationExportService specificationExportService;

    public OrderExportService(SpecificationExportService specificationExportService) {
        this.specificationExportService = specificationExportService;
    }

    public byte[] export(List<OrderLineExportRequestDto> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Список позиций заказа не должен быть пустым");
        }
        for (OrderLineExportRequestDto line : lines) {
            if (line.quantity() == null || line.quantity() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Количество позиции заказа должно быть не меньше 1");
            }
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Заказ");
            CellStyle mainRowStyle = boldStyle(workbook, (short) 12);
            CellStyle headerStyle = boldStyle(workbook, (short) -1);

            int rowIndex = writeHeaderRow(sheet, 0, MAIN_COLUMNS, headerStyle);
            int position = 1;
            BigDecimal orderRetailTotal = BigDecimal.ZERO;
            BigDecimal orderDealerTotal = BigDecimal.ZERO;
            for (OrderLineExportRequestDto line : lines) {
                SpecificationExportService.ConfigurationBreakdown breakdown =
                        specificationExportService.resolveConfigurationBreakdown(line.specification());
                BigDecimal quantity = BigDecimal.valueOf(line.quantity());
                BigDecimal lineSum = breakdown.totals().retail().multiply(quantity);
                orderRetailTotal = orderRetailTotal.add(lineSum);
                orderDealerTotal = orderDealerTotal.add(breakdown.totals().dealer().multiply(quantity));
                rowIndex = writeConfigurationRow(sheet, rowIndex, position, line, breakdown, lineSum, mainRowStyle);
                rowIndex = writeHeaderRow(sheet, rowIndex, DETAIL_COLUMNS, headerStyle);
                for (SpecificationExportService.DetailRow detailRow : breakdown.detailRows()) {
                    writeDetailRow(sheet.createRow(rowIndex++), detailRow);
                }
                rowIndex = writeDetailTotalsRow(sheet, rowIndex, breakdown.detailRows(), mainRowStyle);
                position++;
            }
            writeOrderTotalRow(sheet, rowIndex, orderDealerTotal, orderRetailTotal, mainRowStyle);

            int columnCount = Math.max(MAIN_COLUMNS.length, DETAIL_COLUMNS.length);
            for (int i = 0; i < columnCount; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сформировать файл заказа", e);
        }
    }

    private int writeHeaderRow(Sheet sheet, int rowIndex, String[] columns, CellStyle style) {
        Row headerRow = sheet.createRow(rowIndex);
        for (int i = 0; i < columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(style);
        }
        return rowIndex + 1;
    }

    private int writeConfigurationRow(
            Sheet sheet, int rowIndex, int position, OrderLineExportRequestDto line,
            SpecificationExportService.ConfigurationBreakdown breakdown, BigDecimal lineSum, CellStyle style) {
        BigDecimal unitPrice = breakdown.totals().retail();

        Row row = sheet.createRow(rowIndex);
        Cell positionCell = row.createCell(0);
        positionCell.setCellValue(position);
        positionCell.setCellStyle(style);
        // Ячейка «Конфигурация» — без стиля (не жирная, см. правку пользователя): наименование, и если у
        // позиции есть применимые теги атрибутов (line.attributeTags(), см. change
        // refine-order-export-layout), сразу после наименования через « — » и запятые между тегами.
        Cell nameCell = row.createCell(1);
        nameCell.setCellValue(configurationCellText(line));
        if (breakdown.dimensionsLabel() != null) {
            row.createCell(2).setCellValue(breakdown.dimensionsLabel());
        }
        row.createCell(3).setCellValue(unitPrice.doubleValue());
        row.createCell(4).setCellValue(line.quantity());
        row.createCell(5).setCellValue(lineSum.doubleValue());
        return rowIndex + 1;
    }

    // «Наименование — ТЕГ1, ТЕГ2» (см. правку пользователя) — attributeTags() может быть null (клиент, ещё
    // не отправляющий это поле, см. OrderLineExportRequestDto) или пустым списком; в обоих случаях — только
    // наименование, без « — ».
    private String configurationCellText(OrderLineExportRequestDto line) {
        List<String> tags = line.attributeTags();
        if (tags == null || tags.isEmpty()) {
            return line.displayName();
        }
        return line.displayName() + " — " + String.join(", ", tags);
    }

    private void writeDetailRow(Row row, SpecificationExportService.DetailRow detailRow) {
        row.createCell(0).setCellValue(detailRow.element());
        row.createCell(1).setCellValue(detailRow.name());
        if (detailRow.size() != null) {
            row.createCell(2).setCellValue(detailRow.size());
        }
        if (detailRow.colour() != null) {
            row.createCell(3).setCellValue(detailRow.colour());
        }
        row.createCell(4).setCellValue(detailRow.quantity());
        if (detailRow.priced()) {
            row.createCell(5).setCellValue(detailRow.dealerPrice().doubleValue());
            row.createCell(6).setCellValue(detailRow.retailPrice().doubleValue());
            row.createCell(7).setCellValue(detailRow.dealerSum().doubleValue());
            row.createCell(8).setCellValue(detailRow.retailSum().doubleValue());
        } else {
            // Цена не найдена — прочерк, тем же принципом, что и в SpecificationExportService.writeRow
            // (см. specs/door-configuration-export, «Ненайденная цена отображается прочерком»).
            row.createCell(5).setCellValue("—");
            row.createCell(6).setCellValue("—");
            row.createCell(7).setCellValue("—");
            row.createCell(8).setCellValue("—");
        }
    }

    // Строка «Итого» под детализацией одной конфигурации (см. правку пользователя) — на месте прежней
    // пустой строки-разделителя перед следующей конфигурацией: сумма столбцов «Сумма дилер»/«Сумма клиенту»
    // по всем строкам детализации этой позиции (тем же принципом, что и итоговая строка «Итого» в
    // раскрываемой детализации позиции корзины на фронте, см. frontend/src/CartScreen.tsx, DetailTable —
    // ненайденные цены считаются как 0, не пропускаются и не прерывают суммирование).
    private int writeDetailTotalsRow(
            Sheet sheet, int rowIndex, List<SpecificationExportService.DetailRow> detailRows, CellStyle style) {
        BigDecimal dealerTotal = BigDecimal.ZERO;
        BigDecimal retailTotal = BigDecimal.ZERO;
        for (SpecificationExportService.DetailRow detailRow : detailRows) {
            if (detailRow.priced()) {
                dealerTotal = dealerTotal.add(detailRow.dealerSum());
                retailTotal = retailTotal.add(detailRow.retailSum());
            }
        }

        Row row = sheet.createRow(rowIndex);
        Cell labelCell = row.createCell(1);
        labelCell.setCellValue("Итого");
        labelCell.setCellStyle(style);
        Cell dealerCell = row.createCell(7);
        dealerCell.setCellValue(dealerTotal.doubleValue());
        dealerCell.setCellStyle(style);
        Cell retailCell = row.createCell(8);
        retailCell.setCellValue(retailTotal.doubleValue());
        retailCell.setCellStyle(style);
        return rowIndex + 1;
    }

    // Итог по заказу — последней строкой листа, после блоков всех позиций: ровно три заполненные, идущие
    // подряд ячейки, начиная с колонки A (см. правку пользователя) — «Итого по заказу» (жирным, единственная
    // жирная ячейка строки), дилерская сумма по заказу, клиентская (розничная) сумма по заказу (обе — без
    // жирного начертания). Дилерская сумма — сумма breakdown.totals().dealer() каждой позиции с учётом её
    // количества; клиентская — сумма столбца «Сумма» всех строк конфигураций, тем же принципом, что и
    // «Итого по заказу» под таблицей позиций на экране корзины (см. order-cart-ui, «Сводка и итог по заказу»).
    private void writeOrderTotalRow(Sheet sheet, int rowIndex, BigDecimal dealerTotal, BigDecimal retailTotal, CellStyle labelStyle) {
        Row row = sheet.createRow(rowIndex);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue("Итого по заказу");
        labelCell.setCellStyle(labelStyle);
        row.createCell(1).setCellValue(dealerTotal.doubleValue());
        row.createCell(2).setCellValue(retailTotal.doubleValue());
    }

    // fontHeightPoints — размер шрифта в пунктах, если > 0 (см. строку конфигурации, крупнее заголовков
    // колонок — тем же принципом, что и sectionStyle/headerStyle в SpecificationExportService); иначе —
    // размер по умолчанию книги.
    private CellStyle boldStyle(XSSFWorkbook workbook, short fontHeightPoints) {
        Font font = workbook.createFont();
        font.setBold(true);
        if (fontHeightPoints > 0) {
            font.setFontHeightInPoints(fontHeightPoints);
        }
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }
}
