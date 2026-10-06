package com.example.furniturecalculator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.example.furniturecalculator.domain.LeafPanelType;
import com.example.furniturecalculator.dto.ComponentCatalogDto;
import com.example.furniturecalculator.dto.ComponentPriceDto;
import com.example.furniturecalculator.dto.ComponentSelectionDto;
import com.example.furniturecalculator.dto.DoorConfigurationDto;
import com.example.furniturecalculator.dto.LinerDimensionOptionDto;
import com.example.furniturecalculator.dto.PricingRequestDto;
import com.example.furniturecalculator.dto.PricingResponseDto;
import com.example.furniturecalculator.dto.SpecificationExportRequestDto;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Каталог и цены сервиса «Эмаль Лайт» (change add-emal-layt-service). Работает по данным, заведённым
// миграцией 0116, поэтому не пишет в БД. Ожидаемые цены заданы здесь независимо от SQL (по прайсу
// hausdoors_emal_layt_tsfo_06_10_2026): дилерская / розничная.
@SpringBootTest
@AutoConfigureMockMvc
class EmalLaytCatalogIntegrationTest {

    private static final List<String> MODEL_NAMES = List.of(
            "MONO MN 01", "QUADRO QU 01", "QUADRO QU 02", "VENEZIA VN 01", "VENEZIA VN 02", "REFLEX RF 01");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void каталог_содержит_шесть_моделей_только_с_полотном_и_размерами_из_прайса() throws Exception {
        List<DoorConfigurationDto> models = emalLaytConfigurations(performGetCatalog());

        assertThat(models).extracting(c -> c.leaf().type().name()).containsExactlyInAnyOrderElementsOf(MODEL_NAMES);
        for (DoorConfigurationDto configuration : models) {
            assertThat(configuration.leaf().priceList().code()).isEqualTo("PL-002");
            assertThat(configuration.leaf().priceList().name()).isEqualTo("hausdoors_emal_layt_tsfo_06_10_2026");
            assertThat(configuration.frame()).isNull();
            assertThat(configuration.edge()).isNull();
            assertThat(configuration.doorCasing()).isNull();
            assertThat(configuration.frameExtensions()).isNull();
            assertThat(configuration.reverse()).isFalse();
            assertThat(configuration.hasQuarter()).isFalse();
            assertThat(widths(configuration)).containsExactlyInAnyOrder(400, 600, 700, 800, 900);
            assertThat(heights(configuration)).containsExactlyInAnyOrder(2000, 2100, 2200, 2300);
            assertThat(configuration.leaf().dimensionOptions()).allMatch(LinerDimensionOptionDto::standard);
            // Толщина — только 44 мм (стандартная), без 59 мм.
            assertThat(options(configuration, "ТОЛЩИНА")).extracting(o -> o.value().intValue()).containsExactly(44);
        }
    }

    @Test
    void толщина_44_не_меняет_цену_полотна() throws Exception {
        DoorConfigurationDto mono = emalLaytConfigurations(performGetCatalog()).stream()
                .filter(c -> c.leaf().type().name().equals("MONO MN 01"))
                .findFirst()
                .orElseThrow();
        Long width = option(mono, "ДЛИНА", 700).id();
        Long height = option(mono, "ВЫСОТА", 2000).id();
        Long thickness = option(mono, "ТОЛЩИНА", 44).id();

        PricingResponseDto without = price(mono.id(), width, height);
        PricingResponseDto with = priceWithThickness(mono.id(), width, height, thickness);

        assertThat(with.totalRetailPrice()).isEqualByComparingTo(without.totalRetailPrice()).isEqualByComparingTo("1000000");
        assertThat(with.totalDealerPrice()).isEqualByComparingTo(without.totalDealerPrice()).isEqualByComparingTo("1000000");
    }

    @Test
    void цены_всех_сочетаний_размеров_совпадают_с_прайсом_без_надбавок() throws Exception {
        List<DoorConfigurationDto> models = emalLaytConfigurations(performGetCatalog());

        int checked = 0;
        for (DoorConfigurationDto configuration : models) {
            String model = configuration.leaf().type().name();
            for (LinerDimensionOptionDto width : options(configuration, "ДЛИНА")) {
                for (LinerDimensionOptionDto height : options(configuration, "ВЫСОТА")) {
                    PricingResponseDto response = price(configuration.id(), width.id(), height.id());
                    ComponentPriceDto leaf = response.components().stream()
                            .filter(c -> c.component().equals("leaf"))
                            .findFirst()
                            .orElseThrow();
                    int[] expected = expectedPrice(model, width.value().intValue(), height.value().intValue());
                    String where = model + " " + width.value() + "x" + height.value();
                    assertThat(leaf.priced()).as(where).isTrue();
                    assertThat(leaf.dealerPrice()).as(where + " дилерская").isEqualByComparingTo(BigDecimal.valueOf(expected[0]));
                    assertThat(leaf.retailPrice()).as(where + " розничная").isEqualByComparingTo(BigDecimal.valueOf(expected[1]));
                    assertThat(leaf.baseRetailPrice()).as(where + " без надбавок").isEqualByComparingTo(leaf.retailPrice());
                    assertThat(response.totalRetailPrice()).as(where + " итог").isEqualByComparingTo(leaf.retailPrice());
                    checked++;
                }
            }
        }
        assertThat(checked).isEqualTo(6 * 5 * 4);
    }

