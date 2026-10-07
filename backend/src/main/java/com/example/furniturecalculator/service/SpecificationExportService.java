package com.example.furniturecalculator.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.furniturecalculator.domain.FramePost;
import com.example.furniturecalculator.domain.LeafType;
import com.example.furniturecalculator.dto.ComponentPriceDto;
import com.example.furniturecalculator.dto.ComponentSelectionDto;
import com.example.furniturecalculator.dto.DecorativeElementPriceDto;
import com.example.furniturecalculator.dto.HardwarePriceDto;
import com.example.furniturecalculator.dto.SpecificationExportRequestDto;

// Сборка .xlsx-файла спецификации (см. change add-specification-export). Валидация и расчёт цены здесь не
// дублируются — этот сервис только вызывает DoorConfigurationPricingService.resolveSpecificationComponents
// и раскладывает уже посчитанные ResolvedComponent/HardwarePriceDto/DecorativeElementPriceDto по строкам
// листа (см. design.md, «Переиспользование ценовой логики»). Один лист: до пяти разделов с компонентами,
// затем, если есть, отдельный завершающий раздел «Надбавки к цене полотна» — общие колонки одинаковы во
// всех разделах.
@Service
public class SpecificationExportService {

    // См. frontend/src/App.tsx, FRAME_POST_NAME_WITHOUT_LENGTH — то же исключение отображения длины для
    // позиции «Комплект зарезных стоек», что и в разбивке результата расчёта на фронте (см.
    // door-configurator-ui, «Отображение позиций короба»).
    private static final String FRAME_POST_NAME_WITHOUT_LENGTH = "Комплект зарезных стоек";

    private static final String SURCHARGES_SECTION_TITLE = "Надбавки к цене полотна";

    private static final String[] COLUMNS = {
            "Наименование", "Измерения, мм", "Цвет", "Количество",
            "Итоговая цена, ₽", "Базовая цена розница, ₽", "Цена дилер, ₽", "Базовая цена дилер, ₽"
    };

    private final DoorConfigurationPricingService pricingService;

    public SpecificationExportService(DoorConfigurationPricingService pricingService) {
        this.pricingService = pricingService;
    }

