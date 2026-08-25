package com.example.furniturecalculator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
                new ComponentSelectionDto(lengthOptionId, null, null, null), ComponentSelectionDto.EMPTY, null, null, null);

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
        Long frameTypeId = insertReverseFrameType("IT-REVERSE-FRAME-1");
        Long configurationId = insertDoorConfiguration(leafTypeId, frameTypeId, null, null, null);

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
                new PricingRequestDto(new ComponentSelectionDto(frameLengthOptionId, null, null, null), null, null, null, null);

        mockMvc.perform(post("/api/door-configurations/{id}/price", configurationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
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
        Long collectionId = insertLeafCollection("COLL-" + code);
        return jdbcTemplate.queryForObject(
                "INSERT INTO leaf_type (code, name, collection_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, code, code, collectionId);
    }

    private Long insertLeafCollection(String code) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO collection (code, name) VALUES (?, ?) RETURNING id", Long.class, code, code);
    }

    private Long insertFrameType(String code) {
        return jdbcTemplate.queryForObject("INSERT INTO frame_type (code, name) VALUES (?, ?) RETURNING id", Long.class, code, code);
    }

    private Long insertReverseFrameType(String code) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO frame_type (code, name, is_reverse) VALUES (?, ?, true) RETURNING id", Long.class, code, code);
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
        return jdbcTemplate.queryForObject(
                "INSERT INTO door_configuration (leaf_type_id, frame_type_id, edge_type_id, door_casing_type_id, frame_extensions_type_id) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, leafTypeId, frameTypeId, edgeTypeId, doorCasingTypeId, frameExtensionsTypeId);
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
}
