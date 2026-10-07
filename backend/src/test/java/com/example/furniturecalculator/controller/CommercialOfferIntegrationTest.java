package com.example.furniturecalculator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.furniturecalculator.dto.ColourOptionDto;
import com.example.furniturecalculator.dto.ComponentSelectionDto;
import com.example.furniturecalculator.dto.DoorConfigurationDto;
import com.example.furniturecalculator.dto.OrderLineExportRequestDto;
import com.example.furniturecalculator.dto.SpecificationExportRequestDto;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Выгрузка КП (change add-commercial-offer-pdf-export, commercial-offer-export) на реальном каталоге
// «Эмаль Лайт» (MONO MN 01, цена 700×2000 — 17 761 ₽ розница, 10 764 ₽ дилер). БД не изменяется.
@SpringBootTest
@AutoConfigureMockMvc
class CommercialOfferIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void кп_содержит_шапку_характеристики_клиентские_цены_без_дилерских() throws Exception {
        DoorConfigurationDto mono = monoConfiguration();
        OrderLineExportRequestDto line = line(mono, "Белое облако", 2);

        MvcResult result = postOffer(List.of(line)).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("application/pdf");
        String disposition = URLDecoder.decode(result.getResponse().getHeader("Content-Disposition"), StandardCharsets.UTF_8);
        assertThat(disposition).startsWith("attachment").containsPattern("КП-\\d{10}\\.pdf");

        PdfReader reader = new PdfReader(result.getResponse().getContentAsByteArray());
        // Одна позиция — одна страница (без обложки и страницы контактов).
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(text).contains("Номер заказа").contains("Дата и время заказа").contains("Модель").contains("MONO MN 01");
        assertThat(text).contains("Цвет").contains("Белое облако").contains("700*2000*44");
        // Извлечение текста по-разному передаёт неразрывные пробелы — сравниваем без пробельных символов.
        String compact = compact(text);
        assertThat(compact).contains("Итоговаяцена:35522₽").contains("17761₽");
        assertThat(compact).doesNotContain("10764");
    }

    @Test
    void итог_по_заказу_суммирует_позиции_и_каждая_позиция_на_своей_странице() throws Exception {
        DoorConfigurationDto mono = monoConfiguration();
        MvcResult result = postOffer(List.of(line(mono, "Белое облако", 2), line(mono, "Серый вереск", 1)))
                .andExpect(status().isOk()).andReturn();

        PdfReader reader = new PdfReader(result.getResponse().getContentAsByteArray());
        assertThat(reader.getNumberOfPages()).isEqualTo(2);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        assertThat(compact(extractor.getTextFromPage(1))).contains("Позиция1из2").doesNotContain("Итогопозаказу");
        // 17 761 × 2 + 17 761 = 53 283 — итог на последней странице.
        assertThat(compact(extractor.getTextFromPage(2))).contains("Позиция2из2").contains("Итогопозаказу53283₽");
    }

    @Test
    void изображение_двери_выводится_только_если_оно_есть_для_цвета() throws Exception {
        DoorConfigurationDto mono = monoConfiguration();
        // «Белое облако» — есть файл; «Кремовая» для MONO MN 01 — файла нет (страница без изображения).
        int withImage = xobjectCount(postOffer(List.of(line(mono, "Белое облако", 1))).andReturn());
        int withoutImage = xobjectCount(postOffer(List.of(line(mono, "Кремовая", 1))).andReturn());

        assertThat(withImage).isEqualTo(withoutImage + 1);
    }

    @Test
    void пустой_список_отклоняется() throws Exception {
        postOffer(List.of()).andExpect(status().isBadRequest());
    }

    @Test
    void количество_меньше_единицы_отклоняется() throws Exception {
        postOffer(List.of(line(monoConfiguration(), "Белое облако", 0))).andExpect(status().isBadRequest());
    }

    @Test
    void несуществующий_тип_полотна_даёт_404() throws Exception {
        OrderLineExportRequestDto line = new OrderLineExportRequestDto("X", 1,
                new SpecificationExportRequestDto(999999L, null, null, null, false, null, null, null, null, null, null,
                        BigDecimal.valueOf(2000), null, null), List.of());
        postOffer(List.of(line)).andExpect(status().isNotFound());
    }

    private static String compact(String text) {
        return text.replaceAll("[\\s\u00A0]+", "");
    }

    private org.springframework.test.web.servlet.ResultActions postOffer(List<OrderLineExportRequestDto> lines) throws Exception {
        return mockMvc.perform(post("/api/specification/export-offer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(lines)));
    }

    // Число XObject'ов (изображений) на странице позиции (стр. 1): логотип шапки + при наличии картинка двери.
    private int xobjectCount(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        PdfReader reader = new PdfReader(result.getResponse().getContentAsByteArray());
        var resources = reader.getPageN(1).getAsDict(com.lowagie.text.pdf.PdfName.RESOURCES);
        var xobjects = resources.getAsDict(com.lowagie.text.pdf.PdfName.XOBJECT);
        return xobjects == null ? 0 : xobjects.size();
    }

    // Каталог большой (~8 000 конфигураций) — забираем только нужную конфигурацию и один раз на класс.
    private static DoorConfigurationDto cachedMono;

    private DoorConfigurationDto monoConfiguration() throws Exception {
        if (cachedMono == null) {
            String json = mockMvc.perform(get("/api/door-configurations")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            cachedMono = objectMapper.readValue(json, new TypeReference<List<DoorConfigurationDto>>() {
            }).stream()
                    .filter(c -> c.leaf().priceList() != null && c.leaf().priceList().code().equals("PL-002"))
                    .filter(c -> c.leaf().type().name().equals("MONO MN 01"))
                    .findFirst()
                    .orElseThrow();
        }
        return cachedMono;
    }

    private OrderLineExportRequestDto line(DoorConfigurationDto configuration, String colourName, int quantity) {
        Long width = configuration.leaf().dimensionOptions().stream()
                .filter(o -> o.dimensionType().name().equals("ДЛИНА") && o.value().intValue() == 700).findFirst().orElseThrow().id();
        Long height = configuration.leaf().dimensionOptions().stream()
                .filter(o -> o.dimensionType().name().equals("ВЫСОТА") && o.value().intValue() == 2000).findFirst().orElseThrow().id();
        Long thickness = configuration.leaf().dimensionOptions().stream()
                .filter(o -> o.dimensionType().name().equals("ТОЛЩИНА") && o.value().intValue() == 44).findFirst().orElseThrow().id();
        ColourOptionDto colour = configuration.leaf().colourOptions().stream()
                .filter(o -> o.colourType().name().equals(colourName)).findFirst()
                .orElseThrow(() -> new AssertionError("Нет цвета " + colourName + " среди "
                        + configuration.leaf().colourOptions().stream().map(o -> o.colourType().name()).toList()));
        SpecificationExportRequestDto specification = new SpecificationExportRequestDto(
                configuration.leaf().type().id(),
                new ComponentSelectionDto(width, height, thickness, colour.id(), null, null, null, null, null, null, null),
                null, null, false, null, null, null, null, null, null, BigDecimal.valueOf(2000), null, null);
        return new OrderLineExportRequestDto(configuration.leaf().type().name(), quantity, specification, List.of());
    }
}
