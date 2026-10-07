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
import com.example.furniturecalculator.dto.HardwareCategoryDto;
import com.example.furniturecalculator.dto.HardwareSelectionDto;
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
        assertThat(text).contains("Номер").contains("Дата и время").contains("Модель").contains("MONO MN 01");
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
        assertThat(compact(extractor.getTextFromPage(1))).contains("Позиция1из2").doesNotContain("Итогопозаказу:");
        // 17 761 × 2 + 17 761 = 53 283 — итог на последней странице.
        assertThat(compact(extractor.getTextFromPage(2))).contains("Позиция2из2").contains("Итогопозаказу:53283₽");
    }

    @Test
    void изображение_двери_выводится_только_если_оно_есть_для_цвета() throws Exception {
        DoorConfigurationDto mono = monoConfiguration();
        // «Белое облако» — есть файл; «Кремовая» для MONO MN 01 — файла нет (страница без изображения).
        int withImage = xobjectCount(postOffer(List.of(line(mono, "Белое облако", 1))).andReturn());
        int withoutImage = xobjectCount(postOffer(List.of(line(mono, "Кремовая", 1))).andReturn());

        assertThat(withImage).isEqualTo(withoutImage + 1);
    }

    // Фурнитура прайс-листа «Фурнитура» (PL-003, change add-hardware-service), добавленная к двери «Эмаль Лайт»,
    // попадает в КП строкой комплектации и в итоговую цену позиции (проверки принадлежности прайс-листу нет).
    @Test
    void фурнитура_из_прайса_фурнитура_попадает_в_кп_и_цену_позиции() throws Exception {
        OrderLineExportRequestDto withHardware =
                withHardware(line(monoConfiguration(), "Белое облако", 1), hardwareOptionId("FM 100-50 MSN/CP"), hardwareOptionId("FM 100-50 MSN/CP"));

        MvcResult result = postOffer(List.of(withHardware)).andExpect(status().isOk()).andReturn();

        String text = compact(new PdfTextExtractor(new PdfReader(result.getResponse().getContentAsByteArray())).getTextFromPage(1));
        // 17 761 + 2 × 2 948 = 23 657.
        assertThat(text).contains("Фурнитура").contains("Fantom").contains("Итоговаяцена:23657₽");
    }

    // Фото фурнитуры справа от двери (change add-hardware-photos-to-commercial-offer): по одному XObject на
    // уникальный артикул с фото; фурнитура без фото и дубли не добавляют изображений.
    @Test
    void фото_фурнитуры_выводится_один_раз_на_артикул_и_только_если_оно_есть() throws Exception {
        OrderLineExportRequestDto base = line(monoConfiguration(), "Белое облако", 1);
        Long withPhoto = hardwareOptionId("FM 100-50 MSN/CP");
        Long otherWithPhoto = hardwareOptionId("FM 100-50 MWH/CP");
        Long withoutPhoto = hardwareOptionId("F20D AL");

        int none = xobjectCount(postOffer(List.of(base)).andReturn());
        int one = xobjectCount(postOffer(List.of(withHardware(base, withPhoto))).andReturn());
        int duplicated = xobjectCount(postOffer(List.of(withHardware(base, withPhoto, withPhoto))).andReturn());
        int two = xobjectCount(postOffer(List.of(withHardware(base, withPhoto, otherWithPhoto))).andReturn());
        int noPhoto = xobjectCount(postOffer(List.of(withHardware(base, withoutPhoto))).andReturn());

        assertThat(one).isEqualTo(none + 1);
        assertThat(duplicated).isEqualTo(one);
        assertThat(two).isEqualTo(none + 2);
        assertThat(noPhoto).isEqualTo(none);
    }

    // Текст заказа и адрес в шапке (change update-cart-panel-and-offer-header, commercial-offer-export).
    private static final String ADDRESS_COMPACT = "Москва,ул.Марксистская,д.38,ТЦ«Кристалл»,2-йэтаж";

    @Test
    void в_шапке_кп_есть_строка_заказа_и_адрес() throws Exception {
        MvcResult result = postOffer(List.of(line(monoConfiguration(), "Белое облако", 1)), "  Квартира на Тверской ")
                .andExpect(status().isOk()).andReturn();

        String text = compact(new PdfTextExtractor(new PdfReader(result.getResponse().getContentAsByteArray())).getTextFromPage(1));
        assertThat(text).contains("ЗаказКвартиранаТверской").contains("Адресмагазина" + ADDRESS_COMPACT);
        // порядок шапки: заказ (самая верхняя строка), номер, дата, адрес магазина, менеджер
        assertThat(text.indexOf("ЗаказКвартира")).isLessThan(text.indexOf("Номер2"));
        assertThat(text.indexOf("Номер2")).isLessThan(text.indexOf("Датаивремя"));
        assertThat(text.indexOf("Датаивремя")).isLessThan(text.indexOf("Адресмагазина"));
        assertThat(text.indexOf("Адресмагазина")).isLessThan(text.indexOf("Менеджер"));
        assertThat(text).contains("МенеджерИвановИванИванович+7(900)111-22-33");
    }

    @Test
    void без_текста_заказа_строки_нет_а_адрес_есть() throws Exception {
        for (String note : new String[] {null, "", "   "}) {
            MvcResult result = postOffer(List.of(line(monoConfiguration(), "Белое облако", 1)), note)
                    .andExpect(status().isOk()).andReturn();
            String text = compact(new PdfTextExtractor(new PdfReader(result.getResponse().getContentAsByteArray())).getTextFromPage(1));
            assertThat(text).doesNotContain("Заказ").contains("Адресмагазина" + ADDRESS_COMPACT);
        }
    }

    @Test
    void длинный_текст_заказа_обрезается_до_80_символов() throws Exception {
        String note = "слово ".repeat(20);
        MvcResult result = postOffer(List.of(line(monoConfiguration(), "Белое облако", 1)), note)
                .andExpect(status().isOk()).andReturn();

        String text = compact(new PdfTextExtractor(new PdfReader(result.getResponse().getContentAsByteArray())).getTextFromPage(1));
        // 80 символов: «слово » × 13 + «сло» — шестое и последующие слова после 13-го не выводятся
        assertThat(text).contains("Заказсловослово").doesNotContain("слово".repeat(14));
    }

    @Test
    void адрес_и_заказ_выводятся_на_каждой_странице() throws Exception {
        DoorConfigurationDto mono = monoConfiguration();
        MvcResult result = postOffer(List.of(line(mono, "Белое облако", 1), line(mono, "Серый вереск", 1)), "Дача")
                .andExpect(status().isOk()).andReturn();

        PdfTextExtractor extractor = new PdfTextExtractor(new PdfReader(result.getResponse().getContentAsByteArray()));
        for (int page = 1; page <= 2; page++) {
            assertThat(compact(extractor.getTextFromPage(page))).contains("ЗаказДача").contains(ADDRESS_COMPACT);
        }
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

    private Long hardwareOptionId(String article) throws Exception {
        String catalog = mockMvc.perform(get("/api/hardware-catalog")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(catalog, new TypeReference<List<HardwareCategoryDto>>() {
        }).stream().flatMap(c -> c.types().stream()).flatMap(t -> t.options().stream())
                .filter(o -> article.equals(o.article())).findFirst().orElseThrow().id();
    }

    // Та же позиция с фурнитурой: по одной штуке на каждый переданный id (id может повторяться).
    private OrderLineExportRequestDto withHardware(OrderLineExportRequestDto base, Long... optionIds) {
        SpecificationExportRequestDto spec = base.specification();
        List<HardwareSelectionDto> hardware = java.util.Arrays.stream(optionIds).map(id -> new HardwareSelectionDto(id, 1)).toList();
        return new OrderLineExportRequestDto(base.displayName(), base.quantity(),
                new SpecificationExportRequestDto(spec.leafTypeId(), spec.leaf(), null, null, false, null, null, null, null, null, null,
                        spec.leafHeightValue(), hardware, null),
                List.of());
    }

    private static String compact(String text) {
        return text.replaceAll("[\\s\u00A0]+", "");
    }

    private org.springframework.test.web.servlet.ResultActions postOffer(List<OrderLineExportRequestDto> lines) throws Exception {
        return postOffer(lines, null);
    }

    private org.springframework.test.web.servlet.ResultActions postOffer(List<OrderLineExportRequestDto> lines, String orderNote) throws Exception {
        var request = post("/api/specification/export-offer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(lines));
        if (orderNote != null) {
            request = request.param("orderNote", orderNote);
        }
        return mockMvc.perform(request);
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