    @Test
    void каталог_отдаёт_запрет_нестандартных_размеров_только_у_полотна_эмаль_лайт() throws Exception {
        List<DoorConfigurationDto> catalog = performGetCatalog();

        assertThat(emalLaytConfigurations(catalog))
                .allSatisfy(c -> assertThat(c.leaf().customDimensionsAllowed()).isFalse());
        DoorConfigurationDto emalIShpon = catalog.stream()
                .filter(c -> c.leaf().priceList() != null && c.leaf().priceList().code().equals("PL-001"))
                .findFirst()
                .orElseThrow();
        assertThat(emalIShpon.leaf().customDimensionsAllowed()).isTrue();
        assertThat(emalLaytConfigurations(catalog))
                .allSatisfy(c -> {
                    assertThat(c.frame()).isNull();
                    assertThat(c.edge()).isNull();
                });
    }

    @Test
    void произвольная_ширина_и_высота_у_полотна_эмаль_лайт_отклоняются() throws Exception {
        DoorConfigurationDto mono = emalLaytConfigurations(performGetCatalog()).stream()
                .filter(c -> c.leaf().type().name().equals("MONO MN 01"))
                .findFirst()
                .orElseThrow();
        DoorConfigurationDto quadro = emalLaytConfigurations(performGetCatalog()).stream()
                .filter(c -> c.leaf().type().name().equals("QUADRO QU 01"))
                .findFirst()
                .orElseThrow();

        postPrice(mono.id(), new ComponentSelectionDto(
                null, option(mono, "ВЫСОТА", 2000).id(), null, null, BigDecimal.valueOf(650), null, null, null, null, null, null))
                .andExpect(status().isBadRequest());
        postPrice(quadro.id(), new ComponentSelectionDto(
                option(quadro, "ДЛИНА", 700).id(), null, null, null, null, BigDecimal.valueOf(2150), null, null, null, null, null))
                .andExpect(status().isBadRequest());
        // Произвольное значение, совпадающее со стандартным, тоже отклоняется — доступны только каталожные опции.
        postPrice(mono.id(), new ComponentSelectionDto(
                null, option(mono, "ВЫСОТА", 2000).id(), null, null, BigDecimal.valueOf(700), null, null, null, null, null, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void высоты_2100_2200_2300_стоят_одинаково() throws Exception {
        DoorConfigurationDto reflex = emalLaytConfigurations(performGetCatalog()).stream()
                .filter(c -> c.leaf().type().name().equals("REFLEX RF 01"))
                .findFirst()
                .orElseThrow();
        LinerDimensionOptionDto width600 = option(reflex, "ДЛИНА", 600);

        for (int height : new int[] {2100, 2200, 2300}) {
            PricingResponseDto response = price(reflex.id(), width600.id(), option(reflex, "ВЫСОТА", height).id());
            assertThat(response.totalRetailPrice()).as("высота " + height).isEqualByComparingTo("20656");
            assertThat(response.totalDealerPrice()).as("высота " + height).isEqualByComparingTo("12519");
        }
    }

    @Test
    void короб_neo_75_есть_у_каждой_модели_эмаль_лайт_и_только_в_базовом_исполнении() throws Exception {
        List<DoorConfigurationDto> models = neo75Configurations(performGetCatalog());

        assertThat(models).extracting(c -> c.leaf().type().name()).containsExactlyInAnyOrderElementsOf(MODEL_NAMES);
        for (DoorConfigurationDto configuration : models) {
            assertThat(configuration.frame().type().name()).isEqualTo("NEO 75");
            assertThat(configuration.frame().type().code()).isEqualTo("FT-004");
            assertThat(configuration.edge()).isNull();
            assertThat(configuration.doorCasing()).isNull();
            assertThat(configuration.frameExtensions()).isNull();
            assertThat(configuration.reverse()).isFalse();
            assertThat(configuration.hasQuarter()).isFalse();
            assertThat(frameHeights(configuration)).containsExactlyInAnyOrder(2170, 2400);
            // Каждая высота — две позиции с привязкой к опции высоты.
            assertThat(configuration.frame().posts()).hasSize(4).allSatisfy(post -> assertThat(post.heightOptionId()).isNotNull());
        }
    }

    @Test
    void у_существующих_коробов_позиции_не_привязаны_к_высоте() throws Exception {
        DoorConfigurationDto neo = performGetCatalog().stream()
                .filter(c -> c.frame() != null && c.frame().type().code().equals("FT-003"))
                .findFirst()
                .orElseThrow();

        assertThat(neo.frame().posts()).isNotEmpty().allSatisfy(post -> assertThat(post.heightOptionId()).isNull());
    }

    @Test
    void стоимость_короба_neo_75_по_высотам_без_надбавок() throws Exception {
        DoorConfigurationDto mono = neo75Configurations(performGetCatalog()).stream()
                .filter(c -> c.leaf().type().name().equals("MONO MN 01"))
                .findFirst()
                .orElseThrow();
        Long width = option(mono, "ДЛИНА", 700).id();

        // Полотно 2000 → короб 2170: розница 2 317 + 4 633 = 6 950, дилер 1 404 + 2 808 = 4 212.
        ComponentPriceDto frame2170 = framePrice(mono, width, 2000, 2170);
        assertThat(frame2170.priced()).isTrue();
        assertThat(frame2170.retailPrice()).isEqualByComparingTo("6950");
        assertThat(frame2170.dealerPrice()).isEqualByComparingTo("4212");
        assertThat(frame2170.baseRetailPrice()).isEqualByComparingTo(frame2170.retailPrice());
        assertThat(frame2170.baseDealerPrice()).isEqualByComparingTo(frame2170.dealerPrice());

        // Полотно 2100/2200/2300 → короб 2400: розница 2 617 + 5 234 = 7 851, дилер 1 586 + 3 172 = 4 758.
        for (int leafHeight : new int[] {2100, 2200, 2300}) {
            ComponentPriceDto frame2400 = framePrice(mono, width, leafHeight, 2400);
            assertThat(frame2400.retailPrice()).as("полотно " + leafHeight).isEqualByComparingTo("7851");
            assertThat(frame2400.dealerPrice()).as("полотно " + leafHeight).isEqualByComparingTo("4758");
            assertThat(frame2400.baseRetailPrice()).isEqualByComparingTo(frame2400.retailPrice());
        }
    }

    @Test
    void высота_короба_neo_75_должна_подходить_к_высоте_полотна_и_быть_выбрана() throws Exception {
        DoorConfigurationDto mono = neo75Configurations(performGetCatalog()).stream()
                .filter(c -> c.leaf().type().name().equals("MONO MN 01"))
                .findFirst()
                .orElseThrow();
        Long width = option(mono, "ДЛИНА", 700).id();
        ComponentSelectionDto leaf2200 = leafSelection(mono, width, 2200);

        // Высота короба 2170 не покрывает высоту полотна 2200.
        postFramePrice(mono.id(), leaf2200, new ComponentSelectionDto(
                null, frameOption(mono, 2170).id(), null, null, null, null, null, null, null, null, null))
                .andExpect(status().isBadRequest());
        // Высота короба не выбрана.
        postFramePrice(mono.id(), leaf2200, ComponentSelectionDto.EMPTY).andExpect(status().isBadRequest());
    }

    @Test
    void выгрузка_спецификации_содержит_только_позиции_короба_нужной_высоты() throws Exception {
        DoorConfigurationDto mono = neo75Configurations(performGetCatalog()).stream()
                .filter(c -> c.leaf().type().name().equals("MONO MN 01"))
                .findFirst()
                .orElseThrow();
        Long width = option(mono, "ДЛИНА", 700).id();

        // Полотно 2000 → короб 2170; полотно 2300 → короб 2400. В каждом случае ровно две позиции (а не четыре).
        for (int[] heights : new int[][] {{2000, 2170}, {2300, 2400}}) {
            List<String> rows = exportRows(mono, width, heights[0], heights[1]);

            assertThat(rows.stream().filter(r -> r.contains("Стойка незарезная")).count()).as("полотно " + heights[0]).isEqualTo(1);
            assertThat(rows.stream().filter(r -> r.contains("Комплект зарезных стоек")).count()).as("полотно " + heights[0]).isEqualTo(1);
        }
    }

    @Test
    void у_каждой_модели_девять_сочетаний_наличника_и_добора_с_коробом_neo_75() throws Exception {
        List<DoorConfigurationDto> catalog = performGetCatalog();

        for (String model : MODEL_NAMES) {
            Set<String> combinations = allNeo75Configurations(catalog).stream()
                    .filter(c -> c.leaf().type().name().equals(model))
                    .peek(c -> {
                        assertThat(c.reverse()).isFalse();
                        assertThat(c.hasQuarter()).isFalse();
                        assertThat(c.edge()).isNull();
                    })
                    .map(c -> (c.doorCasing() == null ? "-" : c.doorCasing().type().code()) + "/"
                            + (c.frameExtensions() == null ? "-" : c.frameExtensions().type().code()))
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(combinations).as(model).containsExactlyInAnyOrder(
                    "-/-", "-/FET-014", "-/FET-015",
                    "DCT-010/-", "DCT-010/FET-014", "DCT-010/FET-015",
                    "DCT-011/-", "DCT-011/FET-014", "DCT-011/FET-015");
        }

        DoorConfigurationDto withBoth = neo75Configuration(catalog, "MONO MN 01", "DCT-010", "FET-015");
        assertThat(withBoth.doorCasing().type().name()).isEqualTo("Light");
        assertThat(withBoth.frameExtensions().type().name()).isEqualTo("Добор Neo 190 мм");
        assertThat(options(withBoth.doorCasing(), "ДЛИНА")).extracting(o -> o.value().intValue()).containsExactlyInAnyOrder(2250, 2400);
        assertThat(options(withBoth.frameExtensions(), "ДЛИНА")).extracting(o -> o.value().intValue()).containsExactlyInAnyOrder(2170, 2400);
    }

    @Test
    void новые_наличники_и_доборы_недоступны_другим_коллекциям_и_без_правил_погонажа() {
        Integer elsewhere = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM door_configuration dc JOIN leaf_type lt ON lt.id = dc.leaf_type_id "
                        + "WHERE (dc.door_casing_type_id IN (SELECT id FROM door_casing_type WHERE code IN ('DCT-010', 'DCT-011')) "
                        + "OR dc.frame_extensions_type_id IN (SELECT id FROM frame_extensions_type WHERE code IN ('FET-014', 'FET-015'))) "
                        + "AND lt.code NOT IN ('LT-046', 'LT-047', 'LT-048', 'LT-049', 'LT-050', 'LT-051')",
                Integer.class);
        Integer rules = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pogonazh_surcharge_rule WHERE door_casing_type_id IN "
                        + "(SELECT id FROM door_casing_type WHERE code IN ('DCT-010', 'DCT-011')) "
                        + "OR frame_extensions_type_id IN (SELECT id FROM frame_extensions_type WHERE code IN ('FET-014', 'FET-015'))",
                Integer.class);
        // Существующий «Модо» (DCT-003) остался прежним: розница 1 898, дилер 1 084.
        Integer modo = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM configuration_price p JOIN door_casing_type dct ON dct.id = p.door_casing_type_id "
                        + "WHERE dct.code = 'DCT-003' AND p.retail_price = 1898 AND p.dealer_price = 1084",
                Integer.class);

        assertThat(elsewhere).isZero();
        assertThat(rules).isZero();
        assertThat(modo).isEqualTo(1);
    }

