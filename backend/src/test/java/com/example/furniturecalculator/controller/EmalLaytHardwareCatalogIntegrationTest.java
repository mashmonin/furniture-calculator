package com.example.furniturecalculator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

// Фурнитура прайс-листа «Эмаль Лайт» hausdoors_emal_layt_tsfo_05_10_2026 (change add-emal-layt-hardware).
// Ожидаемые значения заданы здесь независимо от SQL: {цвет, артикул, дилерская, розничная}.
@SpringBootTest
@AutoConfigureMockMvc
class EmalLaytHardwareCatalogIntegrationTest {

    private static final String PRICE_LIST_NAME = "hausdoors_emal_layt_tsfo_05_10_2026";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    // Новые типы: код → {название, категория, бренд}, затем варианты.
    private static final Map<String, String[]> TYPES = Map.ofEntries(
            Map.entry("HWT-021", new String[] {"ARMADILLO скрытая петля U3D2000.TG универсальная", "HWG-007", "ARMADILLO"}),
            Map.entry("HWT-022", new String[] {"AGB скрытая петля Eclipse 2.2 с декор.накладками, универсальная, 110х30х20", "HWG-008", "AGB"}),
            Map.entry("HWT-023", new String[] {"ARMADILLO Петля универсальная флажковая FL.IN3800.US /квадратная", "HWG-009", "ARMADILLO"}),
            Map.entry("HWT-024", new String[] {"ARMADILLO Петля универсальная флажковая FL.IN3800.UR /круглая", "HWG-009", "ARMADILLO"}),
            Map.entry("HWT-025", new String[] {"AGB защелка магнитная под завертку WC 96 мм Mediana Polaris 2XT (с отв.планкой Minimal XT)", "HWG-010", "AGB"}),
            Map.entry("HWT-026", new String[] {"AGB защелка магнитная под цилиндр 85 мм Mediana Polaris 2XT (с отв.планкой Minimal XT)", "HWG-010", "AGB"}),
            Map.entry("HWT-027", new String[] {"PUNTO Магнитная защелка врезная сантехническая ML96WC", "HWG-011", "PUNTO"}),
            Map.entry("HWT-028", new String[] {"PUNTO Магнитная защелка врезная под цилиндр ML85", "HWG-011", "PUNTO"}),
            Map.entry("HWT-029", new String[] {"FUARO Цилиндровый механизм ключ-завертка 1000ZMKnob60 (25+10+25)", "HWG-012", "FUARO"}),
            Map.entry("HWT-030", new String[] {"FUARO Цилиндровый механизм ключ-ключ 1000ZAKey60 (25+10+25)", "HWG-012", "FUARO"}),
            Map.entry("HWT-031", new String[] {"PUNTO Цилиндровый механизм ключ-завертка A2002Knob60 (25+10+25)", "HWG-012", "PUNTO"}),
            Map.entry("HWT-032", new String[] {"PUNTO Цилиндровый механизм ключ-ключ A2000Key60 (25+10+25)", "HWG-012", "PUNTO"}),
            Map.entry("HWT-033", new String[] {"FUARO Упор дверной торцевой RIGEL16-140", "HWG-013", "FUARO"}));

    private static final Map<String, Object[][]> OPTIONS = Map.ofEntries(
            Map.entry("HWT-021", new Object[][] {{"Матовый хром", "U3D2000.TG SC", 891, 1337}, {"Черный", "U3D2000.TG BL", 891, 1337}}),
            Map.entry("HWT-022", new Object[][] {{"Матовый хром", "E30200.85.34.640", 1598, 2398}, {"Черный", "E30200.85.93.640", 1997, 2996}}),
            Map.entry("HWT-023", new Object[][] {{"Матовый хром", "FL.IN3800.US MWSC-33", 628, 942}, {"Черный", "FL.IN3800.US BL", 628, 942}}),
            Map.entry("HWT-024", new Object[][] {{"Матовый хром", "FL.IN3800.UR MWSC-33", 590, 885}, {"Черный", "FL.IN3800.UR BL", 590, 885}}),
            Map.entry("HWT-025", new Object[][] {{"Матовый хром", "B06102.50.34.640", 1365, 2048}, {"Черный", "B06102.50.93.640", 1436, 2154}}),
            Map.entry("HWT-026", new Object[][] {{"Матовый хром", "B06103.50.34.640", 1448, 2172}, {"Черный", "B06103.50.93.640", 1532, 2298}}),
            Map.entry("HWT-027", new Object[][] {{"Черный", "50/BL BL", 383, 575}, {"Сатин.хром", "50/BL SSC-16", 383, 575}}),
            Map.entry("HWT-028", new Object[][] {{"Черный", "50/BL BL", 383, 575}, {"Сатин.хром", "50/BL SSC-16", 383, 575}}),
            Map.entry("HWT-029", new Object[][] {{"Черный", "100 ZM/60", 370, 555}}),
            Map.entry("HWT-030", new Object[][] {{"Черный", "100 ZA/60", 329, 494}}),
            Map.entry("HWT-031", new Object[][] {{"Никель", "A202/60", 341, 512}}),
            Map.entry("HWT-032", new Object[][] {{"Никель", "A200/60", 312, 468}}),
            Map.entry("HWT-033", new Object[][] {{"Черный", "(FB 140-16) BL-24", 179, 269}, {"Матовый никель", "(FB 140-16) SN-3", 179, 269}}));