    // @Transactional здесь обязателен, а не только на resolveSpecificationComponents: ColourOption.colourType
    // и FramePost.postType — ленивые (FetchType.LAZY) связи. Если бы транзакция ограничивалась только
    // resolveSpecificationComponents, сессия Hibernate закрывалась бы до того, как componentRow/
    // frameSectionRows обращаются к этим прокси (getColourType()/getPostType()) при сборке строк листа —
    // это и вызывало LazyInitializationException. REQUIRED-транзакция здесь присоединяет к себе вложенный
    // вызов resolveSpecificationComponents, продлевая сессию на всё время сборки .xlsx.
    @Transactional(readOnly = true)
    public byte[] export(SpecificationExportRequestDto request) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Спецификация");
            writeConfigurationSheet(workbook, sheet, request);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сформировать файл спецификации", e);
        }
    }

    // Пишет полную спецификацию одной конфигурации (все разделы компонентов, итоговую сумму, надбавки) в
    // уже созданный лист workbook — выделено из export() (см. change add-order-cart-screen, design.md,
    // «Backend: выгрузка всего заказа одним файлом»), чтобы OrderExportService мог переиспользовать ровно ту
    // же логику построчно для каждой позиции заказа, не дублируя её. @Transactional — по той же причине, что
    // и у export(): ленивые связи компонентов должны резолвиться в той же транзакции, где выполнялся
    // resolveSpecificationComponents. Package-private, а не private — вызывается из OrderExportService
    // (тот же пакет service) через инжектированный бин, а не self-invocation, поэтому проходит через
    // Spring-прокси и получает собственную транзакцию на каждый вызов (на каждую позицию заказа).
    @Transactional(readOnly = true)
    Totals writeConfigurationSheet(XSSFWorkbook workbook, Sheet sheet, SpecificationExportRequestDto request) {
        SpecificationComponents components = pricingService.resolveSpecificationComponents(request);
        CellStyle sectionStyle = sectionStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);

        int rowIndex = writeSection(sheet, 0, "Полотно и опции полотна", headerStyle, sectionStyle,
                leafSectionRows(components, request), true);

        if (components.frame() != null) {
            rowIndex = writeSection(sheet, rowIndex, "Короб и обрамление", headerStyle, sectionStyle,
                    frameSectionRows(components), true);
        }

        List<SpecRow> casingRows = casingSectionRows(components, request);
        if (!casingRows.isEmpty()) {
            rowIndex = writeSection(sheet, rowIndex, "Наличники и доборы", headerStyle, sectionStyle, casingRows, true);
        }

        if (!components.decorativeElements().isEmpty()) {
            rowIndex = writeSection(sheet, rowIndex, "Декоративные элементы", headerStyle, sectionStyle,
                    decorativeElementSectionRows(components.decorativeElements()), true);
        }

        if (!components.hardware().isEmpty()) {
            rowIndex = writeSection(sheet, rowIndex, "Фурнитура", headerStyle, sectionStyle,
                    hardwareSectionRows(components.hardware()), true);
        }

        // Итоговая сумма — после всех разделов с компонентами, но до надбавок (см. change
        // add-specification-export): отдельный блок с двумя колонками вместо общей 8-колоночной раскладки,
        // поскольку у итога нет ни наименования, ни размеров — только сумма по всем ценам файла.
        Totals totals = totals(components);
        rowIndex = writeTotalsSection(sheet, rowIndex, totals, sectionStyle, headerStyle);

        // Надбавки к цене полотна — отдельным разделом под всей таблицей расчётов, а не строками внутри
        // «Полотно и опции полотна» (там теперь — строки выбранных опций, см. leafSectionRows). Без
        // строки заголовков колонок (includeColumnHeaders=false) — колонки размеров/цвета/количества/цены
        // не имеют смысла для этих строк (только наименование и процент).
        List<LeafPriceSurcharge> surcharges = components.leaf().surcharges();
        if (!surcharges.isEmpty()) {
            List<SpecRow> surchargeRows = surcharges.stream().map(this::surchargeRow).collect(Collectors.toList());
            writeSection(sheet, rowIndex, SURCHARGES_SECTION_TITLE, headerStyle, sectionStyle, surchargeRows, false);
        }

        for (int i = 0; i < COLUMNS.length; i++) {
            sheet.autoSizeColumn(i);
        }
        return totals;
    }

    // Плоская построчная детализация конфигурации по компонентам (см. change update-order-export-flat-layout,
    // order-export-api) — тот же состав данных, что фронтенд строит для раскрываемой таблицы детализации
    // позиции корзины (см. frontend/src/ConfiguratorScreen.tsx, buildCartItemContent/detailRows), но
    // независимо пересчитанный на backend через resolveSpecificationComponents (см. writeConfigurationSheet
    // выше — тот же принцип «не доверять уже показанным на экране числам»). В отличие от writeConfigurationSheet
    // (сгруппированные по разделам строки с 8 колонками для единичной выгрузки, см. door-configuration-export)
    // здесь — один плоский список строк с колонкой «Элемент» и явной суммой по строке, используемый только
    // OrderExportService для листа заказа целиком.
    @Transactional(readOnly = true)
    ConfigurationBreakdown resolveConfigurationBreakdown(SpecificationExportRequestDto request) {
        SpecificationComponents components = pricingService.resolveSpecificationComponents(request);
        Totals totals = totals(components);
        String dimensionsLabel = leafDimensionsLabel(components, request);
        List<DetailRow> detailRows = buildDetailRows(components, request, dimensionsLabel);
        ResolvedComponent leaf = components.leaf();
        String collectionName = leaf.type() instanceof LeafType leafType && leafType.getCollection() != null
                ? leafType.getCollection().getName()
                : null;
        String leafColourName = leaf.colourOption() != null ? leaf.colourOption().getColourType().getName() : null;
        return new ConfigurationBreakdown(
                leaf.type().getName(), dimensionsLabel, totals, detailRows, collectionName, leafColourName);
    }

    // Длина × высота × толщина полотна через « × » (см. order-cart-ui, «Таблица позиций корзины» —
    // dimensionsLabel строится на фронте тем же способом и тем же разделителем, без единиц измерения).
    private String leafDimensionsLabel(SpecificationComponents components, SpecificationExportRequestDto request) {
        ComponentSelectionDto leafSelection = request.leaf() != null ? request.leaf() : ComponentSelectionDto.EMPTY;
        ResolvedComponent leaf = components.leaf();
        BigDecimal length = leaf.lengthOption() != null ? leaf.lengthOption().getValue() : leafSelection.customLengthValueMm();
        BigDecimal height = components.leafHeightValue();
        BigDecimal thickness = leaf.thicknessOption() != null ? leaf.thicknessOption().getValue() : null;
        List<BigDecimal> values = new ArrayList<>();
        if (length != null) {
            values.add(length);
        }
        if (height != null) {
            values.add(height);
        }
        if (thickness != null) {
            values.add(thickness);
        }
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().map(value -> value.stripTrailingZeros().toPlainString()).collect(Collectors.joining(" × "));
    }

    private List<DetailRow> buildDetailRows(
            SpecificationComponents components, SpecificationExportRequestDto request, String leafDimensionsLabel) {
        List<DetailRow> rows = new ArrayList<>();
        ResolvedComponent leaf = components.leaf();
        rows.add(detailRow("Полотно", leaf.type().getName(), leafDimensionsLabel, formatColour(leaf), 1, leaf.price()));

        // Исполнение зеркала/вид остекления сразу под полотном — тот же источник (leaf.selectedOptions(),
        // резолвится один раз в DoorConfigurationPricingService), что и у leafSectionRows одиночной выгрузки
        // (см. change show-mirror-glazing-in-order-detail-rows). Полотно не может иметь выбранными оба
        // одновременно (зеркало только для глухих, остекление только для остеклённых, см.
        // door-configuration-catalog, «Владение mirror_finish_option»), поэтому selectedOptions() содержит
        // не более одного значения — какое именно, определяется по id из запроса, а не по позиции в списке.
        ComponentSelectionDto leafSelection = request.leaf() != null ? request.leaf() : ComponentSelectionDto.EMPTY;
        if (!leaf.selectedOptions().isEmpty()) {
            String optionLabel = leafSelection.mirrorFinishTypeId() != null ? "Исполнение зеркала" : "Вид остекления";
            rows.add(leafOptionRow(optionLabel, leaf.selectedOptions().get(0)));
        }

        if (components.edge() != null) {
            ResolvedComponent edge = components.edge();
            rows.add(detailRow("Кромка", edge.type().getName(), null, formatColour(edge), 1, edge.price()));
        }

        if (components.frame() != null) {
            // Позиции состава короба (frame.framePosts(), включая «Комплект зарезных стоек») здесь намеренно
            // НЕ выводятся отдельными строками (см. правку пользователя, order-cart-ui, «Разворачиваемая
            // детализация позиции корзины» — тот же принцип, что и в детализации корзины на фронте, откуда
            // этот метод и переиспользуется для листа заказа): цена компонента «Короб» уже включает их сумму
            // целиком (см. door-configuration-api, «Стоимость короба как сумма цен его стоек»), поэтому
            // одной строки «Короб» достаточно. Состав по-прежнему выгружается построчно в ОДИНОЧНОЙ
            // спецификации (см. frameSectionRows ниже, door-configuration-export, «Состав короба выгружается
            // построчно») — это отдельная, не затронутая этой правкой возможность.
            ResolvedComponent frame = components.frame();
            String frameSize = plainNumber(resolvedHeight(frame, null));
            rows.add(detailRow("Короб", frame.type().getName(), frameSize, formatColour(frame), 1, frame.price()));
        }

        if (components.doorCasing() != null) {
            ResolvedComponent casing = components.doorCasing();
            ComponentSelectionDto selection = request.doorCasing() != null ? request.doorCasing() : ComponentSelectionDto.EMPTY;
            String size = plainNumber(resolvedLength(casing, selection));
            rows.add(detailRow("Наличник", casing.type().getName(), size, formatColour(casing), casing.quantity(), casing.price()));
        }

        if (components.frameExtensions() != null) {
            ResolvedComponent extensions = components.frameExtensions();
            ComponentSelectionDto selection = request.frameExtensions() != null ? request.frameExtensions() : ComponentSelectionDto.EMPTY;
            String size = plainNumber(resolvedLength(extensions, selection));
            rows.add(detailRow(
                    "Добор", extensions.type().getName(), size, formatColour(extensions), extensions.quantity(), extensions.price()));
        }

        for (DecorativeElementPriceDto item : components.decorativeElements()) {
            // Только название типа, без категории (см. правку пользователя) — в отличие от фурнитуры.
            String name = item.type().name();
            BigDecimal retailUnit = perUnit(item.retailPrice(), item.quantity());
            BigDecimal dealerUnit = perUnit(item.dealerPrice(), item.quantity());
            rows.add(new DetailRow("Декоративные элементы", name,
                    formatDimensions(item.lengthMm(), item.widthMm(), item.thicknessMm()), null, item.quantity(), true,
                    retailUnit, dealerUnit, item.retailPrice(), item.dealerPrice(), true));
        }

        for (HardwarePriceDto item : components.hardware()) {
            String name = item.category().name() + " — " + item.type().name();
            BigDecimal retailUnit = perUnit(item.retailPrice(), item.quantity());
            BigDecimal dealerUnit = perUnit(item.dealerPrice(), item.quantity());
            rows.add(new DetailRow("Фурнитура", name, null, item.colourName(), item.quantity(), true,
                    retailUnit, dealerUnit, item.retailPrice(), item.dealerPrice(), true, item.article()));
        }

        return rows;
    }

    // Строка детализации по компоненту с ценой из ComponentPriceDto (уже посчитанной с учётом quantity для
    // doorCasing/frameExtensions, см. комментарий в totals()) — цена «за единицу» получается делением
    // обратно на quantity (для leaf/edge/frame/postов quantity=1, деление не требуется).
    private DetailRow detailRow(String element, String name, String size, String colour, int quantity, ComponentPriceDto price) {
        if (!price.priced()) {
            return new DetailRow(element, name, size, colour, quantity, false, null, null, null, null, true);
        }
        BigDecimal retailSum = price.retailPrice();
        BigDecimal dealerSum = price.dealerPrice();
        return new DetailRow(element, name, size, colour, quantity, true, perUnit(retailSum, quantity), perUnit(dealerSum, quantity),
                retailSum, dealerSum, true);
    }

    // Строка без применимой цены (исполнение зеркала/вид остекления под полотном, см. buildDetailRows) —
    // priceApplicable=false, ценовые ячейки остаются пустыми, а не «—» (см. правку пользователя — лишние
    // прочерки мешают восприятию; тот же принцип, что и у SpecRow.priceApplicable в одиночной выгрузке).
    private DetailRow leafOptionRow(String element, String name) {
        return new DetailRow(element, name, null, null, 1, false, null, null, null, null, false);
    }

    private BigDecimal perUnit(BigDecimal total, int quantity) {
        return quantity == 1 ? total : total.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
    }

    // Высота короба/наличника/добора — из каталожной опции либо, если недоступна напрямую (короб «Фантом»
    // мирроритcя от высоты полотна), из heightMmOverride (см. componentRow выше — тот же принцип).
    private BigDecimal resolvedHeight(ResolvedComponent component, BigDecimal externalOverride) {
        if (externalOverride != null) {
            return externalOverride;
        }
        return component.heightOption() != null ? component.heightOption().getValue() : component.heightMmOverride();
    }

    // Размер наличника/добора — это ДЛИНА (подбирается по диапазону, привязанному к высоте полотна, см.
    // DoorConfigurationPricingService.addComponentIfPresent, requireLengthWithinLeafRange), а не высота: у
    // этих компонентов heightOption всегда null (см. componentRow выше — та же самая логика для одиночной
    // выгрузки), в отличие от короба, где именно высота — значимый размер (см. resolvedHeight).
    private BigDecimal resolvedLength(ResolvedComponent component, ComponentSelectionDto selection) {
        return component.lengthOption() != null ? component.lengthOption().getValue() : selection.customLengthValueMm();
    }

    private String plainNumber(BigDecimal value) {
        return value != null ? value.stripTrailingZeros().toPlainString() : null;
    }

    // includeColumnHeaders=false — для раздела «Надбавки к цене полотна»: его строки несут только
    // наименование и процент, колонки размеров/цвета/количества/цены к ним неприменимы, поэтому строка
    // заголовков колонок для этого раздела не пишется вовсе (а не просто оставляется пустой).
    private int writeSection(
            Sheet sheet, int rowIndex, String title, CellStyle headerStyle, CellStyle sectionStyle, List<SpecRow> rows,
            boolean includeColumnHeaders) {
        Row sectionRow = sheet.createRow(rowIndex++);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue(title);
        sectionCell.setCellStyle(sectionStyle);

        if (includeColumnHeaders) {
            Row headerRow = sheet.createRow(rowIndex++);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(COLUMNS[i]);
                cell.setCellStyle(headerStyle);
            }
        }

        for (SpecRow row : rows) {
            writeRow(sheet.createRow(rowIndex++), row);
        }
        // Пустая строка-разделитель перед следующим разделом.
        return rowIndex + 1;
    }

    // Колонки итога — те же индексы, что и «Итоговая цена, ₽» (E, COLUMNS[4]) и «Цена дилер, ₽» (G,
    // COLUMNS[6]) в общей раскладке разделов с компонентами, чтобы суммы визуально стояли строго под
    // соответствующими колонками цены, а не в произвольных A/B.
    private static final int TOTALS_RETAIL_COLUMN = 4;
    private static final int TOTALS_DEALER_COLUMN = 6;

    // Отдельный блок «Итоговая сумма» — своя раскладка из двух колонок («Итоговая цена», «Итоговая цена
    // дилер»), а не переиспользование 8-колоночной COLUMNS целиком: у итога нет ни наименования, ни
    // размеров/цвета/количества, только сумма — но сами колонки сумм совмещены с колонками E/G выше.
    private int writeTotalsSection(Sheet sheet, int rowIndex, Totals totals, CellStyle sectionStyle, CellStyle headerStyle) {
        Row titleRow = sheet.createRow(rowIndex++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Итоговая сумма");
        titleCell.setCellStyle(sectionStyle);

        Row headerRow = sheet.createRow(rowIndex++);
        Cell retailHeader = headerRow.createCell(TOTALS_RETAIL_COLUMN);
        retailHeader.setCellValue("Итоговая цена");
        retailHeader.setCellStyle(headerStyle);
        Cell dealerHeader = headerRow.createCell(TOTALS_DEALER_COLUMN);
        dealerHeader.setCellValue("Итоговая цена дилер");
        dealerHeader.setCellStyle(headerStyle);

        Row dataRow = sheet.createRow(rowIndex++);
        dataRow.createCell(TOTALS_RETAIL_COLUMN).setCellValue(totals.retail().doubleValue());
        dataRow.createCell(TOTALS_DEALER_COLUMN).setCellValue(totals.dealer().doubleValue());

        return rowIndex + 1;
    }

    // Сумма итоговых (не базовых) цен по всем строкам файла, кроме строк надбавок/выбранных опций (у них нет
    // собственной цены) — компоненты без найденной цены (priced=false) в сумму не входят, тем же принципом,
    // что и в DoorConfigurationPricingService.sumRetail/sumDealer (переиспользовать их напрямую нельзя: они
    // принимают List<ComponentPriceDto>, а здесь дополнительно нужно учесть позиции состава короба и
    // фурнитуру, которых в этом списке нет).
    private Totals totals(SpecificationComponents components) {
        BigDecimal retail = pricedRetail(components.leaf());
        BigDecimal dealer = pricedDealer(components.leaf());
        if (components.edge() != null) {
            retail = retail.add(pricedRetail(components.edge()));
            dealer = dealer.add(pricedDealer(components.edge()));
        }
        if (components.frame() != null) {
            retail = retail.add(pricedRetail(components.frame()));
            dealer = dealer.add(pricedDealer(components.frame()));
            for (FramePost post : components.frame().framePosts()) {
                retail = retail.add(post.getRetailPrice());
                dealer = dealer.add(post.getDealerPrice());
            }
        }
        if (components.doorCasing() != null) {
            retail = retail.add(pricedRetail(components.doorCasing()));
            dealer = dealer.add(pricedDealer(components.doorCasing()));
        }
        if (components.frameExtensions() != null) {
            retail = retail.add(pricedRetail(components.frameExtensions()));
            dealer = dealer.add(pricedDealer(components.frameExtensions()));
        }
        for (DecorativeElementPriceDto item : components.decorativeElements()) {
            retail = retail.add(item.retailPrice());
            dealer = dealer.add(item.dealerPrice());
        }
        for (HardwarePriceDto item : components.hardware()) {
            retail = retail.add(item.retailPrice());
            dealer = dealer.add(item.dealerPrice());
        }
        return new Totals(retail, dealer);
    }

    private BigDecimal pricedRetail(ResolvedComponent component) {
        return component.price().priced() ? component.price().retailPrice() : BigDecimal.ZERO;
    }

    private BigDecimal pricedDealer(ResolvedComponent component) {
        return component.price().priced() ? component.price().dealerPrice() : BigDecimal.ZERO;
    }

    // Package-private (не private) — возвращается из writeConfigurationSheet, которую вызывает
    // OrderExportService (тот же пакет service) для сборки строки сводки заказа с итоговой ценой позиции.
    record Totals(BigDecimal retail, BigDecimal dealer) {
    }

    // Одна строка плоской детализации конфигурации по компоненту (см. resolveConfigurationBreakdown выше) —
    // retailPrice/dealerPrice здесь уже цена ЗА ЕДИНИЦУ (в отличие от ComponentPriceDto/HardwarePriceDto,
    // где для doorCasing/frameExtensions/фурнитуры цена уже умножена на quantity), retailSum/dealerSum — с
    // учётом quantity этой строки. priced=false — цена не найдена, все ценовые поля null (см. OrderExportService,
    // где это превращается в «—», тем же принципом, что и в writeRow/SpecRow выше). priceApplicable=false —
    // цена в принципе неприменима к этой строке (например, исполнение зеркала/вид остекления под полотном,
    // см. leafOptionRow) — в отличие от priced=false, ценовые ячейки остаются пустыми, а не «—» (тот же
    // принцип, что и SpecRow.priceApplicable в одиночной выгрузке, см. правку пользователя).
    record DetailRow(
            String element,
            String name,
            String size,
            String colour,
            int quantity,
            boolean priced,
            BigDecimal retailPrice,
            BigDecimal dealerPrice,
            BigDecimal retailSum,
            BigDecimal dealerSum,
            boolean priceApplicable,
            // Артикул цветового варианта фурнитуры (только для строк «Фурнитура», иначе null) — по нему КП
            // подбирает фото (см. change add-hardware-photos-to-commercial-offer).
            String article) {

        DetailRow(
                String element, String name, String size, String colour, int quantity, boolean priced,
                BigDecimal retailPrice, BigDecimal dealerPrice, BigDecimal retailSum, BigDecimal dealerSum,
                boolean priceApplicable) {
            this(element, name, size, colour, quantity, priced, retailPrice, dealerPrice, retailSum, dealerSum,
                    priceApplicable, null);
        }
    }

    // Итог по одной конфигурации заказа для OrderExportService: displayName/dimensionsLabel — для строки
    // конфигурации листа заказа (те же значения, что и CartItem.displayName/dimensionsLabel на фронте),
    // totals — цена за единицу (используется и как «Цена за ед.», и умножается на quantity позиции для
    // «Сумма»), detailRows — построчная детализация под строкой конфигурации.
    // collectionName/leafColourName (см. change add-commercial-offer-pdf-export) — коллекция полотна и название
    // его фронтального цвета (null, если не применимы), по которым КП подбирает изображение двери.
    record ConfigurationBreakdown(
            String displayName, String dimensionsLabel, Totals totals, List<DetailRow> detailRows,
            String collectionName, String leafColourName) {
    }

    private void writeRow(Row dataRow, SpecRow row) {
        dataRow.createCell(0).setCellValue(row.name());
        if (row.dimensions() != null) {
            dataRow.createCell(1).setCellValue(row.dimensions());
        }
        if (row.colour() != null) {
            dataRow.createCell(2).setCellValue(row.colour());
        }
        if (row.quantity() != null) {
            dataRow.createCell(3).setCellValue(row.quantity());
        }
        if (!row.priceApplicable()) {
            // Не применимо (например, строка выбранной опции или надбавки под таблицей — у них нет
            // собственной цены, она уже учтена в цене строки полотна) — ячейки остаются пустыми, в отличие
            // от прочерка, означающего именно ненайденную цену компонента.
            return;
        }
        if (row.priced()) {
            dataRow.createCell(4).setCellValue(row.retailPrice().doubleValue());
            dataRow.createCell(5).setCellValue(row.baseRetailPrice().doubleValue());
            dataRow.createCell(6).setCellValue(row.dealerPrice().doubleValue());
            dataRow.createCell(7).setCellValue(row.baseDealerPrice().doubleValue());
        } else {
            // Цена не найдена — прочерк вместо числа, строка не пропускается (см. specs/door-configuration-export,
            // «Ненайденная цена отображается прочерком»).
            dataRow.createCell(4).setCellValue("—");
            dataRow.createCell(5).setCellValue("—");
            dataRow.createCell(6).setCellValue("—");
            dataRow.createCell(7).setCellValue("—");
        }
    }

    private List<SpecRow> leafSectionRows(SpecificationComponents components, SpecificationExportRequestDto request) {
        List<SpecRow> rows = new ArrayList<>();
        ComponentSelectionDto leafSelection = request.leaf() != null ? request.leaf() : ComponentSelectionDto.EMPTY;
        rows.add(componentRow(components.leaf(), leafSelection, components.leafHeightValue(), false));
        // Строки выбранных опций полотна — на месте, где раньше показывалась разбивка надбавок (см. change
        // add-specification-export): тип открывания, если реверс (по умолчанию — «Прямое», ничего не
        // показываем), затем исполнение зеркала/вид остекления, если выбраны (см.
        // DoorConfigurationPricingService.leafSelectedOptions — резолвятся один раз там же, где и множители
        // надбавок, не заново — но голым наименованием, без префикса-категории; префикс добавляется здесь,
        // потому что у этой (одиночной) выгрузки строка — единственная ячейка «Наименование: значение», в
        // отличие от двухколоночной детализации заказа, см. buildDetailRows). Сами надбавки (проценты) —
        // отдельным разделом внизу файла (см. export()).
        if (Boolean.TRUE.equals(request.isReverse())) {
            rows.add(labelRow("Тип открывания: Реверс"));
        }
        if (!components.leaf().selectedOptions().isEmpty()) {
            String optionPrefix = leafSelection.mirrorFinishTypeId() != null ? "Исполнение зеркала: " : "Вид остекления: ";
            rows.add(labelRow(optionPrefix + components.leaf().selectedOptions().get(0)));
        }
        if (components.edge() != null) {
            ComponentSelectionDto edgeSelection = request.edge() != null ? request.edge() : ComponentSelectionDto.EMPTY;
            rows.add(componentRow(components.edge(), edgeSelection, null, false));
        }
        return rows;
    }

    // Строка без размеров/цвета/количества/цены — только наименование, priceApplicable=false. Используется и
    // для строк выбранных опций под полотном, и для строк надбавок в завершающем разделе файла.
    private SpecRow labelRow(String label) {
        return new SpecRow("  " + label, null, null, null, false, null, null, null, null, false);
    }

    private SpecRow surchargeRow(LeafPriceSurcharge surcharge) {
        String percentText = surcharge.percent().stripTrailingZeros().toPlainString();
        return labelRow(surcharge.label() + ": +" + percentText + "%");
    }

    private List<SpecRow> frameSectionRows(SpecificationComponents components) {
        List<SpecRow> rows = new ArrayList<>();
        ResolvedComponent frame = components.frame();
        rows.add(componentRow(frame, ComponentSelectionDto.EMPTY, null, false));
        for (FramePost post : frame.framePosts()) {
            String name = post.getPostType().getName();
            // «Комплект зарезных стоек» — уже готовый набор, считается как 1 штука, а не post.getQuantity()
            // (см. тот же принцип в buildDetailRows выше).
            boolean isKitPost = FRAME_POST_NAME_WITHOUT_LENGTH.equals(name);
            BigDecimal length = isKitPost ? null : post.getLength();
            String dimensions = formatDimensions(length, null, null);
            int quantity = isKitPost ? 1 : post.getQuantity();
            // У позиций короба надбавок никогда не бывает — базовая цена совпадает с итоговой.
            rows.add(new SpecRow(name, dimensions, null, quantity, true,
                    post.getRetailPrice(), post.getRetailPrice(), post.getDealerPrice(), post.getDealerPrice(), true));
        }
        return rows;
    }

    private List<SpecRow> casingSectionRows(SpecificationComponents components, SpecificationExportRequestDto request) {
        List<SpecRow> rows = new ArrayList<>();
        if (components.doorCasing() != null) {
            ComponentSelectionDto selection = request.doorCasing() != null ? request.doorCasing() : ComponentSelectionDto.EMPTY;
            rows.add(componentRow(components.doorCasing(), selection, null, true));
        }
        if (components.frameExtensions() != null) {
            ComponentSelectionDto selection =
                    request.frameExtensions() != null ? request.frameExtensions() : ComponentSelectionDto.EMPTY;
            rows.add(componentRow(components.frameExtensions(), selection, null, true));
        }
        return rows;
    }

    private List<SpecRow> hardwareSectionRows(List<HardwarePriceDto> hardware) {
        List<SpecRow> rows = new ArrayList<>();
        for (HardwarePriceDto item : hardware) {
            String name = item.category().name() + " — " + item.type().name();
            // У фурнитуры надбавок никогда не бывает — базовая цена совпадает с итоговой (уже с учётом
            // количества, как и в ответе POST /api/hardware/price).
            rows.add(new SpecRow(name, null, item.colourName(), item.quantity(), true,
                    item.retailPrice(), item.retailPrice(), item.dealerPrice(), item.dealerPrice(), true));
        }
        return rows;
    }

    private List<SpecRow> decorativeElementSectionRows(List<DecorativeElementPriceDto> decorativeElements) {
        List<SpecRow> rows = new ArrayList<>();
        for (DecorativeElementPriceDto item : decorativeElements) {
            // Только название типа, без категории (см. правку пользователя) — в отличие от фурнитуры.
            String name = item.type().name();
            String dimensions = formatDimensions(item.lengthMm(), item.widthMm(), item.thicknessMm());
            // У декоративных элементов надбавок никогда не бывает — базовая цена совпадает с итоговой (уже
            // с учётом количества, как и в ответе POST /api/decorative-elements/price); цвета нет.
            rows.add(new SpecRow(name, dimensions, null, item.quantity(), true,
                    item.retailPrice(), item.retailPrice(), item.dealerPrice(), item.dealerPrice(), true));
        }
        return rows;
    }

    // includeQuantity — показывать ли колонку «Количество» для этого компонента: применимо только к
    // doorCasing/frameExtensions (см. resolveQuantity в DoorConfigurationPricingService — для остальных
    // компонентов количество всегда 1 и не несёт информации).
    // externalHeightOverride — значение высоты для строки полотна (components.leafHeightValue(), см.
    // SpecificationComponents): у leaf-компонента heightOption в ResolvedComponent всегда null (высота не
    // сопоставляется с каталожной опцией напрямую), поэтому её нужно передать отдельно; для остальных
    // компонентов — null, высота (если применима) берётся из heightOption либо, для короба, из
    // component.heightMmOverride() (см. ResolvedComponent — высота короба «Фантом» мирроритcя от полотна,
    // а не выбирается из каталога).
    private SpecRow componentRow(
            ResolvedComponent component, ComponentSelectionDto selection, BigDecimal externalHeightOverride,
            boolean includeQuantity) {
        String name = component.type().getName();
        BigDecimal lengthMm =
                component.lengthOption() != null ? component.lengthOption().getValue() : selection.customLengthValueMm();
        BigDecimal heightMm = externalHeightOverride != null
                ? externalHeightOverride
                : (component.heightOption() != null ? component.heightOption().getValue() : component.heightMmOverride());
        BigDecimal thicknessMm = component.thicknessOption() != null ? component.thicknessOption().getValue() : null;
        String dimensions = formatDimensions(lengthMm, heightMm, thicknessMm);
        String colour = formatColour(component);
        Integer quantity = includeQuantity ? component.quantity() : null;
        ComponentPriceDto price = component.price();
        return new SpecRow(name, dimensions, colour, quantity,
                price.priced(), price.retailPrice(), price.baseRetailPrice(), price.dealerPrice(), price.baseDealerPrice(), true);
    }

    // Оба цвета полотна при двусторонней покраске — через « / » (фронтальный/задний, см. change
    // add-leaf-double-sided-painting); если выбран только один из двух (либо двусторонняя покраска не
    // применима — короб/кромка/наличник/добор, у которых backColourOption всегда null), поведение не
    // отличается от прежнего единственного цвета.
    private String formatColour(ResolvedComponent component) {
        String front = component.colourOption() != null ? component.colourOption().getColourType().getName() : null;
        String back = component.backColourOption() != null ? component.backColourOption().getColourType().getName() : null;
        if (front != null && back != null) {
            return front + " / " + back;
        }
        return front != null ? front : back;
    }

    // Объединяет применимые измерения в одну колонку через « × » в порядке длина-высота-толщина
    // (например, «900 × 2400 × 44»), пропуская неприменимые — вместо отдельной колонки на каждую ось.
    private String formatDimensions(BigDecimal length, BigDecimal height, BigDecimal thickness) {
        List<BigDecimal> values = new ArrayList<>();
        if (length != null) {
            values.add(length);
        }
        if (height != null) {
            values.add(height);
        }
        if (thickness != null) {
            values.add(thickness);
        }
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().map(value -> value.stripTrailingZeros().toPlainString()).collect(Collectors.joining(" × "));
    }

    private CellStyle sectionStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    // priceApplicable=false — единственный способ отличить «цена не применима к этой строке» (строка
    // выбранной опции или надбавки) от priced=false «цена применима, но не найдена» (прочерк).
    // baseRetailPrice/baseDealerPrice — цена без наценок (см. ComponentPriceDto.baseRetailPrice/
    // baseDealerPrice); для компонентов, к которым наценки никогда не применяются (всё, кроме leaf,
    // а также позиции короба и фурнитура), совпадает с retailPrice/dealerPrice.
    record SpecRow(
            String name,
            String dimensions,
            String colour,
            Integer quantity,
            boolean priced,
            BigDecimal retailPrice,
            BigDecimal baseRetailPrice,
            BigDecimal dealerPrice,
            BigDecimal baseDealerPrice,
            boolean priceApplicable) {
    }
}
