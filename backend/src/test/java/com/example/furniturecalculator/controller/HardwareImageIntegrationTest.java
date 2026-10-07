package com.example.furniturecalculator.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import com.example.furniturecalculator.dto.HardwareCategoryDto;
import com.example.furniturecalculator.dto.HardwareOptionDto;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Фото цветовых вариантов фурнитуры (change add-hardware-option-images, hardware-catalog).
@SpringBootTest
@AutoConfigureMockMvc
class HardwareImageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private Map<String, HardwareOptionDto> optionsByArticle() throws Exception {
        String body = mockMvc.perform(get("/api/hardware-catalog")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<HardwareCategoryDto> catalog = objectMapper.readValue(body, new TypeReference<List<HardwareCategoryDto>>() {
        });
        return catalog.stream()
                .flatMap(c -> c.types().stream())
                .flatMap(t -> t.options().stream())
                .filter(o -> o.article() != null)
                .collect(Collectors.toMap(HardwareOptionDto::article, o -> o, (first, second) -> first));
    }

    @Test
    void вариант_с_фото_получает_ссылку_и_файл_отдаётся() throws Exception {
        HardwareOptionDto option = optionsByArticle().get("SC-00269698");

        assertThat(option.imageUrl()).isEqualTo("/api/hardware-images/SC-00269698.webp");
        mockMvc.perform(get(option.imageUrl()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/webp"))
                .andExpect(header().exists("Cache-Control"));
    }

    @Test
    void артикул_со_слэшем_и_пробелами_находит_фото() throws Exception {
        HardwareOptionDto option = optionsByArticle().get("FM 100-50 MSN/CP");

        assertThat(option.imageUrl()).isEqualTo("/api/hardware-images/FM%20100-50%20MSN_CP.webp");
        mockMvc.perform(get(URI.create(option.imageUrl()))).andExpect(status().isOk());
    }

    @Test
    void вариант_без_фото_получает_пустую_ссылку() throws Exception {
        // ручка Vantage без фото в папке; петли и пр. фото не имеют
        assertThat(optionsByArticle().get("F20D AL").imageUrl()).isNull();
    }

    @Test
    void вариант_без_артикула_не_ломает_каталог() throws Exception {
        String body = mockMvc.perform(get("/api/hardware-catalog")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<HardwareCategoryDto> catalog = objectMapper.readValue(body, new TypeReference<List<HardwareCategoryDto>>() {
        });
        List<HardwareOptionDto> withoutArticle = catalog.stream()
                .flatMap(c -> c.types().stream()).flatMap(t -> t.options().stream())
                .filter(o -> o.article() == null).toList();
        assertThat(withoutArticle).isNotEmpty().allSatisfy(o -> assertThat(o.imageUrl()).isNull());
    }

    @Test
    void несуществующий_файл_даёт_404() throws Exception {
        mockMvc.perform(get("/api/hardware-images/net-takogo.webp")).andExpect(status().isNotFound());
    }

    @Test
    void попытка_выйти_за_каталог_не_отдаёт_файл() throws Exception {
        int code = mockMvc.perform(get("/api/hardware-images/..%2Fapplication.properties")).andReturn().getResponse().getStatus();
        assertThat(HttpStatus.valueOf(code).is4xxClientError()).isTrue();
        mockMvc.perform(get("/api/hardware-images/..%2F..%2Fdb%2Fchangelog%2Fdb.changelog-master.yaml.webp"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void фото_есть_у_всех_вариантов_прайс_листа_фурнитура_кроме_пятнадцати_без_файла() throws Exception {
        String body = mockMvc.perform(get("/api/hardware-catalog")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<HardwareCategoryDto> catalog = objectMapper.readValue(body, new TypeReference<List<HardwareCategoryDto>>() {
        });
        List<String> withoutPhoto = catalog.stream()
                .flatMap(c -> c.types().stream())
                .filter(t -> "PL-003".equals(t.priceList().code()))
                .flatMap(t -> t.options().stream())
                .filter(o -> o.imageUrl() == null)
                .map(HardwareOptionDto::article).toList();

        // «новинка(…)»-артикулы получают фото по артикулу без суффикса, у этих файла нет в исходной папке
        assertThat(withoutPhoto).hasSize(15).contains("F20D AL", "ZL60(30x30) CP", "VE001 SB", "Квадрат 6х6х100");
    }

    @Test
    void каждый_файл_фото_относится_к_артикулу_каталога() throws Exception {
        Set<String> expectedStems = optionsByArticle().keySet().stream()
                .map(article -> article.replace('/', '_')).collect(Collectors.toSet());
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:hardware-images/*.webp");

        assertThat(files).hasSize(1037);
        for (Resource file : files) {
            String name = java.text.Normalizer.normalize(file.getFilename(), java.text.Normalizer.Form.NFC);
            assertThat(expectedStems).as("файл без артикула: %s", name).contains(name.substring(0, name.length() - ".webp".length()));
        }
    }
}
