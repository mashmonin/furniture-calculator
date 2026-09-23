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
// под ним — так лист детализации каждой позиции строится реальной, уже протестированной в
// SpecificationExportServiceTest логикой (writeConfigurationSheet), а эти тесты проверяют только то, что
// добавляет OrderExportService: лист-сводку, имена листов детализации и валидацию списка позиций.
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
        return new OrderLineExportRequestDto(displayName, quantity, request());
    }

    @Test
    void сводка_содержит_строку_на_каждую_позицию_и_итог() throws IOException {
        when(pricingService.resolveSpecificationComponents(any()))
                .thenReturn(new SpecificationComponents(leafComponent(BigDecimal.valueOf(1000)), null, null, null, null, null, List.of()));

        byte[] file = service.export(List.of(line("Вертикаль 01", 2), line("Вертикаль 02", 1)));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet summary = workbook.getSheet("Сводка");
            assertThat(summary).isNotNull();

            Row row1 = summary.getRow(1);
            assertThat(row1.getCell(0).getNumericCellValue()).isEqualTo(1);
            assertThat(row1.getCell(1).getStringCellValue()).isEqualTo("Вертикаль 01");
            assertThat(row1.getCell(2).getNumericCellValue()).isEqualTo(2);
            assertThat(row1.getCell(3).getNumericCellValue()).isEqualTo(1000.0);
            assertThat(row1.getCell(4).getNumericCellValue()).isEqualTo(2000.0);

            Row row2 = summary.getRow(2);
            assertThat(row2.getCell(1).getStringCellValue()).isEqualTo("Вертикаль 02");
            assertThat(row2.getCell(2).getNumericCellValue()).isEqualTo(1);
            assertThat(row2.getCell(4).getNumericCellValue()).isEqualTo(1000.0);

            Row totalRow = summary.getRow(3);
            assertThat(totalRow.getCell(1).getStringCellValue()).isEqualTo("Итого по заказу");
            assertThat(totalRow.getCell(4).getNumericCellValue()).isEqualTo(3000.0);
        }
    }

    @Test
    void лист_детализации_соответствует_одиночной_выгрузке_той_же_конфигурации() throws IOException {
        when(pricingService.resolveSpecificationComponents(any()))
                .thenReturn(new SpecificationComponents(leafComponent(BigDecimal.valueOf(1000)), null, null, null, null, null, List.of()));

        byte[] file = service.export(List.of(line("Вертикаль 01", 1)));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            Sheet detailSheet = workbook.getSheet("1. Вертикаль 01");
            assertThat(detailSheet).isNotNull();
            assertThat(detailSheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Полотно и опции полотна");
            Row leafRow = detailSheet.getRow(2);
            assertThat(leafRow.getCell(0).getStringCellValue()).isEqualTo("LeafType 1");
            assertThat(leafRow.getCell(4).getNumericCellValue()).isEqualTo(1000.0);
        }
    }

    @Test
    void название_листа_включает_номер_и_наименование_позиции() throws IOException {
        when(pricingService.resolveSpecificationComponents(any()))
                .thenReturn(new SpecificationComponents(leafComponent(BigDecimal.valueOf(1000)), null, null, null, null, null, List.of()));

        byte[] file = service.export(List.of(line("Вертикаль 01", 1), line("Вертикаль 02", 1)));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(workbook.getSheet("1. Вертикаль 01")).isNotNull();
            assertThat(workbook.getSheet("2. Вертикаль 02")).isNotNull();
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