    @Test
    void цены_наличников_и_доборов_по_длине_без_надбавок_и_с_количеством() throws Exception {
        List<DoorConfigurationDto> catalog = performGetCatalog();
        // {тип, высота полотна, ожидаемая длина, розница, дилер}
        Object[][] casings = {
                {"DCT-010", 2000, 2250, 1094, 663}, {"DCT-010", 2100, 2400, 1266, 767},
                {"DCT-010", 2200, 2400, 1266, 767}, {"DCT-010", 2300, 2400, 1266, 767},
                {"DCT-011", 2000, 2250, 1094, 663}, {"DCT-011", 2300, 2400, 1266, 767}};
        for (Object[] row : casings) {
            DoorConfigurationDto configuration = neo75Configuration(catalog, "MONO MN 01", (String) row[0], null);
            ComponentPriceDto price = componentPrice(configuration, (int) row[1], (int) row[2], null, null, "doorCasing");
            String where = row[0] + " полотно " + row[1];
            assertThat(price.priced()).as(where).isTrue();
            assertThat(price.retailPrice()).as(where).isEqualByComparingTo(BigDecimal.valueOf((int) row[3]));
            assertThat(price.dealerPrice()).as(where).isEqualByComparingTo(BigDecimal.valueOf((int) row[4]));
            assertThat(price.baseRetailPrice()).as(where).isEqualByComparingTo(price.retailPrice());
        }
        Object[][] extensions = {
                {"FET-014", 2000, 2170, 1330, 806}, {"FET-014", 2200, 2400, 1544, 936},
                {"FET-015", 2000, 2170, 2102, 1274}, {"FET-015", 2300, 2400, 2424, 1469}};
        for (Object[] row : extensions) {
            DoorConfigurationDto configuration = neo75Configuration(catalog, "QUADRO QU 01", null, (String) row[0]);
            ComponentPriceDto price = componentPrice(configuration, (int) row[1], null, (int) row[2], null, "frameExtensions");
            String where = row[0] + " полотно " + row[1];
            assertThat(price.priced()).as(where).isTrue();
            assertThat(price.retailPrice()).as(where).isEqualByComparingTo(BigDecimal.valueOf((int) row[3]));
            assertThat(price.dealerPrice()).as(where).isEqualByComparingTo(BigDecimal.valueOf((int) row[4]));
            assertThat(price.baseRetailPrice()).as(where).isEqualByComparingTo(price.retailPrice());
        }

        // Количество умножает цену за штуку: «Modo», полотно 2100, длина 2400, 2 шт → 2 532 / 1 534.
        DoorConfigurationDto modo = neo75Configuration(catalog, "MONO MN 01", "DCT-011", null);
        ComponentPriceDto twoPieces = componentPrice(modo, 2100, 2400, null, 2, "doorCasing");
        assertThat(twoPieces.retailPrice()).isEqualByComparingTo("2532");
        assertThat(twoPieces.dealerPrice()).isEqualByComparingTo("1534");
    }

