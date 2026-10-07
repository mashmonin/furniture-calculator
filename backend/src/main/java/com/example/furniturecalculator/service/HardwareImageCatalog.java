package com.example.furniturecalculator.service;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

// Каталог фото фурнитуры (см. change add-hardware-option-images, hardware-catalog, «Фото цветового варианта
// фурнитуры»). Файлы лежат в ресурсах: hardware-images/<артикул, где «/» заменён на «_»>.webp. Индекс «имя файла
// без расширения → ресурс» строится один раз при первом обращении и не зависит от БД; имена сравниваются в NFC
// (macOS хранит имена файлов в NFD).
@Component
public class HardwareImageCatalog {

    private static final Logger log = LoggerFactory.getLogger(HardwareImageCatalog.class);
    private static final String ROOT = "hardware-images/";
    private static final String EXTENSION = ".webp";

    private volatile Map<String, Resource> index;

    // Имя файла (с расширением) для артикула, если фото есть.
    public Optional<String> findFileName(String article) {
        if (article == null || article.isBlank()) {
            return Optional.empty();
        }
        String key = key(article.replace('/', '_'));
        return index().containsKey(key) ? Optional.of(key + EXTENSION) : Optional.empty();
    }

    // Ресурс фото по артикулу (для КП); пусто, если фото нет.
    public Optional<Resource> findResourceByArticle(String article) {
        return findFileName(article).flatMap(this::findResource);
    }

    // Ресурс по имени файла из ссылки; имя вне индекса (в том числе с «../») — пусто.
    public Optional<Resource> findResource(String fileName) {
        if (fileName == null || !fileName.endsWith(EXTENSION)) {
            return Optional.empty();
        }
        String stem = fileName.substring(0, fileName.length() - EXTENSION.length());
        return Optional.ofNullable(index().get(key(stem)));
    }

    private Map<String, Resource> index() {
        Map<String, Resource> result = index;
        if (result == null) {
            synchronized (this) {
                if (index == null) {
                    index = buildIndex();
                }
                result = index;
            }
        }
        return result;
    }

    private Map<String, Resource> buildIndex() {
        Map<String, Resource> result = new HashMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:" + ROOT + "*" + EXTENSION);
            for (Resource resource : resources) {
                String name = resource.getFilename();
                if (!resource.isReadable() || name == null || name.startsWith(".")) {
                    continue;
                }
                String decoded = URLDecoder.decode(name.replace("+", "%2B"), StandardCharsets.UTF_8);
                result.putIfAbsent(key(decoded.substring(0, decoded.length() - EXTENSION.length())), resource);
            }
        } catch (IOException e) {
            log.warn("Не удалось прочитать каталог фото фурнитуры: {}", e.getMessage());
        }
        return result;
    }

    private static String key(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }
}
