package com.example.furniturecalculator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.furniturecalculator.domain.ColourOption;
import com.example.furniturecalculator.domain.ColourType;
import com.example.furniturecalculator.domain.DoorCasingType;
import com.example.furniturecalculator.domain.EdgeType;
import com.example.furniturecalculator.domain.FrameExtensionsType;
import com.example.furniturecalculator.domain.FramePost;
import com.example.furniturecalculator.domain.FrameType;
import com.example.furniturecalculator.domain.LeafType;
import com.example.furniturecalculator.domain.LinerDimensionOption;
import com.example.furniturecalculator.domain.LinerDimensionType;
import com.example.furniturecalculator.dto.ComponentPriceDto;
import com.example.furniturecalculator.dto.DecorativeElementPriceDto;
import com.example.furniturecalculator.dto.HardwarePriceDto;
import com.example.furniturecalculator.dto.ReferenceDto;
import com.example.furniturecalculator.dto.SpecificationExportRequestDto;
import com.example.furniturecalculator.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class SpecificationExportServiceTest {

    private static final List<String> SECTION_TITLES = List.of(
            "Полотно и опции полотна", "Короб и обрамление", "Наличники и доборы", "Декоративные элементы", "Фурнитура",
            "Надбавки к цене полотна");

    @Mock
    private DoorConfigurationPricingService pricingService;

    @InjectMocks
    private SpecificationExportService service;

    private final LeafType leafType = TestEntities.leafType(1L);

    @Test
    void минимальный_набор_содержит_только_раздел_полотна() throws IOException {
        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, null, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        List<String> sectionTitles = sectionTitles(file);
        assertThat(sectionTitles).containsExactly("Полотно и опции полотна");
    }

    @Test
    void ненайденная_цена_отображается_прочерком_а_не_пропуском_строки() throws IOException {
        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", false, null, null, null, null),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, null, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row leafRow = sheet.getRow(2);
            assertThat(leafRow.getCell(0).getStringCellValue()).isEqualTo("LeafType 1");
            assertThat(leafRow.getCell(4).getStringCellValue()).isEqualTo("—");
            assertThat(leafRow.getCell(5).getStringCellValue()).isEqualTo("—");
            assertThat(leafRow.getCell(6).getStringCellValue()).isEqualTo("—");
            assertThat(leafRow.getCell(7).getStringCellValue()).isEqualTo("—");
        }
    }

    @Test
    void заголовки_колонок_переименованы_и_переупорядочены() throws IOException {
        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, null, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Row headerRow = workbook.getSheetAt(0).getRow(1);
            List<String> headers = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                headers.add(headerRow.getCell(i).getStringCellValue());
            }
            assertThat(headers).containsExactly(
                    "Наименование", "Измерения, мм", "Цвет", "Количество",
                    "Итоговая цена, ₽", "Базовая цена розница, ₽", "Цена дилер, ₽", "Базовая цена дилер, ₽");
        }
    }

    @Test
    void полный_набор_разделов_присутствует_в_ожидаемом_порядке() throws IOException {
        FrameType frameType = TestEntities.frameType(2L);
        DoorCasingType doorCasingType = TestEntities.doorCasingType(5L);
        FrameExtensionsType frameExtensionsType = TestEntities.frameExtensionsType(6L);
        FramePost post = TestEntities.framePost(100L, BigDecimal.valueOf(300), BigDecimal.valueOf(200), frameType);

        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        ResolvedComponent frame = new ResolvedComponent(frameType, new ComponentPriceDto("frame", true, BigDecimal.valueOf(3000), BigDecimal.valueOf(2000),
                        BigDecimal.valueOf(3000), BigDecimal.valueOf(2000)),
                null, null, null, null, null, List.of(post), 1, List.of(), List.of(), null);
        ResolvedComponent doorCasing = new ResolvedComponent(doorCasingType, new ComponentPriceDto("doorCasing", true, BigDecimal.valueOf(300), BigDecimal.valueOf(200),
                        BigDecimal.valueOf(300), BigDecimal.valueOf(200)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        ResolvedComponent frameExtensions = new ResolvedComponent(frameExtensionsType, new ComponentPriceDto("frameExtensions", true, BigDecimal.valueOf(150), BigDecimal.valueOf(100),
                        BigDecimal.valueOf(150), BigDecimal.valueOf(100)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        HardwarePriceDto hardware = new HardwarePriceDto(
                new ReferenceDto(300L, "HardwareCategory-300", "HardwareCategory 300", null),
                new ReferenceDto(301L, "HardwareType-301", "HardwareType 301", null),
                "хром", 2, BigDecimal.valueOf(2000), BigDecimal.valueOf(1400));
        DecorativeElementPriceDto decorativeElement = new DecorativeElementPriceDto(
                new ReferenceDto(400L, "DEC-001", "Плинтус", null),
                new ReferenceDto(401L, "DET-001", "Плинтус Модо", null),
                BigDecimal.valueOf(2400), null, null, 2, BigDecimal.valueOf(5222), BigDecimal.valueOf(2984));

        SpecificationComponents components = new SpecificationComponents(
                leaf, null, null, frame, doorCasing, frameExtensions, List.of(hardware), List.of(decorativeElement));
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        List<String> sectionTitles = sectionTitles(file);
        assertThat(sectionTitles).containsExactly(
                "Полотно и опции полотна", "Короб и обрамление", "Наличники и доборы", "Декоративные элементы", "Фурнитура");
    }

    @Test
    void раздел_декоративных_элементов_содержит_длину_и_цену_с_учётом_количества() throws IOException {
        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        DecorativeElementPriceDto decorativeElement = new DecorativeElementPriceDto(
                new ReferenceDto(400L, "DEC-001", "Плинтус", null),
                new ReferenceDto(401L, "DET-001", "Плинтус Модо", null),
                BigDecimal.valueOf(2400), null, null, 2, BigDecimal.valueOf(5222), BigDecimal.valueOf(2984));

        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, null, null, null, List.of(), List.of(decorativeElement));
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row decorativeElementRow = null;
            for (Row row : sheet) {
                Cell firstCell = row.getCell(0);
                if (firstCell != null && firstCell.getCellType() == CellType.STRING
                        && firstCell.getStringCellValue().contains("Плинтус Модо")) {
                    decorativeElementRow = row;
                    break;
                }
            }
            assertThat(decorativeElementRow).isNotNull();
            assertThat(decorativeElementRow.getCell(0).getStringCellValue()).isEqualTo("Плинтус Модо");
            assertThat(decorativeElementRow.getCell(1).getStringCellValue()).isEqualTo("2400");
            assertThat(decorativeElementRow.getCell(3).getNumericCellValue()).isEqualTo(2);
            assertThat(decorativeElementRow.getCell(4).getNumericCellValue()).isEqualTo(5222);
            assertThat(decorativeElementRow.getCell(6).getNumericCellValue()).isEqualTo(2984);
        }
    }

    @Test
    void раздел_декоративных_элементов_с_шириной_и_толщиной_объединяет_измерения_через_крестик() throws IOException {
        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        DecorativeElementPriceDto decorativeElement = new DecorativeElementPriceDto(
                new ReferenceDto(410L, "DEC-002", "Блок и база", null),
                new ReferenceDto(411L, "DET-003", "Блок А", null),
                BigDecimal.valueOf(90), BigDecimal.valueOf(90), BigDecimal.valueOf(30),
                1, BigDecimal.valueOf(2021), BigDecimal.valueOf(1154));

        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, null, null, null, List.of(), List.of(decorativeElement));
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row decorativeElementRow = null;
            for (Row row : sheet) {
                Cell firstCell = row.getCell(0);
                if (firstCell != null && firstCell.getCellType() == CellType.STRING
                        && firstCell.getStringCellValue().contains("Блок А")) {
                    decorativeElementRow = row;
                    break;
                }
            }
            assertThat(decorativeElementRow).isNotNull();
            assertThat(decorativeElementRow.getCell(1).getStringCellValue()).isEqualTo("90 × 90 × 30");
        }
    }

    @Test
    void надбавки_к_цене_полотна_выгружаются_отдельным_разделом_под_таблицей() throws IOException {
        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1400), BigDecimal.valueOf(1260),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(new LeafPriceSurcharge("За исполнение зеркала", BigDecimal.valueOf(40))), List.of(), null);
        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, null, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String> sectionTitles = sectionTitles(file);
            assertThat(sectionTitles).containsExactly("Полотно и опции полотна", "Надбавки к цене полотна");

            // Между разделом полотна и разделом надбавок — блок «Итоговая сумма» (строки 4-6, см. отдельный
            // тест на него), поэтому раздел надбавок начинается со строки 8, а не сразу после полотна.
            Row sectionTitleRow = sheet.getRow(8);
            assertThat(sectionTitleRow.getCell(0).getStringCellValue()).isEqualTo("Надбавки к цене полотна");

            // Раздел надбавок — без строки заголовков колонок (см. writeSection, includeColumnHeaders=false):
            // строка надбавки идёт сразу за заголовком раздела, а не через одну строку.
            Row headerlessRow = sheet.getRow(9);
            assertThat(headerlessRow.getCell(0).getStringCellValue()).isEqualTo("  За исполнение зеркала: +40%");
            assertThat(headerlessRow.getCell(4)).isNull();
            assertThat(headerlessRow.getCell(5)).isNull();
            assertThat(headerlessRow.getCell(6)).isNull();
            assertThat(headerlessRow.getCell(7)).isNull();
        }
    }

    @Test
    void надбавка_за_двустороннюю_покраску_показана_в_разделе_надбавок() throws IOException {
        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(2100), BigDecimal.valueOf(1890),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1,
                List.of(new LeafPriceSurcharge("За выбранный цвет", BigDecimal.valueOf(40)),
                        new LeafPriceSurcharge("За двустороннюю покраску", BigDecimal.valueOf(50))),
                List.of(), null);
        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, null, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row colourSurchargeRow = sheet.getRow(9);
            assertThat(colourSurchargeRow.getCell(0).getStringCellValue()).isEqualTo("  За выбранный цвет: +40%");
            Row doubleSidedRow = sheet.getRow(10);
            assertThat(doubleSidedRow.getCell(0).getStringCellValue()).isEqualTo("  За двустороннюю покраску: +50%");
        }
    }

    @Test
    void двусторонняя_покраска_показывает_оба_цвета_через_разделитель() throws IOException {
        ColourType colourTypeFront = TestEntities.colourType(1L, BigDecimal.valueOf(0));
        ColourOption front = TestEntities.colourOption(2000L, colourTypeFront, leafType);
        ColourType colourTypeBack = TestEntities.colourType(2L, BigDecimal.valueOf(20));
        ColourOption back = TestEntities.colourOption(2001L, colourTypeBack, leafType);

        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, front, back, List.of(), 1, List.of(), List.of(), null);
        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, null, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row leafRow = sheet.getRow(2);
            assertThat(leafRow.getCell(2).getStringCellValue())
                    .isEqualTo(colourTypeFront.getName() + " / " + colourTypeBack.getName());
        }
    }

    @Test
    void единственный_выбранный_цвет_при_двусторонней_покраске_показан_один() throws IOException {
        ColourType frontColourType = TestEntities.colourType(1L, BigDecimal.valueOf(0));
        ColourOption front = TestEntities.colourOption(2000L, frontColourType, leafType);

        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, front, null, List.of(), 1, List.of(), List.of(), null);
        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, null, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row leafRow = sheet.getRow(2);
            assertThat(leafRow.getCell(2).getStringCellValue()).isEqualTo(frontColourType.getName());
        }
    }

    @Test
    void измерения_объединяются_через_звёздочку_и_видна_базовая_и_итоговая_цена() throws IOException {
        LinerDimensionType lengthType = TestEntities.linerDimensionType(4L, "DT-001");
        LinerDimensionType thicknessType = TestEntities.linerDimensionType(6L, "DT-003");
        LinerDimensionOption lengthOption =
                TestEntities.linerDimensionOption(10L, lengthType, BigDecimal.valueOf(900), true, leafType);
        LinerDimensionOption thicknessOption =
                TestEntities.linerDimensionOption(11L, thicknessType, BigDecimal.valueOf(44), true, leafType);

        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1400), BigDecimal.valueOf(1260),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                lengthOption, null, thicknessOption, null, null, null, 1, List.of(), List.of(), null);
        SpecificationComponents components =
                new SpecificationComponents(leaf, BigDecimal.valueOf(2400), null, null, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        SpecificationExportRequestDto request = new SpecificationExportRequestDto(
                1L, null, null, null, true, null, null, null, null, null, null, null, null);

        byte[] file = service.export(request);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row leafRow = sheet.getRow(2);
            assertThat(leafRow.getCell(1).getStringCellValue()).isEqualTo("900 × 2400 × 44");
            assertThat(leafRow.getCell(4).getNumericCellValue()).isEqualTo(1400.0);
            assertThat(leafRow.getCell(5).getNumericCellValue()).isEqualTo(1000.0);
            assertThat(leafRow.getCell(6).getNumericCellValue()).isEqualTo(1260.0);
            assertThat(leafRow.getCell(7).getNumericCellValue()).isEqualTo(900.0);

            Row openingTypeRow = sheet.getRow(3);
            assertThat(openingTypeRow.getCell(0).getStringCellValue()).isEqualTo("  Тип открывания: Реверс");
        }
    }

    @Test
    void без_реверса_строка_типа_открывания_не_добавляется() throws IOException {
        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, null, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(3)).isNull();
        }
    }

    @Test
    void выбранные_опции_полотна_выгружаются_строками_между_полотном_и_кромкой() throws IOException {
        EdgeType edgeType = TestEntities.edgeType(3L);
        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1400), BigDecimal.valueOf(1260),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(), List.of("Исполнение зеркала: С фацетом", "Вид остекления: Сатинированное"), null);
        ResolvedComponent edge = new ResolvedComponent(edgeType, new ComponentPriceDto("edge", true, BigDecimal.valueOf(500), BigDecimal.valueOf(400),
                        BigDecimal.valueOf(500), BigDecimal.valueOf(400)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        SpecificationComponents components =
                new SpecificationComponents(leaf, null, edge, null, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("  Исполнение зеркала: С фацетом");
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("  Вид остекления: Сатинированное");
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("EdgeType 3");
        }
    }

    @Test
    void измерение_короба_с_каталожной_высотой_выгружается() throws IOException {
        FrameType frameType = TestEntities.frameType(2L);
        LinerDimensionType heightType = TestEntities.linerDimensionType(5L, "DT-002");
        LinerDimensionOption heightOption =
                TestEntities.linerDimensionOption(20L, heightType, BigDecimal.valueOf(2100), true, frameType);

        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        ResolvedComponent frame = new ResolvedComponent(frameType, new ComponentPriceDto("frame", true, BigDecimal.valueOf(3000), BigDecimal.valueOf(2000),
                        BigDecimal.valueOf(3000), BigDecimal.valueOf(2000)),
                null, heightOption, null, null, null, List.of(), 1, List.of(), List.of(), null);
        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, frame, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            // Раздел «Полотно и опции полотна»: строки 0(заголовок)-1(шапка)-2(полотно); пустая строка 3;
            // раздел «Короб и обрамление» с строки 4.
            Row frameRow = sheet.getRow(6);
            assertThat(frameRow.getCell(0).getStringCellValue()).isEqualTo("FrameType 2");
            assertThat(frameRow.getCell(1).getStringCellValue()).isEqualTo("2100");
        }
    }

    @Test
    void измерение_короба_с_мирроритcя_от_высоты_полотна() throws IOException {
        FrameType frameType = TestEntities.frameType(2L);

        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        ResolvedComponent frame = new ResolvedComponent(frameType, new ComponentPriceDto("frame", true, BigDecimal.valueOf(3000), BigDecimal.valueOf(2000),
                        BigDecimal.valueOf(3000), BigDecimal.valueOf(2000)),
                null, null, null, null, null, List.of(), 1, List.of(), List.of(), BigDecimal.valueOf(2000));
        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, frame, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row frameRow = sheet.getRow(6);
            assertThat(frameRow.getCell(1).getStringCellValue()).isEqualTo("2000");
        }
    }

    @Test
    void итоговая_сумма_выводится_после_полотна_с_двумя_колонками() throws IOException {
        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, null, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("Итоговая сумма");
            // Колонки E(4)/G(6) — те же, что и «Итоговая цена, ₽»/«Цена дилер, ₽» в общей раскладке.
            assertThat(sheet.getRow(5).getCell(4).getStringCellValue()).isEqualTo("Итоговая цена");
            assertThat(sheet.getRow(5).getCell(6).getStringCellValue()).isEqualTo("Итоговая цена дилер");
            assertThat(sheet.getRow(6).getCell(4).getNumericCellValue()).isEqualTo(1000.0);
            assertThat(sheet.getRow(6).getCell(6).getNumericCellValue()).isEqualTo(900.0);
        }
    }

    @Test
    void итоговая_сумма_учитывает_все_компоненты_и_пропускает_ненайденную_цену() throws IOException {
        FrameType frameType = TestEntities.frameType(2L);
        FramePost post = TestEntities.framePost(100L, BigDecimal.valueOf(300), BigDecimal.valueOf(200), frameType);
        EdgeType edgeType = TestEntities.edgeType(3L);

        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        // Цена не найдена — не должна попасть в итоговую сумму (тот же принцип, что и в
        // DoorConfigurationPricingService.sumRetail/sumDealer).
        ResolvedComponent edge = new ResolvedComponent(edgeType, new ComponentPriceDto("edge", false, null, null, null, null),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        ResolvedComponent frame = new ResolvedComponent(frameType, new ComponentPriceDto("frame", true, BigDecimal.valueOf(3000), BigDecimal.valueOf(2000),
                        BigDecimal.valueOf(3000), BigDecimal.valueOf(2000)),
                null, null, null, null, null, List.of(post), 1, List.of(), List.of(), null);

        SpecificationComponents components =
                new SpecificationComponents(leaf, null, edge, frame, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        byte[] file = service.export(emptyRequest());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            // Полотно (0=заголовок,1=шапка,2=полотно,3=кромка) → короб и обрамление (5=заголовок,6=шапка,
            // 7=короб,8=стойка) → итоговая сумма (10=заголовок,11=шапка,12=данные).
            assertThat(sheet.getRow(10).getCell(0).getStringCellValue()).isEqualTo("Итоговая сумма");
            // Колонки E(4)/G(6) — те же, что и «Итоговая цена, ₽»/«Цена дилер, ₽» в общей раскладке.
            // 1000(leaf) + 0(edge, не найдена) + 3000(frame) + 300(frame_post) = 4300.
            assertThat(sheet.getRow(12).getCell(4).getNumericCellValue()).isEqualTo(4300.0);
            // 900(leaf) + 0(edge) + 2000(frame) + 200(frame_post) = 3100.
            assertThat(sheet.getRow(12).getCell(6).getNumericCellValue()).isEqualTo(3100.0);
        }
    }

    // Регрессия: размер наличника/добора — это ДЛИНА (см. resolvedLength в SpecificationExportService), а
    // не высота — у этих компонентов heightOption всегда null, поэтому попытка взять heightOption/
    // heightMmOverride (как для короба) молча давала пустое «Размеры» в детализации заказа.
    @Test
    void детализация_показывает_длину_наличника_и_добора_как_размер() {
        DoorCasingType doorCasingType = TestEntities.doorCasingType(5L);
        FrameExtensionsType frameExtensionsType = TestEntities.frameExtensionsType(6L);
        LinerDimensionType lengthType = TestEntities.linerDimensionType(4L, "DT-001");
        LinerDimensionOption casingLength =
                TestEntities.linerDimensionOption(20L, lengthType, BigDecimal.valueOf(2100), true, doorCasingType);
        LinerDimensionOption extensionsLength =
                TestEntities.linerDimensionOption(21L, lengthType, BigDecimal.valueOf(2050), true, frameExtensionsType);

        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        ResolvedComponent doorCasing = new ResolvedComponent(doorCasingType, new ComponentPriceDto("doorCasing", true, BigDecimal.valueOf(300), BigDecimal.valueOf(200),
                        BigDecimal.valueOf(300), BigDecimal.valueOf(200)),
                casingLength, null, null, null, null, null, 1, List.of(), List.of(), null);
        ResolvedComponent frameExtensions = new ResolvedComponent(frameExtensionsType, new ComponentPriceDto("frameExtensions", true, BigDecimal.valueOf(150), BigDecimal.valueOf(100),
                        BigDecimal.valueOf(150), BigDecimal.valueOf(100)),
                extensionsLength, null, null, null, null, null, 1, List.of(), List.of(), null);

        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, null, doorCasing, frameExtensions, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        SpecificationExportService.ConfigurationBreakdown breakdown = service.resolveConfigurationBreakdown(emptyRequest());

        SpecificationExportService.DetailRow casingRow = breakdown.detailRows().stream()
                .filter(row -> "Наличник".equals(row.element())).findFirst().orElseThrow();
        assertThat(casingRow.size()).isEqualTo("2100");

        SpecificationExportService.DetailRow extensionsRow = breakdown.detailRows().stream()
                .filter(row -> "Добор".equals(row.element())).findFirst().orElseThrow();
        assertThat(extensionsRow.size()).isEqualTo("2050");
    }

    // Плоская детализация (resolveConfigurationBreakdown, используется листом заказа и, до правки
    // пользователя, повторяла раскрываемую детализацию корзины на фронте) больше НЕ содержит отдельных строк
    // на состав короба (см. правку пользователя, order-cart-ui, «Разворачиваемая детализация позиции
    // корзины») — короб виден только одной строкой «Короб» с суммарной ценой; сам frame_post из теста при
    // этом не отбрасывается совсем: старый лист одиночной спецификации (frameSectionRows, отдельная,
    // не затронутая этой правкой возможность door-configuration-export) по-прежнему показывает его отдельной
    // строкой, и «Комплект зарезных стоек» там по-прежнему считается как 1 штука (см. предыдущую правку).
    @Test
    void плоская_детализация_не_содержит_отдельных_строк_состава_короба() throws IOException {
        FrameType frameType = TestEntities.frameType(2L);
        FramePost kitPost = TestEntities.framePost(100L, BigDecimal.valueOf(300), BigDecimal.valueOf(200), frameType);
        org.springframework.test.util.ReflectionTestUtils.setField(kitPost.getPostType(), "name", "Комплект зарезных стоек");
        org.springframework.test.util.ReflectionTestUtils.setField(kitPost, "quantity", 3);

        ResolvedComponent leaf = new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, BigDecimal.valueOf(1000), BigDecimal.valueOf(900),
                        BigDecimal.valueOf(1000), BigDecimal.valueOf(900)),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
        ResolvedComponent frame = new ResolvedComponent(frameType, new ComponentPriceDto("frame", true, BigDecimal.valueOf(3000), BigDecimal.valueOf(2000),
                        BigDecimal.valueOf(3000), BigDecimal.valueOf(2000)),
                null, null, null, null, null, List.of(kitPost), 1, List.of(), List.of(), null);
        SpecificationComponents components =
                new SpecificationComponents(leaf, null, null, frame, null, null, List.of());
        when(pricingService.resolveSpecificationComponents(any())).thenReturn(components);

        SpecificationExportService.ConfigurationBreakdown breakdown = service.resolveConfigurationBreakdown(emptyRequest());
        assertThat(breakdown.detailRows()).extracting(SpecificationExportService.DetailRow::element)
                .containsExactly("Полотно", "Короб");
        SpecificationExportService.DetailRow frameRow = breakdown.detailRows().stream()
                .filter(row -> "Короб".equals(row.element())).findFirst().orElseThrow();
        // Цена строки «Короб» — цена всего frame-компонента (3000/2000), уже включающая состав его стоек
        // (см. door-configuration-api, «Стоимость короба как сумма цен его стоек») — не 300/200 отдельного поста.
        assertThat(frameRow.retailSum()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        assertThat(frameRow.dealerSum()).isEqualByComparingTo(BigDecimal.valueOf(2000));

        byte[] file = service.export(emptyRequest());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            // Полотно (0=заголовок,1=шапка,2=полотно) → короб и обрамление (4=заголовок,5=шапка,6=короб,
            // 7=комплект стоек) — старый лист одиночной спецификации по-прежнему показывает состав короба.
            Row kitSheetRow = sheet.getRow(7);
            assertThat(kitSheetRow.getCell(0).getStringCellValue()).isEqualTo("Комплект зарезных стоек");
            assertThat(kitSheetRow.getCell(3).getNumericCellValue()).isEqualTo(1);
            assertThat(kitSheetRow.getCell(4).getNumericCellValue()).isEqualTo(300.0);
        }
    }

    private SpecificationExportRequestDto emptyRequest() {
        return new SpecificationExportRequestDto(
                1L, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    // Раздел начинается со строки, чья первая ячейка — один из заголовков разделов (в отличие от строк
    // данных, где первая ячейка — наименование компонента/подпись, и строк заголовков колонок, где первая
    // ячейка — «Наименование»).
    private List<String> sectionTitles(byte[] file) throws IOException {
        List<String> titles = new java.util.ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                Cell cell = row.getCell(0);
                if (cell == null || cell.getCellType() != CellType.STRING) {
                    continue;
                }
                String value = cell.getStringCellValue();
                if (SECTION_TITLES.contains(value)) {
                    titles.add(value);
                }
            }
        }
        return titles;
    }
}
