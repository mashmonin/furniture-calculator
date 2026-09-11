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

import com.example.furniturecalculator.dto.HardwareCategoryDto;
import com.example.furniturecalculator.dto.HardwareTypeDto;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HardwareCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void эндпоинт_возвращает_каталог_фурнитуры_из_начальных_данных() throws Exception {
        String body = mockMvc.perform(get("/api/hardware-catalog"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<HardwareCategoryDto> catalog =
                objectMapper.readValue(body, new TypeReference<List<HardwareCategoryDto>>() {
                });

        assertThat(catalog).hasSize(6);

        HardwareCategoryDto locksCategory = catalog.stream()
                .filter(c -> "HWG-001".equals(c.category().code()))
                .findFirst()
                .orElseThrow();
        assertThat(locksCategory.category().name()).isEqualTo("Замки, цилиндры");
        assertThat(locksCategory.types()).hasSize(6);

        HardwareTypeDto wcLatch = locksCategory.types().stream()
                .filter(t -> "HWT-001".equals(t.type().code()))
                .findFirst()
                .orElseThrow();
        assertThat(wcLatch.unit()).isEqualTo("шт");
        assertThat(wcLatch.options()).hasSize(6);
        assertThat(wcLatch.options())
                .anySatisfy(option -> {
                    assertThat(option.colourName()).isEqualTo("матовый хром");
                    assertThat(option.retailPrice()).isEqualByComparingTo("2048");
                    assertThat(option.dealerPrice()).isEqualByComparingTo("1365");
                });
    }
}
