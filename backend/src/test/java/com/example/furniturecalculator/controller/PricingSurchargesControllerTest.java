package com.example.furniturecalculator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.furniturecalculator.dto.PricingSurchargesDto;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PricingSurchargesControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void эндпоинт_возвращает_проценты_надбавок() throws Exception {
        String body = mockMvc.perform(get("/api/pricing-surcharges"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        PricingSurchargesDto result = objectMapper.readValue(body, PricingSurchargesDto.class);

        assertThat(result.reverseSurchargePercent()).isEqualByComparingTo("10");
        assertThat(result.dimensionSurchargeRules())
                .anySatisfy(rule -> {
                    assertThat(rule.dimensionType().code()).isEqualTo("DT-001");
                    assertThat(rule.value()).isEqualByComparingTo("950");
                    assertThat(rule.surchargePercent()).isEqualByComparingTo("20");
                });
        assertThat(result.mirrorFinishSurcharges())
                .anySatisfy(surcharge -> assertThat(surcharge.surchargePercent()).isEqualByComparingTo("30"));
        assertThat(result.colourSurcharges())
                .anySatisfy(surcharge -> {
                    assertThat(surcharge.name()).isEqualTo("Другой цвет из коллекции RAL и NCS");
                    assertThat(surcharge.surchargePercent()).isEqualByComparingTo("20");
                });
        assertThat(result.pogonazhSurchargeRules())
                .anySatisfy(rule -> {
                    assertThat(rule.ownerType()).isEqualTo("frame");
                    assertThat(rule.value()).isEqualByComparingTo("2400");
                    assertThat(rule.surchargePercent()).isEqualByComparingTo("30");
                });
    }
}