    @Test
    void новые_типы_фурнитуры_с_брендом_артикулами_и_ценами_из_прайса() throws Exception {
        List<HardwareCategoryDto> catalog = performGetCatalog();

        List<HardwareTypeDto> newTypes = catalog.stream()
                .flatMap(c -> c.types().stream())
                .filter(t -> PRICE_LIST_NAME.equals(t.priceList().name()))
                .toList();
        assertThat(newTypes).hasSize(13);
        assertThat(newTypes.stream().mapToInt(t -> t.options().size()).sum()).isEqualTo(22);
        assertThat(catalog.stream()
                .filter(c -> c.types().stream().anyMatch(t -> PRICE_LIST_NAME.equals(t.priceList().name())))
                .map(c -> c.category().code()))
                .containsExactlyInAnyOrder("HWG-007", "HWG-008", "HWG-009", "HWG-010", "HWG-011", "HWG-012", "HWG-013");

        for (HardwareCategoryDto category : catalog) {
            for (HardwareTypeDto type : category.types()) {
                String[] expectedType = TYPES.get(type.type().code());
                if (expectedType == null) {
                    continue;
                }
                String code = type.type().code();
                assertThat(type.type().name()).as(code).isEqualTo(expectedType[0]);
                assertThat(category.category().code()).as(code).isEqualTo(expectedType[1]);
                assertThat(type.brand()).as(code).isEqualTo(expectedType[2]);
                assertThat(type.unit()).as(code).isEqualTo("шт");
                assertThat(type.priceList().code()).as(code).isEqualTo("PL-002");
                Object[][] expectedOptions = OPTIONS.get(code);
                assertThat(type.options()).as(code).hasSize(expectedOptions.length);
                for (Object[] expected : expectedOptions) {
                    HardwareOptionDto option = type.options().stream()
                            .filter(o -> o.colourName().equals(expected[0]))
                            .findFirst()
                            .orElseThrow();
                    assertThat(option.article()).as(code + " " + expected[0]).isEqualTo(expected[1]);
                    assertThat(option.dealerPrice()).as(code + " дилер").isEqualByComparingTo(BigDecimal.valueOf((int) expected[2]));
                    assertThat(option.retailPrice()).as(code + " розница").isEqualByComparingTo(BigDecimal.valueOf((int) expected[3]));
                }
            }
        }
    }

    @Test
    void существующая_фурнитура_на_pl_001_без_бренда_и_артикулов() throws Exception {
        List<HardwareTypeDto> oldTypes = performGetCatalog().stream()
                .flatMap(c -> c.types().stream())
                .filter(t -> "PL-001".equals(t.priceList().code()))
                .toList();

        assertThat(oldTypes).hasSize(20);
        assertThat(oldTypes.stream().mapToInt(t -> t.options().size()).sum()).isEqualTo(55);
        assertThat(oldTypes).allSatisfy(t -> {
            assertThat(t.brand()).as(t.type().code()).isNull();
            assertThat(t.priceList().name()).isEqualTo("hausdoors_emal_i_shpon_rf_07_09_2026");
            assertThat(t.options()).allSatisfy(o -> assertThat(o.article()).isNull());
        });
        // Цены существующей фурнитуры не изменились: AGB WC 96, матовый хром — розница 2 048, дилер 1 365.
        HardwareOptionDto agb = oldTypes.stream()
                .filter(t -> t.type().code().equals("HWT-001"))
                .flatMap(t -> t.options().stream())
                .filter(o -> o.colourName().equals("матовый хром"))
                .findFirst()
                .orElseThrow();
        assertThat(agb.retailPrice()).isEqualByComparingTo("2048");
        assertThat(agb.dealerPrice()).isEqualByComparingTo("1365");
    }

    @Test
    void расчёт_стоимости_фурнитуры_нового_прайса_по_цене_варианта_и_количеству() throws Exception {
        HardwareOptionDto eclipseBlack = performGetCatalog().stream()
                .flatMap(c -> c.types().stream())
                .filter(t -> t.type().code().equals("HWT-022"))
                .flatMap(t -> t.options().stream())
                .filter(o -> o.colourName().equals("Черный"))
                .findFirst()
                .orElseThrow();

        String json = mockMvc.perform(post("/api/hardware/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HardwarePricingRequestDto(
                                List.of(new HardwareSelectionDto(eclipseBlack.id(), 3))))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        HardwarePricingResponseDto response = objectMapper.readValue(json, HardwarePricingResponseDto.class);

        // 3 шт × (розница 2 996 / дилер 1 997) без надбавок.
        assertThat(response.totalRetailPrice()).isEqualByComparingTo("8988");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("5991");
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
