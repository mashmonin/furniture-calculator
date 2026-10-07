package com.example.furniturecalculator.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Подбор изображений двери для КП (см. change add-commercial-offer-pdf-export) — по реальным файлам из
// ресурсов commercial-offer/door-images и реальным названиям моделей/цветов каталога.
class DoorImageCatalogTest {

    private final DoorImageCatalog catalog = new DoorImageCatalog();

    @Test
    void вертикаль_подбирается_по_номеру_модели_и_цвету() {
        assertThat(catalog.find("Вертикаль", "ВЕРТИКАЛЬ 01", "Матовый Антрацит")).isPresent();
        assertThat(catalog.find("Вертикаль", "ВЕРТИКАЛЬ 07", "Матовый Жемчужный")).isPresent();
    }

    @Test
    void jpeg_и_webp_оба_находятся() {
        // «Светлый Бежевый» у Вертикаль 1 — единственный JPEG, остальные — WebP.
        assertThat(catalog.find("Вертикаль", "ВЕРТИКАЛЬ 01", "Матовый Светлый Бежевый"))
                .hasValueSatisfying(resource -> assertThat(resource.getFilename()).endsWith(".jpg"));
        assertThat(catalog.find("Вертикаль", "ВЕРТИКАЛЬ 01", "Матовый Антрацит"))
                .hasValueSatisfying(resource -> assertThat(resource.getFilename()).endsWith(".webp"));
    }

    @Test
    void эмаль_лайт_подбирается_несмотря_на_разные_написания_моделей() {
        assertThat(catalog.find("MONO", "MONO MN 01", "Белое Облако")).isPresent();
        assertThat(catalog.find("QUADRO", "QUADRO QU 01", "Кремовая")).isPresent();
        assertThat(catalog.find("VENEZIA", "VENEZIA VN 02", "Кремовая")).isPresent();
        assertThat(catalog.find("REFLEX", "REFLEX RF 01", "Серый Вереск")).isPresent();
    }

    @Test
    void регистр_цвета_не_важен() {
        assertThat(catalog.find("MONO", "MONO MN 01", "белое облако")).isPresent();
    }

    @Test
    void нет_файла_для_сочетания_модель_цвет() {
        assertThat(catalog.find("MONO", "MONO MN 01", "Кремовая")).isEmpty();
        assertThat(catalog.find("Вертикаль", "ВЕРТИКАЛЬ 01", "Несуществующий цвет")).isEmpty();
    }

    @Test
    void номер_модели_другой_коллекции_не_совпадает_с_вертикалью() {
        assertThat(catalog.find("Атмосфера", "АТМОСФЕРА 01", "Матовый Антрацит")).isEmpty();
    }

    @Test
    void отсутствие_цвета_или_модели_даёт_пусто() {
        assertThat(catalog.find("Вертикаль", "ВЕРТИКАЛЬ 01", null)).isEmpty();
        assertThat(catalog.find("Вертикаль", null, "Матовый Антрацит")).isEmpty();
    }
}
