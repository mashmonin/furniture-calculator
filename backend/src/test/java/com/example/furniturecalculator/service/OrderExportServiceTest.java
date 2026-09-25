package com.example.furniturecalculator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.example.furniturecalculator.domain.LeafType;
import com.example.furniturecalculator.dto.ComponentPriceDto;
import com.example.furniturecalculator.dto.OrderLineExportRequestDto;
import com.example.furniturecalculator.dto.SpecificationExportRequestDto;
import com.example.furniturecalculator.support.TestEntities;

// SpecificationExportService здесь настоящий (не мок), с замоканным только DoorConfigurationPricingService
// под ним — так построчная детализация каждой позиции строится реальной, уже протестированной в
// SpecificationExportServiceTest логикой (resolveConfigurationBreakdown), а эти тесты проверяют только то,
// что добавляет OrderExportService: единый лист «Заказ» со строкой конфигурации и детализацией под ней на
// каждую позицию (см. change update-order-export-flat-layout) и валидацию списка позиций.
@ExtendWith(MockitoExtension.class)
class OrderExportServiceTest {

    @Mock
    private DoorConfigurationPricingService pricingService;

    private OrderExportService service;

    private final LeafType leafType = TestEntities.leafType(1L);

    @BeforeEach
    void setUp() {
        service = new OrderExportService(new SpecificationExportService(pricingService));
    }

    private ResolvedComponent leafComponent(BigDecimal retailPrice) {
        return new ResolvedComponent(leafType, new ComponentPriceDto("leaf", true, retailPrice, retailPrice, retailPrice, retailPrice),
                null, null, null, null, null, null, 1, List.of(), List.of(), null);
    }

    private SpecificationExportRequestDto request() {
        return new SpecificationExportRequestDto(1L, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private OrderLineExportRequestDto line(String displayName, int quantity) {
        return new OrderLineExportRequestDto(displayName, quantity, request(), List.of());
    }

    private OrderLineExportRequestDto lineWithTags(String displayName, int quantity, List<String> attributeTags) {
        return new OrderLineExportRequestDto(displayName, quantity, request(), attributeTags);
    }

    // Жирный шрифт ячейки — через индекс шрифта в её стиле (POI не даёт прямого Cell.isBold()).
    private boolean isBold(XSSFWorkbook workbook, org.apache.poi.ss.usermodel.Cell cell) {
        return workbook.getFontAt(cell.getCellStyle().getFontIndex()).getBold();
    }

    @Test
    void лист_заказа_содержит_строку_конфигурации_и_детализацию_для_каждой_позиции() throws IOException {
        when(pricingService.resolveSpecificationComponents(any()))
                .thenReturn(new SpecificationComponents(leafComponent(BigDecimal.valueOf(1000)), null, null, null, null, null, List.of()));

        byte[] file = service.export(List.of(line("Вертикаль 01", 2), line("Вертикаль 02", 1)));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            Sheet sheet = workbook.getSheet("Заказ");
            assertThat(sheet).isNotNull();

            Row headerRow = sheet.getRow(0);
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("№");
            assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("Конфигурация");

            Row configRow1 = sheet.getRow(1);
            assertThat(configRow1.getCell(0).getNumericCellValue()).isEqualTo(1);
            assertThat(configRow1.getCell(1).getStringCellValue()).isEqualTo("Вертикаль 01");
            assertThat(configRow1.getCell(3).getNumericCellValue()).isEqualTo(1000.0);
            assertThat(configRow1.getCell(4).getNumericCellValue()).isEqualTo(2);
            assertThat(configRow1.getCell(5).getNumericCellValue()).isEqualTo(2000.0);
            // Наименование не выделяется жирным (см. правку пользователя), в отличие от «№».
            assertThat(isBold(workbook, configRow1.getCell(0))).isTrue();
            assertThat(isBold(workbook, configRow1.getCell(1))).isFalse();

            Row detailHeaderRow1 = sheet.getRow(2);
            assertThat(detailHeaderRow1.getCell(0).getStringCellValue()).isEqualTo("Элемент");

            // Строка 3 (индекс) — детализация полотна первой позиции, строка 4 — «Итого» по детализации
            // (см. writeDetailTotalsRow), строка 5 — строка конфигурации второй позиции.
            Row totalsRow1 = sheet.getRow(4);
            assertThat(totalsRow1.getCell(1).getStringCellValue()).isEqualTo("Итого");
            assertThat(totalsRow1.getCell(7).getNumericCellValue()).isEqualTo(1000.0);
            assertThat(totalsRow1.getCell(8).getNumericCellValue()).isEqualTo(1000.0);

            Row configRow2 = sheet.getRow(5);
            assertThat(configRow2.getCell(0).getNumericCellValue()).isEqualTo(2);
            assertThat(configRow2.getCell(1).getStringCellValue()).isEqualTo("Вертикаль 02");
            assertThat(configRow2.getCell(4).getNumericCellValue()).isEqualTo(1);
            assertThat(configRow2.getCell(5).getNumericCellValue()).isEqualTo(1000.0);

            // Строка 9 (индекс) — последняя строка листа: итог по заказу (2000 + 1000, см. writeOrderTotalRow),
            // ровно три ячейки подряд с колонки A — label, дилерская сумма, клиентская сумма. В тестовой
            // фикстуре leafComponent дилерская цена равна розничной, поэтому дилерский итог тоже 3000.
            Row orderTotalRow = sheet.getRow(9);
            assertThat(orderTotalRow.getCell(0).getStringCellValue()).isEqualTo("Итого по заказу");
            assertThat(orderTotalRow.getCell(1).getNumericCellValue()).isEqualTo(3000.0);
            assertThat(orderTotalRow.getCell(2).getNumericCellValue()).isEqualTo(3000.0);
            assertThat(orderTotalRow.getCell(3)).isNull();
            assertThat(isBold(workbook, orderTotalRow.getCell(0))).isTrue();
            assertThat(isBold(workbook, orderTotalRow.getCell(1))).isFalse();
            assertThat(isBold(workbook, orderTotalRow.getCell(2))).isFalse();
        }
    }

    @Test
    void ячейка_конфигурации_содержит_теги_атрибутов_после_наименования() throws IOException {
        when(pricingService.resolveSpecificationComponents(any()))
                .thenReturn(new SpecificationComponents(leafComponent(BigDecimal.valueOf(1000)), null, null, null, null, null, List.of()));

        byte[] file = service.export(List.of(lineWithTags("Вертикаль 01", 1, List.of("РЕВЕРС", "ЧЕТВЕРТЬ"))));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheet("Заказ");
            Row configRow = sheet.getRow(1);
            assertThat(configRow.getCell(1).getStringCellValue()).isEqualTo("Вертикаль 01 — РЕВЕРС, ЧЕТВЕРТЬ");
        }
    }

