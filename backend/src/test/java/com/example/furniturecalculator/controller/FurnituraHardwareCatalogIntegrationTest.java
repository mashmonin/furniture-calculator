package com.example.furniturecalculator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.furniturecalculator.dto.HardwareCategoryDto;
import com.example.furniturecalculator.dto.HardwareOptionDto;
import com.example.furniturecalculator.dto.HardwarePricingRequestDto;
import com.example.furniturecalculator.dto.HardwarePricingResponseDto;
import com.example.furniturecalculator.dto.HardwareSelectionDto;
import com.example.furniturecalculator.dto.HardwareTypeDto;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Фурнитура прайс-листа «Фурнитура» prays_list_furnitura_tsfo_01_02_2026 (change add-hardware-service,
// PL-003). Ожидаемые значения заданы здесь независимо от SQL — по строкам файла (дилер / розница, ₽).
@SpringBootTest
@AutoConfigureMockMvc
class FurnituraHardwareCatalogIntegrationTest {

    private static final String PRICE_LIST_CODE = "PL-003";
    private static final String PRICE_LIST_NAME = "prays_list_furnitura_tsfo_01_02_2026";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    // Строка файла = цветовой вариант с артикулом: HAUSDOORS — 143 строки, Fantom — 105 (без «Образец FANTOM
    // PREMIUM» FNT0002 — у него нет розничной цены), Vantage — 805.
    @Test
    void все_строки_файла_заведены_по_брендам() throws Exception {
        Map<String, Long> optionsByBrand = furnitura(performGetCatalog()).stream()
                .collect(Collectors.groupingBy(HardwareTypeDto::brand, Collectors.summingLong(t -> t.options().size())));

        assertThat(optionsByBrand).containsOnlyKeys("HAUSDOORS", "Fantom", "Vantage");
        assertThat(optionsByBrand.get("HAUSDOORS")).isEqualTo(143);
        assertThat(optionsByBrand.get("Fantom")).isEqualTo(104);
        assertThat(optionsByBrand.get("Vantage")).isEqualTo(805);
    }

    @Test
    void типы_относятся_к_прайс_листу_pl_003_в_штуках() throws Exception {
        List<HardwareTypeDto> types = furnitura(performGetCatalog());

        assertThat(types).isNotEmpty().allSatisfy(type -> {
            assertThat(type.priceList().code()).isEqualTo(PRICE_LIST_CODE);
            assertThat(type.priceList().name()).isEqualTo(PRICE_LIST_NAME);
            assertThat(type.unit()).isEqualTo("шт");
        });
    }

    @Test
    void артикулы_цвета_и_цены_выбранных_строк_совпадают_с_файлом() throws Exception {
        List<HardwareCategoryDto> catalog = performGetCatalog();

        // HAUSDOORS, строка 13 файла: SC-00269698 «Турин MOV AL6 (черный)», 3711 / 6309.
        assertOption(catalog, "SC-00269698", "HAUSDOORS", "Ручки дверные", "Турин MOV", "Черный", 3711, 6309);
        // Fantom, строка 14: FM 100-50 MSN/CP, 1789 / 2948.
        assertOption(catalog, "FM 100-50 MSN/CP", "Fantom", "Дверные ручки", "Дверная ручка Fantom \"Адель\"",
                "Сатин никель/хром блестящий", 1789, 2948);
        // Vantage, строка 15: V01D, цвет в колонке наименования, 898 / 1347.
        assertOption(catalog, "V01D", "Vantage", "Ручки дверные ЦАМ", "Vantage V01D", "Матовый никель", 898, 1347);
    }

    @Test
    void цвета_одной_модели_объединены_в_один_тип() throws Exception {
        List<HardwareTypeDto> turin = furnitura(performGetCatalog()).stream()
                .filter(type -> type.type().name().equals("Турин MOV")).toList();

        assertThat(turin).hasSize(1);
        assertThat(turin.get(0).options()).extracting(HardwareOptionDto::article).containsExactlyInAnyOrder(
                "SC-00269698", "SC-00269699", "SC-00269700", "SC-00270907", "SC-00270908", "SC-00270909");
    }

    @Test
    void строка_без_розничной_цены_не_заведена_а_строка_без_цвета_получает_заглушку() throws Exception {
        List<HardwareOptionDto> options = furnitura(performGetCatalog()).stream().flatMap(t -> t.options().stream()).toList();

        assertThat(options).extracting(HardwareOptionDto::article).doesNotContain("FNT0002");
        // Vantage, «Раздвижные системы»: «Верхняя напр. L1 2м» — без цвета, 730 / 1095.
        assertThat(options).filteredOn(o -> "Верхняя напр. L1 2м".equals(o.article())).singleElement().satisfies(option -> {
            assertThat(option.colourName()).isEqualTo("Без цвета");
            assertThat(option.dealerPrice()).isEqualByComparingTo("730");
            assertThat(option.retailPrice()).isEqualByComparingTo("1095");
        });
    }

    // Бренд хранится в типе, а название раздела — без префикса бренда (миграция 0128).
    @Test
    void названия_разделов_без_префикса_бренда() throws Exception {
        List<HardwareCategoryDto> furnituraCategories = performGetCatalog().stream()
                .filter(c -> c.types().stream().anyMatch(t -> PRICE_LIST_CODE.equals(t.priceList().code()))).toList();

        assertThat(furnituraCategories).hasSize(34);
        assertThat(furnituraCategories).extracting(c -> c.category().name())
                .noneMatch(name -> name.startsWith("HAUSDOORS:") || name.startsWith("Fantom:") || name.startsWith("Vantage:"));
    }

    @Test
    void прежняя_фурнитура_pl_001_и_pl_002_не_затронута() throws Exception {
        List<HardwareTypeDto> others = performGetCatalog().stream().flatMap(c -> c.types().stream())
                .filter(type -> !PRICE_LIST_CODE.equals(type.priceList().code())).toList();

        // 20 типов «Эмаль и шпон» (PL-001) + 13 типов «Эмаль Лайт» (PL-002).
        assertThat(others).hasSize(33);
        assertThat(others).extracting(type -> type.priceList().code()).containsOnly("PL-001", "PL-002");
    }

    @Test
    void цена_фурнитуры_из_файла_считается_стандартным_расчётом() throws Exception {
        HardwareOptionDto option = findOption(performGetCatalog(), "FM 100-50 MSN/CP");

        String json = mockMvc.perform(post("/api/hardware/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new HardwarePricingRequestDto(List.of(new HardwareSelectionDto(option.id(), 2))))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        HardwarePricingResponseDto response = objectMapper.readValue(json, HardwarePricingResponseDto.class);

        assertThat(response.totalRetailPrice()).isEqualByComparingTo("5896");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("3578");
    }

    private void assertOption(List<HardwareCategoryDto> catalog, String article, String brand, String categoryName,
            String typeName, String colour, int dealer, int retail) {
        HardwareCategoryDto category = catalog.stream().filter(c -> c.category().name().equals(categoryName)).findFirst()
                .orElseThrow(() -> new AssertionError("Нет категории " + categoryName));
        HardwareTypeDto type = category.types().stream().filter(t -> t.type().name().equals(typeName)).findFirst()
                .orElseThrow(() -> new AssertionError("Нет типа " + typeName));
        assertThat(type.brand()).isEqualTo(brand);
        HardwareOptionDto option = type.options().stream().filter(o -> article.equals(o.article())).findFirst()
                .orElseThrow(() -> new AssertionError("Нет артикула " + article));
        assertThat(option.colourName()).isEqualTo(colour);
        assertThat(option.dealerPrice()).isEqualByComparingTo(String.valueOf(dealer));
        assertThat(option.retailPrice()).isEqualByComparingTo(String.valueOf(retail));
    }

    private static HardwareOptionDto findOption(List<HardwareCategoryDto> catalog, String article) {
        return catalog.stream().flatMap(c -> c.types().stream()).flatMap(t -> t.options().stream())
                .filter(o -> article.equals(o.article())).findFirst().orElseThrow();
    }

    private static List<HardwareTypeDto> furnitura(List<HardwareCategoryDto> catalog) {
        return catalog.stream().flatMap(c -> c.types().stream())
                .filter(type -> PRICE_LIST_CODE.equals(type.priceList().code())).toList();
    }

    private List<HardwareCategoryDto> performGetCatalog() throws Exception {
        String json = mockMvc.perform(get("/api/hardware-catalog"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, new TypeReference<List<HardwareCategoryDto>>() {
        });
    }
}
