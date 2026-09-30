package com.example.furniturecalculator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.furniturecalculator.domain.LeafPanelType;
import com.example.furniturecalculator.dto.ComponentSelectionDto;
import com.example.furniturecalculator.dto.DoorConfigurationDto;
import com.example.furniturecalculator.dto.PricingRequestDto;
import com.example.furniturecalculator.dto.PricingResponseDto;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DoorConfigurationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void каталог_возвращает_свежесозданную_конфигурацию_только_с_полотном() throws Exception {
        Long leafTypeId = insertLeafType("IT-CATALOG-1");
        Long configurationId = insertDoorConfiguration(leafTypeId, null, null, null, null);

        List<DoorConfigurationDto> configurations = performGetCatalog();

        DoorConfigurationDto created = findById(configurations, configurationId);
        assertThat(created.leaf()).isNotNull();
        assertThat(created.leaf().type().id()).isEqualTo(leafTypeId);
        assertThat(created.leaf().collection()).isNotNull();
        assertThat(created.frame()).isNull();
        assertThat(created.edge()).isNull();
        assertThat(created.doorCasing()).isNull();
        assertThat(created.frameExtensions()).isNull();
    }

    @Test
    void каталог_возвращает_позиции_frame_post_для_короба() throws Exception {
        Long leafTypeId = insertLeafType("IT-CATALOG-POSTS-LEAF-1");
        Long frameTypeId = insertFrameType("IT-CATALOG-POSTS-FRAME-1");
        Long configurationId = insertDoorConfiguration(leafTypeId, frameTypeId, null, null, null);

        Long topPostTypeId = insertPostType("IT-CATALOG-POSTS-TOP-1");
        Long sidePostTypeId = insertPostType("IT-CATALOG-POSTS-SIDE-1");
        insertFramePost(frameTypeId, topPostTypeId, 1, null, BigDecimal.valueOf(3549), BigDecimal.valueOf(2027));
        insertFramePost(frameTypeId, sidePostTypeId, 2, BigDecimal.valueOf(2170), BigDecimal.valueOf(7047), BigDecimal.valueOf(4027));

        List<DoorConfigurationDto> configurations = performGetCatalog();

        DoorConfigurationDto created = findById(configurations, configurationId);
        assertThat(created.frame()).isNotNull();
        assertThat(created.frame().posts()).hasSize(2);
        assertThat(created.frame().posts())
                .extracting(post -> post.postType().id())
                .containsExactlyInAnyOrder(topPostTypeId, sidePostTypeId);
    }

    @Test
    void каталог_возвращает_исполнения_зеркала_только_для_полотна() throws Exception {
        Long leafTypeId = insertLeafType("IT-MIRROR-LEAF-1");
        Long frameTypeId = insertFrameType("IT-MIRROR-FRAME-1");
        Long configurationId = insertDoorConfiguration(leafTypeId, frameTypeId, null, null, null);

        Long mirrorFinishTypeId = insertMirrorFinishType("Зеркало с фацетом", BigDecimal.valueOf(40));
        insertMirrorFinishOption(mirrorFinishTypeId, leafTypeId);

        List<DoorConfigurationDto> configurations = performGetCatalog();

        DoorConfigurationDto created = findById(configurations, configurationId);
        assertThat(created.leaf().mirrorFinishOptions()).hasSize(1);
        assertThat(created.leaf().mirrorFinishOptions().get(0).name()).isEqualTo("Зеркало с фацетом");
        assertThat(created.frame().mirrorFinishOptions()).isEmpty();
    }

    @Test
    void каталог_возвращает_пустой_список_исполнений_зеркала_если_их_нет() throws Exception {
        Long leafTypeId = insertLeafType("IT-MIRROR-LEAF-2");
        Long configurationId = insertDoorConfiguration(leafTypeId, null, null, null, null);

        List<DoorConfigurationDto> configurations = performGetCatalog();

        DoorConfigurationDto created = findById(configurations, configurationId);
        assertThat(created.leaf().mirrorFinishOptions()).isEmpty();
    }

    @Test
    void каталог_возвращает_тип_полотна_только_для_leaf_компонента() throws Exception {
        Long leafTypeId = insertLeafType("IT-PANEL-TYPE-LEAF-1", "GLAZED");
        Long frameTypeId = insertFrameType("IT-PANEL-TYPE-FRAME-1");
        Long configurationId = insertDoorConfiguration(leafTypeId, frameTypeId, null, null, null);

        List<DoorConfigurationDto> configurations = performGetCatalog();

        DoorConfigurationDto created = findById(configurations, configurationId);
        assertThat(created.leaf().panelType()).isEqualTo(LeafPanelType.GLAZED);
        assertThat(created.frame().panelType()).isNull();
    }

    @Test
    void каталог_возвращает_виды_остекления_только_для_полотна() throws Exception {
        Long leafTypeId = insertLeafType("IT-GLAZING-LEAF-1", "GLAZED");
        Long frameTypeId = insertFrameType("IT-GLAZING-FRAME-1");
        Long configurationId = insertDoorConfiguration(leafTypeId, frameTypeId, null, null, null);

        Long glazingTypeId = insertGlazingType("Сатинированное");
        insertGlazingOption(glazingTypeId, leafTypeId);

        List<DoorConfigurationDto> configurations = performGetCatalog();

        DoorConfigurationDto created = findById(configurations, configurationId);
        assertThat(created.leaf().glazingOptions()).hasSize(1);
        assertThat(created.leaf().glazingOptions().get(0).name()).isEqualTo("Сатинированное");
        assertThat(created.frame().glazingOptions()).isEmpty();
    }

    @Test
    void каталог_возвращает_пустой_список_видов_остекления_если_их_нет() throws Exception {
        Long leafTypeId = insertLeafType("IT-GLAZING-LEAF-2");
        Long configurationId = insertDoorConfiguration(leafTypeId, null, null, null, null);

        List<DoorConfigurationDto> configurations = performGetCatalog();

        DoorConfigurationDto created = findById(configurations, configurationId);
        assertThat(created.leaf().glazingOptions()).isEmpty();
    }

    @Test
    void glazing_type_без_наименования_отклоняется() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO glazing_type DEFAULT VALUES"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void повторная_пара_glazing_type_и_leaf_type_отклоняется() {
        Long leafTypeId = insertLeafType("IT-GLAZING-LEAF-3");
        Long glazingTypeId = insertGlazingType("IT-GLAZING-TYPE-DUP");
        insertGlazingOption(glazingTypeId, leafTypeId);

        assertThatThrownBy(() -> insertGlazingOption(glazingTypeId, leafTypeId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void leaf_type_без_типа_полотна_отклоняется() {
        Long collectionId = insertLeafCollection("COLL-IT-PANEL-TYPE-MISSING-1");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO leaf_type (code, name, collection_id) VALUES (?, ?, ?)",
                "IT-PANEL-TYPE-MISSING-1", "IT-PANEL-TYPE-MISSING-1", collectionId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void leaf_type_с_недопустимым_типом_полотна_отклоняется() {
        Long collectionId = insertLeafCollection("COLL-IT-PANEL-TYPE-INVALID-1");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO leaf_type (code, name, collection_id, panel_type) VALUES (?, ?, ?, ?)",
                "IT-PANEL-TYPE-INVALID-1", "IT-PANEL-TYPE-INVALID-1", collectionId, "SOLID"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void leaf_type_с_упразднённым_типом_полотна_с_зеркалом_отклоняется() {
        Long collectionId = insertLeafCollection("COLL-IT-PANEL-TYPE-MIRRORED-1");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO leaf_type (code, name, collection_id, panel_type) VALUES (?, ?, ?, ?)",
                "IT-PANEL-TYPE-MIRRORED-1", "IT-PANEL-TYPE-MIRRORED-1", collectionId, "MIRRORED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void mirror_finish_option_для_остеклённого_leaf_type_отклоняется() {
        Long leafTypeId = insertLeafType("IT-MIRROR-GLAZED-1", "GLAZED");
        Long mirrorFinishTypeId = insertMirrorFinishType("IT-MIRROR-GLAZED-FINISH-1", BigDecimal.valueOf(30));

        assertThatThrownBy(() -> insertMirrorFinishOption(mirrorFinishTypeId, leafTypeId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void расчёт_стоимости_суммирует_найденные_компоненты() throws Exception {
        Long leafTypeId = insertLeafType("IT-PRICE-LEAF-1");
        Long frameTypeId = insertFrameType("IT-PRICE-FRAME-1");
        Long configurationId = insertDoorConfiguration(leafTypeId, frameTypeId, null, null, null);

        Long lengthDimensionTypeId =
                jdbcTemplate.queryForObject("SELECT id FROM liner_dimension_type WHERE code = 'DT-001'", Long.class);
        Long lengthOptionId = insertLinerDimensionOption(
                lengthDimensionTypeId, BigDecimal.valueOf(600), true, leafTypeId, null, null, null, null);

        insertConfigurationPrice(
                BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafTypeId, null, null, null, lengthOptionId, null, null);
        Long postTypeId = insertPostType("IT-PRICE-POST-1");
        insertFramePost(frameTypeId, postTypeId, 1, null, BigDecimal.valueOf(300), BigDecimal.valueOf(250));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(lengthOptionId, null, null, null, null, null, null, null, null, null, null), ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = performPost(configurationId, request, PricingResponseDto.class);

        assertThat(response.totalRetailPrice()).isEqualByComparingTo("1300");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("1150");
        assertThat(response.components()).hasSize(2);
        assertThat(response.components()).allMatch(c -> c.priced());
    }

    @Test
    void стоимость_короба_суммирует_несколько_записей_frame_post() throws Exception {
        Long leafTypeId = insertLeafType("IT-FRAME-POST-LEAF-1");
        Long frameTypeId = insertFrameType("IT-FRAME-POST-FRAME-1");
        Long configurationId = insertDoorConfiguration(leafTypeId, frameTypeId, null, null, null);

        Long topPostTypeId = insertPostType("IT-FRAME-POST-TOP-1");
        Long sidePostTypeId = insertPostType("IT-FRAME-POST-SIDE-1");
        insertFramePost(frameTypeId, topPostTypeId, 1, null, BigDecimal.valueOf(3549), BigDecimal.valueOf(2027));
        insertFramePost(frameTypeId, sidePostTypeId, 2, BigDecimal.valueOf(2170), BigDecimal.valueOf(7047), BigDecimal.valueOf(4027));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = performPost(configurationId, request, PricingResponseDto.class);

        var framePrice = response.components().stream()
                .filter(c -> c.component().equals("frame"))
                .findFirst()
                .orElseThrow();
        assertThat(framePrice.priced()).isTrue();
        assertThat(framePrice.retailPrice()).isEqualByComparingTo("10596");
        assertThat(framePrice.dealerPrice()).isEqualByComparingTo("6054");
    }

    @Test
    void надбавка_за_реверс_применяется_к_цене_полотна_если_короб_реверсивный() throws Exception {
        Long leafTypeId = insertLeafType("IT-REVERSE-LEAF-1");
        Long frameTypeId = insertFrameType("IT-REVERSE-FRAME-1");
        Long configurationId = insertDoorConfiguration(leafTypeId, frameTypeId, null, null, null, true);

        insertConfigurationPrice(
                BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafTypeId, null, null, null, null, null, null);

        PricingRequestDto request =
                new PricingRequestDto(ComponentSelectionDto.EMPTY, ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = performPost(configurationId, request, PricingResponseDto.class);

        var leafPrice = response.components().stream()
                .filter(c -> c.component().equals("leaf"))
                .findFirst()
                .orElseThrow();
        assertThat(leafPrice.priced()).isTrue();
        assertThat(leafPrice.retailPrice()).isEqualByComparingTo("1100");
        assertThat(leafPrice.dealerPrice()).isEqualByComparingTo("990");
    }

    @Test
    void расчёт_для_несуществующей_конфигурации_возвращает_404() throws Exception {
        mockMvc.perform(post("/api/door-configurations/{id}/price", 999_999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void опция_чужого_компонента_в_запросе_возвращает_400() throws Exception {
        Long leafTypeId = insertLeafType("IT-VALIDATION-LEAF-1");
        Long frameTypeId = insertFrameType("IT-VALIDATION-FRAME-1");
        Long configurationId = insertDoorConfiguration(leafTypeId, null, null, null, null);

        Long lengthDimensionTypeId =
                jdbcTemplate.queryForObject("SELECT id FROM liner_dimension_type WHERE code = 'DT-001'", Long.class);
        Long frameLengthOptionId = insertLinerDimensionOption(
                lengthDimensionTypeId, BigDecimal.valueOf(600), true, null, frameTypeId, null, null, null);

        PricingRequestDto request =
                new PricingRequestDto(new ComponentSelectionDto(frameLengthOptionId, null, null, null, null, null, null, null, null, null, null), null, null, null, null);

        mockMvc.perform(post("/api/door-configurations/{id}/price", configurationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void расчёт_стоимости_отдельного_полотна_не_требует_door_configuration() throws Exception {
        // Строка door_configuration намеренно не создаётся — только leaf_type и его цена
        // (см. change add-standalone-leaf-pricing).
        Long leafTypeId = insertLeafType("IT-LEAF-PRICE-1");
        insertConfigurationPrice(
                BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafTypeId, null, null, null, null, null, null);

        PricingRequestDto request = new PricingRequestDto(ComponentSelectionDto.EMPTY, null, null, null, null);

        PricingResponseDto response = performLeafPost(leafTypeId, request, PricingResponseDto.class);

        assertThat(response.totalRetailPrice()).isEqualByComparingTo("1000");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("900");
        assertThat(response.components()).hasSize(1);
        assertThat(response.components().get(0).component()).isEqualTo("leaf");
    }

    @Test
    void расчёт_стоимости_отдельного_полотна_для_несуществующего_типа_возвращает_404() throws Exception {
        PricingRequestDto request = new PricingRequestDto(ComponentSelectionDto.EMPTY, null, null, null, null);

        mockMvc.perform(post("/api/leaf-types/{id}/price", 999_999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    private <T> T performLeafPost(Long leafTypeId, PricingRequestDto request, Class<T> responseType) throws Exception {
        String json = mockMvc.perform(post("/api/leaf-types/{id}/price", leafTypeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, responseType);
    }

    private List<DoorConfigurationDto> performGetCatalog() throws Exception {
        String json = mockMvc.perform(get("/api/door-configurations"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, new TypeReference<List<DoorConfigurationDto>>() {
        });
    }

    private <T> T performPost(Long configurationId, PricingRequestDto request, Class<T> responseType) throws Exception {
        String json = mockMvc.perform(post("/api/door-configurations/{id}/price", configurationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, responseType);
    }

    private DoorConfigurationDto findById(List<DoorConfigurationDto> configurations, Long id) {
        return configurations.stream()
                .filter(dto -> dto.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private Long insertLeafType(String code) {
        return insertLeafType(code, "BLIND");
    }

    private Long insertLeafType(String code, String panelType) {
        Long collectionId = insertLeafCollection("COLL-" + code);
        return jdbcTemplate.queryForObject(
                "INSERT INTO leaf_type (code, name, collection_id, panel_type) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, code, code, collectionId, panelType);
    }

    private Long insertLeafCollection(String code) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO collection (code, name) VALUES (?, ?) RETURNING id", Long.class, code, code);
    }

    private Long insertFrameType(String code) {
        return jdbcTemplate.queryForObject("INSERT INTO frame_type (code, name) VALUES (?, ?) RETURNING id", Long.class, code, code);
    }

    private Long insertPostType(String code) {
        return jdbcTemplate.queryForObject("INSERT INTO post_type (code, name) VALUES (?, ?) RETURNING id", Long.class, code, code);
    }

    private Long insertFramePost(
            Long frameTypeId, Long postTypeId, int quantity, BigDecimal length, BigDecimal retailPrice, BigDecimal dealerPrice) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO frame_post (frame_type_id, post_type_id, quantity, length, retail_price, dealer_price) "
                        + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, frameTypeId, postTypeId, quantity, length, retailPrice, dealerPrice);
    }

    private Long insertDoorConfiguration(
            Long leafTypeId, Long frameTypeId, Long edgeTypeId, Long doorCasingTypeId, Long frameExtensionsTypeId) {
        return insertDoorConfiguration(leafTypeId, frameTypeId, edgeTypeId, doorCasingTypeId, frameExtensionsTypeId, false);
    }

    private Long insertDoorConfiguration(
            Long leafTypeId, Long frameTypeId, Long edgeTypeId, Long doorCasingTypeId, Long frameExtensionsTypeId,
            boolean isReverse) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO door_configuration "
                        + "(leaf_type_id, frame_type_id, edge_type_id, door_casing_type_id, frame_extensions_type_id, is_reverse) "
                        + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, leafTypeId, frameTypeId, edgeTypeId, doorCasingTypeId, frameExtensionsTypeId, isReverse);
    }

    private Long insertLinerDimensionOption(
            Long dimensionTypeId, BigDecimal value, boolean standard,
            Long leafTypeId, Long frameTypeId, Long edgeTypeId, Long doorCasingTypeId, Long frameExtensionsTypeId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO liner_dimension_option "
                        + "(liner_dimension_type_id, value, is_standard, leaf_type_id, frame_type_id, edge_type_id, door_casing_type_id, frame_extensions_type_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, dimensionTypeId, value, standard, leafTypeId, frameTypeId, edgeTypeId, doorCasingTypeId, frameExtensionsTypeId);
    }

    private Long insertConfigurationPrice(
            BigDecimal retailPrice, BigDecimal dealerPrice,
            Long leafTypeId, Long frameTypeId, Long edgeTypeId, Long doorCasingTypeId,
            Long lengthOptionId, Long heightOptionId, Long colourOptionId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO configuration_price "
                        + "(retail_price, dealer_price, leaf_type_id, frame_type_id, edge_type_id, door_casing_type_id, length_option_id, height_option_id, colour_option_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, retailPrice, dealerPrice, leafTypeId, frameTypeId, edgeTypeId, doorCasingTypeId,
                lengthOptionId, heightOptionId, colourOptionId);
    }

    private Long insertMirrorFinishType(String name, BigDecimal surchargePercent) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO mirror_finish_type (name, surcharge_percent) VALUES (?, ?) RETURNING id",
                Long.class, name, surchargePercent);
    }

    private Long insertMirrorFinishOption(Long mirrorFinishTypeId, Long leafTypeId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO mirror_finish_option (mirror_finish_type_id, leaf_type_id) VALUES (?, ?) RETURNING id",
                Long.class, mirrorFinishTypeId, leafTypeId);
    }

    private Long insertGlazingType(String name) {
        return jdbcTemplate.queryForObject("INSERT INTO glazing_type (name) VALUES (?) RETURNING id", Long.class, name);
    }

    private Long insertGlazingOption(Long glazingTypeId, Long leafTypeId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO glazing_option (glazing_type_id, leaf_type_id) VALUES (?, ?) RETURNING id",
                Long.class, glazingTypeId, leafTypeId);
    }

    private Long insertDoorCasingType(String code) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO door_casing_type (code, name) VALUES (?, ?) RETURNING id", Long.class, code, code);
    }

    private Long insertFrameExtensionsType(String code) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO frame_extensions_type (code, name) VALUES (?, ?) RETURNING id", Long.class, code, code);
    }

    private Long insertPogonazhSurchargeRule(
            Long frameTypeId, Long doorCasingTypeId, Long frameExtensionsTypeId, BigDecimal value, BigDecimal surchargePercent) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO pogonazh_surcharge_rule (frame_type_id, door_casing_type_id, frame_extensions_type_id, value, surcharge_percent) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, frameTypeId, doorCasingTypeId, frameExtensionsTypeId, value, surchargePercent);
    }

    @Test
    void pogonazh_surcharge_rule_без_владельца_отклоняется() {
        assertThatThrownBy(() -> insertPogonazhSurchargeRule(null, null, null, BigDecimal.valueOf(2400), BigDecimal.valueOf(30)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pogonazh_surcharge_rule_с_двумя_владельцами_отклоняется() {
        Long frameTypeId = insertFrameType("IT-POGONAZH-OWNERS-FRAME-1");
        Long doorCasingTypeId = insertDoorCasingType("IT-POGONAZH-OWNERS-CASING-1");

        assertThatThrownBy(() -> insertPogonazhSurchargeRule(
                frameTypeId, doorCasingTypeId, null, BigDecimal.valueOf(2400), BigDecimal.valueOf(30)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pogonazh_surcharge_rule_повторное_значение_для_того_же_короба_отклоняется() {
        Long frameTypeId = insertFrameType("IT-POGONAZH-DUP-FRAME-1");
        insertPogonazhSurchargeRule(frameTypeId, null, null, BigDecimal.valueOf(2400), BigDecimal.valueOf(30));

        assertThatThrownBy(() -> insertPogonazhSurchargeRule(frameTypeId, null, null, BigDecimal.valueOf(2400), BigDecimal.valueOf(30)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pogonazh_surcharge_rule_одинаковое_значение_для_разных_владельцев_допустимо() {
        Long frameTypeId = insertFrameType("IT-POGONAZH-SHARED-FRAME-1");
        Long doorCasingTypeId = insertDoorCasingType("IT-POGONAZH-SHARED-CASING-1");
        Long frameExtensionsTypeId = insertFrameExtensionsType("IT-POGONAZH-SHARED-EXT-1");

        Long frameRuleId = insertPogonazhSurchargeRule(frameTypeId, null, null, BigDecimal.valueOf(2400), BigDecimal.valueOf(30));
        Long casingRuleId = insertPogonazhSurchargeRule(null, doorCasingTypeId, null, BigDecimal.valueOf(2400), BigDecimal.valueOf(30));
        Long extensionsRuleId =
                insertPogonazhSurchargeRule(null, null, frameExtensionsTypeId, BigDecimal.valueOf(2400), BigDecimal.valueOf(30));

        assertThat(frameRuleId).isNotNull();
        assertThat(casingRuleId).isNotNull();
        assertThat(extensionsRuleId).isNotNull();
    }
}