    @Test
    void ячейка_конфигурации_без_тегов_показывает_только_наименование() throws IOException {
        when(pricingService.resolveSpecificationComponents(any()))
                .thenReturn(new SpecificationComponents(leafComponent(BigDecimal.valueOf(1000)), null, null, null, null, null, List.of()));

        byte[] file = service.export(List.of(line("Вертикаль 01", 1)));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheet("Заказ");
            Row configRow = sheet.getRow(1);
            assertThat(configRow.getCell(1).getStringCellValue()).isEqualTo("Вертикаль 01");
        }
    }

    @Test
    void строка_детализации_полотна_содержит_то_же_наименование_и_цену_что_и_компонент() throws IOException {
        when(pricingService.resolveSpecificationComponents(any()))
                .thenReturn(new SpecificationComponents(leafComponent(BigDecimal.valueOf(1000)), null, null, null, null, null, List.of()));

        byte[] file = service.export(List.of(line("Вертикаль 01", 1)));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet sheet = workbook.getSheet("Заказ");
            Row leafDetailRow = sheet.getRow(3);
            assertThat(leafDetailRow.getCell(0).getStringCellValue()).isEqualTo("Полотно");
            assertThat(leafDetailRow.getCell(1).getStringCellValue()).isEqualTo("LeafType 1");
            assertThat(leafDetailRow.getCell(4).getNumericCellValue()).isEqualTo(1);
            assertThat(leafDetailRow.getCell(5).getNumericCellValue()).isEqualTo(1000.0);
            assertThat(leafDetailRow.getCell(6).getNumericCellValue()).isEqualTo(1000.0);
            assertThat(leafDetailRow.getCell(7).getNumericCellValue()).isEqualTo(1000.0);
            assertThat(leafDetailRow.getCell(8).getNumericCellValue()).isEqualTo(1000.0);
        }
    }

    @Test
    void пустой_список_позиций_отклоняется() {
        assertThatThrownBy(() -> service.export(List.of()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void количество_меньше_1_отклоняется() {
        assertThatThrownBy(() -> service.export(List.of(line("Вертикаль 01", 0))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void невалидная_позиция_отклоняет_весь_запрос_тем_же_кодом() {
        when(pricingService.resolveSpecificationComponents(any()))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "leaf_type не найден"));

        assertThatThrownBy(() -> service.export(List.of(line("Вертикаль 01", 1))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }
}
