package com.example.furniturecalculator.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.furniturecalculator.dto.OrderLineExportRequestDto;

// Выгрузка всего заказа (несколько позиций из корзины, см. change add-order-cart-screen, order-export-api)
// одним .xlsx: первый лист — сводка по всем позициям, затем по листу детализации на каждую позицию. Расчёт
// и построение листа детализации переиспользуют SpecificationExportService.writeConfigurationSheet — та же
// логика, что и у выгрузки одиночной спецификации, без дублирования (см. design.md, «Backend: выгрузка
// всего заказа одним файлом»).
@Service
public class OrderExportService {

    private static final String[] SUMMARY_COLUMNS = {"№", "Наименование", "Количество", "Цена за единицу, ₽", "Сумма, ₽"};

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
            Sheet summarySheet = workbook.createSheet("Сводка");
            List<SummaryRow> summaryRows = new ArrayList<>();
            List<String> usedSheetNames = new ArrayList<>();
            int position = 1;
            for (OrderLineExportRequestDto line : lines) {
                String sheetName = uniqueSheetName(position + ". " + line.displayName(), usedSheetNames);
                usedSheetNames.add(sheetName);
                Sheet detailSheet = workbook.createSheet(sheetName);
                SpecificationExportService.Totals totals =
                        specificationExportService.writeConfigurationSheet(workbook, detailSheet, line.specification());
                summaryRows.add(new SummaryRow(position, line.displayName(), line.quantity(), totals.retail()));
                position++;
            }
            writeSummarySheet(summarySheet, summaryRows);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сформировать файл заказа", e);
        }
    }

    private void writeSummarySheet(Sheet sheet, List<SummaryRow> rows) {
        CellStyle headerStyle = headerStyle(sheet.getWorkbook());
        CellStyle totalStyle = headerStyle(sheet.getWorkbook());

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < SUMMARY_COLUMNS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(SUMMARY_COLUMNS[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        BigDecimal orderTotal = BigDecimal.ZERO;
        for (SummaryRow row : rows) {
            BigDecimal lineTotal = row.unitRetailPrice().multiply(BigDecimal.valueOf(row.quantity()));
            orderTotal = orderTotal.add(lineTotal);
            Row dataRow = sheet.createRow(rowIndex++);
            dataRow.createCell(0).setCellValue(row.position());
            dataRow.createCell(1).setCellValue(row.displayName());
            dataRow.createCell(2).setCellValue(row.quantity());
            dataRow.createCell(3).setCellValue(row.unitRetailPrice().doubleValue());
            dataRow.createCell(4).setCellValue(lineTotal.doubleValue());
        }

        Row totalRow = sheet.createRow(rowIndex);
        Cell totalLabelCell = totalRow.createCell(1);
        totalLabelCell.setCellValue("Итого по заказу");
        totalLabelCell.setCellStyle(totalStyle);
        Cell totalValueCell = totalRow.createCell(4);
        totalValueCell.setCellValue(orderTotal.doubleValue());
        totalValueCell.setCellStyle(totalStyle);

        for (int i = 0; i < SUMMARY_COLUMNS.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // Имена листов Excel ограничены 31 символом, не могут содержать \/?*[]: и должны быть уникальны в
    // пределах книги — WorkbookUtil.createSafeSheetName вырезает недопустимые символы и обрезает длину;
    // уникальность (на случай двух позиций с одинаковым наименованием) добавляем сами суффиксом номера.
    private String uniqueSheetName(String desired, List<String> alreadyUsed) {
        String safe = WorkbookUtil.createSafeSheetName(desired);
        if (!alreadyUsed.contains(safe)) {
            return safe;
        }
        int suffix = 2;
        String candidate;
        do {
            String suffixText = " (" + suffix + ")";
            String truncatedBase = safe.length() > 31 - suffixText.length()
                    ? safe.substring(0, 31 - suffixText.length())
                    : safe;
            candidate = truncatedBase + suffixText;
            suffix++;
        } while (alreadyUsed.contains(candidate));
        return candidate;
    }

    private CellStyle headerStyle(org.apache.poi.ss.usermodel.Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private record SummaryRow(int position, String displayName, int quantity, BigDecimal unitRetailPrice) {
    }
}
