package com.example.furniturecalculator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.furniturecalculator.dto.DecorativeElementCategoryDto;
import com.example.furniturecalculator.dto.DecorativeElementTypeDto;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DecorativeElementCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void эндпоинт_возвращает_каталог_декоративных_элементов_из_начальных_данных() throws Exception {
        String body = mockMvc.perform(get("/api/decorative-elements-catalog"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<DecorativeElementCategoryDto> catalog =
                objectMapper.readValue(body, new TypeReference<List<DecorativeElementCategoryDto>>() {
                });

        assertThat(catalog).hasSize(1);
        DecorativeElementCategoryDto plinthCategory = catalog.get(0);
        assertThat(plinthCategory.category().code()).isEqualTo("DEC-001");
        assertThat(plinthCategory.category().name()).isEqualTo("Плинтус");
        assertThat(plinthCategory.types()).hasSize(2);

        DecorativeElementTypeDto modo = plinthCategory.types().stream()
                .filter(t -> "DET-001".equals(t.type().code()))
                .findFirst()
                .orElseThrow();
        assertThat(modo.type().name()).isEqualTo("Плинтус Модо");
        assertThat(modo.lengthMm()).isEqualByComparingTo("2400");
        assertThat(modo.retailPrice()).isEqualByComparingTo("2611");
        assertThat(modo.dealerPrice()).isEqualByComparingTo("1492");
    }
}