    @Test
    void длина_наличника_и_добора_должна_подходить_к_высоте_полотна_и_быть_выбрана() throws Exception {
        List<DoorConfigurationDto> catalog = performGetCatalog();
        DoorConfigurationDto both = neo75Configuration(catalog, "MONO MN 01", "DCT-011", "FET-014");
        Long width = option(both, "ДЛИНА", 700).id();
        ComponentSelectionDto leaf2200 = leafSelection(both, width, 2200);
        ComponentSelectionDto frame2400 = new ComponentSelectionDto(
                null, frameOption(both, 2400).id(), null, null, null, null, null, null, null, null, null);
        ComponentSelectionDto casingOk = lengthSelection(both.doorCasing(), 2400);
        ComponentSelectionDto extensionOk = lengthSelection(both.frameExtensions(), 2400);

        // Длина наличника 2250 не покрывает высоту полотна 2200.
        postPricing(both.id(), leaf2200, frame2400, lengthSelection(both.doorCasing(), 2250), extensionOk)
                .andExpect(status().isBadRequest());
        // Длина добора 2170 не покрывает высоту полотна 2200.
        postPricing(both.id(), leaf2200, frame2400, casingOk, lengthSelection(both.frameExtensions(), 2170))
                .andExpect(status().isBadRequest());
        // Длина наличника не выбрана.
        postPricing(both.id(), leaf2200, frame2400, ComponentSelectionDto.EMPTY, extensionOk).andExpect(status().isBadRequest());
        // Длина добора не выбрана.
        postPricing(both.id(), leaf2200, frame2400, casingOk, ComponentSelectionDto.EMPTY).andExpect(status().isBadRequest());
        // Правильные длины — расчёт выполняется.
        postPricing(both.id(), leaf2200, frame2400, casingOk, extensionOk).andExpect(status().isOk());
    }

