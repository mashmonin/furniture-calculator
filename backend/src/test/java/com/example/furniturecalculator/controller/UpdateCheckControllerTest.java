package com.example.furniturecalculator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.example.furniturecalculator.dto.UpdateCheckDto;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class UpdateCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void эндпоинт_возвращает_текущий_статус_проверки_обновлений() throws Exception {
        // app.update-check.url не задан в тестовом окружении — сервис по умолчанию
        // ничего не находит (см. UpdateCheckServiceTest — сама логика проверки
        // покрыта там отдельно, без полного Spring-контекста).
        String body = mockMvc.perform(get("/api/update-check")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        UpdateCheckDto result = objectMapper.readValue(body, UpdateCheckDto.class);

        assertThat(result).isEqualTo(UpdateCheckDto.notAvailable());
    }
}