    @Test
    void полотна_эмаль_лайт_глухие_с_тремя_цветами() throws Exception {
        List<DoorConfigurationDto> models = emalLaytConfigurations(performGetCatalog());

        assertThat(models).hasSize(6);
        for (DoorConfigurationDto configuration : models) {
            String model = configuration.leaf().type().name();
            assertThat(configuration.leaf().panelType()).as(model).isEqualTo(LeafPanelType.BLIND);
            assertThat(configuration.leaf().mirrorFinishOptions()).as(model).isEmpty();
            assertThat(configuration.leaf().glazingOptions()).as(model).isEmpty();
            assertThat(configuration.leaf().colourOptions()).as(model)
                    .extracting(c -> c.colourType().name())
                    .containsExactlyInAnyOrder(
                            "Кремовая", "Серый вереск", "Белое облако");
            // Значения RAL/NCS в названиях цветов «Эмаль Лайт» не показываются.
            assertThat(configuration.leaf().colourOptions()).as(model)
                    .allSatisfy(c -> assertThat(c.colourType().name()).doesNotContain("RAL", "NCS", "("));
        }
    }

    @Test
    void новые_цвета_недоступны_другим_коллекциям_и_без_надбавки() {
        Integer elsewhere = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM colour_option co JOIN colour_type ct ON ct.id = co.colour_type_id "
                        + "JOIN leaf_type lt ON lt.id = co.leaf_type_id "
                        + "WHERE ct.code IN ('CT-033', 'CT-034', 'CT-035') "
                        + "AND lt.code NOT IN ('LT-046', 'LT-047', 'LT-048', 'LT-049', 'LT-050', 'LT-051')",
                Integer.class);
        Integer withSurcharge = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM colour_type WHERE code IN ('CT-033', 'CT-034', 'CT-035') AND surcharge_percent <> 0",
                Integer.class);

        assertThat(elsewhere).isZero();
        assertThat(withSurcharge).isZero();
    }

    @Test
    void цвет_не_меняет_цену_полотна() throws Exception {
        DoorConfigurationDto mono = emalLaytConfigurations(performGetCatalog()).stream()
                .filter(c -> c.leaf().type().name().equals("MONO MN 01"))
                .findFirst()
                .orElseThrow();
        Long width = option(mono, "ДЛИНА", 700).id();
        Long height = option(mono, "ВЫСОТА", 2000).id();

        PricingResponseDto withoutColour = price(mono.id(), width, height);
        assertThat(withoutColour.totalRetailPrice()).isEqualByComparingTo("1000000");
        for (var colour : mono.leaf().colourOptions()) {
            PricingRequestDto request = new PricingRequestDto(
                    new ComponentSelectionDto(width, height, null, colour.id(), null, null, null, null, null, null, null),
                    null, null, null, null);
            String json = mockMvc.perform(post("/api/door-configurations/{id}/price", mono.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            PricingResponseDto response = objectMapper.readValue(json, PricingResponseDto.class);
            String name = colour.colourType().name();
            assertThat(response.totalRetailPrice()).as(name).isEqualByComparingTo("1000000");
            assertThat(response.totalDealerPrice()).as(name).isEqualByComparingTo("1000000");
            assertThat(response.components().get(0).baseRetailPrice()).as(name).isEqualByComparingTo(response.components().get(0).retailPrice());
        }
    }

    @Test
    void у_короба_neo_75_нет_правил_погонажа() {
        Integer rules = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pogonazh_surcharge_rule WHERE frame_type_id = (SELECT id FROM frame_type WHERE code = 'FT-004')",
                Integer.class);

        assertThat(rules).isZero();
    }

    @Test
    void уникальность_позиций_короба_учитывает_высоту() {
        // Та же позиция для той же высоты отклоняется.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO frame_post (frame_type_id, post_type_id, quantity, length, dealer_price, retail_price, height_option_id) "
                        + "SELECT fp.frame_type_id, fp.post_type_id, 1, NULL, 1, 1, fp.height_option_id FROM frame_post fp "
                        + "JOIN frame_type ft ON ft.id = fp.frame_type_id JOIN post_type pt ON pt.id = fp.post_type_id "
                        + "WHERE ft.code = 'FT-004' AND pt.code = 'PT-001' AND fp.height_option_id IS NOT NULL FETCH FIRST 1 ROWS ONLY"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Позиция без привязки к высоте тоже остаётся уникальной (как у «НЕО»).
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO frame_post (frame_type_id, post_type_id, quantity, length, dealer_price, retail_price) "
                        + "SELECT frame_type_id, post_type_id, 1, NULL, 1, 1 FROM frame_post fp "
                        + "WHERE fp.height_option_id IS NULL FETCH FIRST 1 ROWS ONLY"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // {дилерская, розничная}: класс ширины (≤800 / 900) × класс высоты (2000 / 2100–2300).
    private static int[] expectedPrice(String model, int width, int height) {
        boolean wide = width == 900;
        boolean tall = height > 2000;
        if (model.equals("MONO MN 01")) {
            return wide ? (tall ? new int[] {13130, 21665} : new int[] {11843, 19541})
                    : (tall ? new int[] {11934, 19691} : new int[] {1000000, 1000000});
        }
        return wide ? (tall ? new int[] {13780, 22737} : new int[] {12493, 20613})
                : (tall ? new int[] {12519, 20656} : new int[] {11349, 18726});
    }

    // Конфигурации «Эмаль Лайт» без короба (только полотно).
    private static List<DoorConfigurationDto> emalLaytConfigurations(List<DoorConfigurationDto> catalog) {
        return catalog.stream()
                .filter(c -> c.leaf().priceList() != null && c.leaf().priceList().code().equals("PL-002"))
                .filter(c -> c.frame() == null)
                .toList();
    }

    // Базовые конфигурации «Эмаль Лайт» с коробом «NEO 75» — без наличника и добора (см. change add-neo-75-frame).
    private static List<DoorConfigurationDto> neo75Configurations(List<DoorConfigurationDto> catalog) {
        return catalog.stream()
                .filter(c -> c.leaf().priceList() != null && c.leaf().priceList().code().equals("PL-002"))
                .filter(c -> c.frame() != null && c.doorCasing() == null && c.frameExtensions() == null)
                .toList();
    }

    // Все конфигурации «Эмаль Лайт» с коробом «NEO 75», включая наличники и доборы (см. change
    // add-emal-layt-casings-extensions).
    private static List<DoorConfigurationDto> allNeo75Configurations(List<DoorConfigurationDto> catalog) {
        return catalog.stream()
                .filter(c -> c.leaf().priceList() != null && c.leaf().priceList().code().equals("PL-002"))
                .filter(c -> c.frame() != null)
                .toList();
    }

    private static DoorConfigurationDto neo75Configuration(
            List<DoorConfigurationDto> catalog, String model, String casingCode, String extensionCode) {
        return allNeo75Configurations(catalog).stream()
                .filter(c -> c.leaf().type().name().equals(model))
                .filter(c -> casingCode == null ? c.doorCasing() == null : c.doorCasing() != null && c.doorCasing().type().code().equals(casingCode))
                .filter(c -> extensionCode == null
                        ? c.frameExtensions() == null
                        : c.frameExtensions() != null && c.frameExtensions().type().code().equals(extensionCode))
                .findFirst()
                .orElseThrow();
    }

    private static List<LinerDimensionOptionDto> options(DoorConfigurationDto configuration, String dimensionTypeName) {
        return configuration.leaf().dimensionOptions().stream()
                .filter(o -> o.dimensionType().name().equals(dimensionTypeName))
                .toList();
    }

    private static LinerDimensionOptionDto option(DoorConfigurationDto configuration, String dimensionTypeName, int value) {
        return options(configuration, dimensionTypeName).stream()
                .filter(o -> o.value().intValue() == value)
                .findFirst()
                .orElseThrow();
    }

    private static Set<Integer> widths(DoorConfigurationDto configuration) {
        return options(configuration, "ДЛИНА").stream().map(o -> o.value().intValue()).collect(java.util.stream.Collectors.toSet());
    }

    private static Set<Integer> heights(DoorConfigurationDto configuration) {
        return options(configuration, "ВЫСОТА").stream().map(o -> o.value().intValue()).collect(java.util.stream.Collectors.toSet());
    }

    private org.springframework.test.web.servlet.ResultActions postPrice(Long configurationId, ComponentSelectionDto leaf) throws Exception {
        PricingRequestDto request = new PricingRequestDto(leaf, null, null, null, null);
        return mockMvc.perform(post("/api/door-configurations/{id}/price", configurationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private static Set<Integer> frameHeights(DoorConfigurationDto configuration) {
        return configuration.frame().dimensionOptions().stream()
                .filter(o -> o.dimensionType().name().equals("ВЫСОТА"))
                .map(o -> o.value().intValue())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static LinerDimensionOptionDto frameOption(DoorConfigurationDto configuration, int value) {
        return configuration.frame().dimensionOptions().stream()
                .filter(o -> o.dimensionType().name().equals("ВЫСОТА") && o.value().intValue() == value)
                .findFirst()
                .orElseThrow();
    }

    private static ComponentSelectionDto leafSelection(DoorConfigurationDto configuration, Long widthOptionId, int leafHeight) {
        return new ComponentSelectionDto(
                widthOptionId, option(configuration, "ВЫСОТА", leafHeight).id(), null, null, null, null, null, null, null, null, null);
    }

    // Цена компонента «короб» при выбранных высоте полотна и высоте короба.
    private ComponentPriceDto framePrice(DoorConfigurationDto configuration, Long widthOptionId, int leafHeight, int frameHeight)
            throws Exception {
        PricingRequestDto request = new PricingRequestDto(
                leafSelection(configuration, widthOptionId, leafHeight),
                new ComponentSelectionDto(null, frameOption(configuration, frameHeight).id(), null, null, null, null, null, null, null, null, null),
                null, null, null);
        String json = mockMvc.perform(post("/api/door-configurations/{id}/price", configuration.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, PricingResponseDto.class).components().stream()
                .filter(c -> c.component().equals("frame"))
                .findFirst()
                .orElseThrow();
    }

    // Все текстовые ячейки выгруженной одиночной спецификации — по одной строке на ряд листа.
    private List<String> exportRows(DoorConfigurationDto configuration, Long widthOptionId, int leafHeight, int frameHeight)
            throws Exception {
        SpecificationExportRequestDto request = new SpecificationExportRequestDto(
                configuration.leaf().type().id(), leafSelection(configuration, widthOptionId, leafHeight),
                null, null, false,
                configuration.frame().type().id(),
                new ComponentSelectionDto(null, frameOption(configuration, frameHeight).id(), null, null, null, null, null, null, null, null, null),
                null, null, null, null, BigDecimal.valueOf(leafHeight), null, null);
        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(post("/api/specification/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("выгрузка: %s", result.getResolvedException() != null ? result.getResolvedException().getMessage() : "")
                .isEqualTo(200);
        byte[] file = result.getResponse().getContentAsByteArray();
        List<String> rows = new java.util.ArrayList<>();
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(file))) {
            for (org.apache.poi.ss.usermodel.Row row : workbook.getSheetAt(0)) {
                StringBuilder text = new StringBuilder();
                for (org.apache.poi.ss.usermodel.Cell cell : row) {
                    if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                        text.append(cell.getStringCellValue()).append(" | ");
                    }
                }
                rows.add(text.toString());
            }
        }
        return rows;
    }

    private static List<LinerDimensionOptionDto> options(ComponentCatalogDto component, String dimensionTypeName) {
        return component.dimensionOptions().stream()
                .filter(o -> o.dimensionType().name().equals(dimensionTypeName))
                .toList();
    }

    // Выбор длины наличника/добора по значению опции (количество по умолчанию).
    private static ComponentSelectionDto lengthSelection(ComponentCatalogDto component, int length) {
        return lengthSelection(component, length, null);
    }

    private static ComponentSelectionDto lengthSelection(ComponentCatalogDto component, int length, Integer quantity) {
        Long optionId = options(component, "ДЛИНА").stream()
                .filter(o -> o.value().intValue() == length)
                .findFirst()
                .orElseThrow()
                .id();
        return new ComponentSelectionDto(optionId, null, null, null, null, null, quantity, null, null, null, null);
    }

    private org.springframework.test.web.servlet.ResultActions postPricing(
            Long configurationId, ComponentSelectionDto leaf, ComponentSelectionDto frame,
            ComponentSelectionDto casing, ComponentSelectionDto extensions) throws Exception {
        PricingRequestDto request = new PricingRequestDto(leaf, frame, null, casing, extensions);
        return mockMvc.perform(post("/api/door-configurations/{id}/price", configurationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    // Цена одного компонента конфигурации («doorCasing» / «frameExtensions»): полотно шириной 700 заданной высоты,
    // короб «NEO 75» подбирается по высоте полотна, длина наличника/добора и количество — по аргументам.
    private ComponentPriceDto componentPrice(
            DoorConfigurationDto configuration, int leafHeight, Integer casingLength, Integer extensionLength,
            Integer quantity, String componentName) throws Exception {
        int frameHeight = leafHeight == 2000 ? 2170 : 2400;
        ComponentSelectionDto frame = new ComponentSelectionDto(
                null, frameOption(configuration, frameHeight).id(), null, null, null, null, null, null, null, null, null);
        ComponentSelectionDto casing = casingLength == null ? null : lengthSelection(configuration.doorCasing(), casingLength, quantity);
        ComponentSelectionDto extensions =
                extensionLength == null ? null : lengthSelection(configuration.frameExtensions(), extensionLength, quantity);
        String json = postPricing(configuration.id(), leafSelection(configuration, option(configuration, "ДЛИНА", 700).id(), leafHeight),
                frame, casing, extensions)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, PricingResponseDto.class).components().stream()
                .filter(c -> c.component().equals(componentName))
                .findFirst()
                .orElseThrow();
    }

    private org.springframework.test.web.servlet.ResultActions postFramePrice(
            Long configurationId, ComponentSelectionDto leaf, ComponentSelectionDto frame) throws Exception {
        PricingRequestDto request = new PricingRequestDto(leaf, frame, null, null, null);
        return mockMvc.perform(post("/api/door-configurations/{id}/price", configurationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private PricingResponseDto price(Long configurationId, Long lengthOptionId, Long heightOptionId) throws Exception {
        return priceWithThickness(configurationId, lengthOptionId, heightOptionId, null);
    }

    private PricingResponseDto priceWithThickness(
            Long configurationId, Long lengthOptionId, Long heightOptionId, Long thicknessOptionId) throws Exception {
        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(lengthOptionId, heightOptionId, thicknessOptionId, null, null, null, null, null, null, null, null),
                null, null, null, null);
        String json = mockMvc.perform(post("/api/door-configurations/{id}/price", configurationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, PricingResponseDto.class);
    }

    // Весь каталог (около 8 000 конфигураций) тяжёлый — разбирается один раз на класс, а в памяти остаются
    // только конфигурации «Эмаль Лайт» (без короба и с «NEO 75»), одна конфигурация «Эмаль и шпон» для сравнения
    // и конфигурация с коробом «НЕО» для проверки, что у существующих коробов привязки к высоте нет.
    private static List<DoorConfigurationDto> cachedCatalog;

    private List<DoorConfigurationDto> performGetCatalog() throws Exception {
        if (cachedCatalog == null) {
            String json = mockMvc.perform(get("/api/door-configurations"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            List<DoorConfigurationDto> all = objectMapper.readValue(json, new TypeReference<List<DoorConfigurationDto>>() {
            });
            List<DoorConfigurationDto> trimmed = new java.util.ArrayList<>(emalLaytConfigurations(all));
            trimmed.addAll(allNeo75Configurations(all).stream().toList());
            all.stream()
                    .filter(c -> c.leaf().priceList() != null && c.leaf().priceList().code().equals("PL-001"))
                    .findFirst()
                    .ifPresent(trimmed::add);
            all.stream()
                    .filter(c -> c.frame() != null && c.frame().type().code().equals("FT-003"))
                    .findFirst()
                    .ifPresent(trimmed::add);
            cachedCatalog = trimmed;
        }
        return cachedCatalog;
    }
}
