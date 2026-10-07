package com.example.furniturecalculator.service;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

// Каталог изображений дверей для КП (см. change add-commercial-offer-pdf-export, commercial-offer-export,
// «Подбор изображения двери»). Файлы лежат в ресурсах: commercial-offer/door-images/<верхняя папка>/<папка
// модели>/<цвет>/<файл>. Индекс строится один раз при первом обращении и не зависит от БД: модель и цвет
// сопоставляются по названиям из каталога.
//
// Ключ модели: у моделей с латинским кодом («QUADRO QU 01» в БД, «КВАДРО QU 01» в папке) — два последних
// токена без ведущих нулей («qu 1»), русский префикс не важен; у моделей, названных коллекцией и номером
// («ВЕРТИКАЛЬ 01» в БД, папка «Вертикаль/1») — «<коллекция>:<номер>», иначе «Вертикаль 1» совпало бы с
// «Атмосфера 1». Названия сравниваются без регистра и в NFC (macOS хранит имена файлов в NFD, и «й» из
// «Матовый» иначе не совпала бы с «й» из БД).
@Component
public class DoorImageCatalog {

    private static final Logger log = LoggerFactory.getLogger(DoorImageCatalog.class);
    private static final String ROOT = "commercial-offer/door-images/";
    private static final Pattern LATIN_CODE = Pattern.compile("[A-Za-z]{2,4}");

    private volatile Map<String, Resource> index;

    public Optional<Resource> find(String collectionName, String leafTypeName, String colourName) {
        if (leafTypeName == null || colourName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(index().get(key(modelKey(collectionName, leafTypeName), colourName)));
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
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:" + ROOT + "**");
            for (Resource resource : resources) {
                if (!resource.isReadable() || resource.getFilename() == null || resource.getFilename().startsWith(".")) {
                    continue;
                }
                String url = URLDecoder.decode(resource.getURL().toString().replace("+", "%2B"), StandardCharsets.UTF_8);
                int rootIndex = url.lastIndexOf(ROOT);
                if (rootIndex < 0) {
                    continue;
                }
                String[] segments = url.substring(rootIndex + ROOT.length()).split("/");
                // верхняя папка / папка модели / цвет / файл
                if (segments.length != 4) {
                    continue;
                }
                String modelKey = folderModelKey(segments[0], segments[1]);
                result.putIfAbsent(key(modelKey, segments[2]), resource);
            }
        } catch (IOException e) {
            log.warn("Не удалось прочитать каталог изображений дверей: {}", e.getMessage());
        }
        return result;
    }

    private static String folderModelKey(String topFolder, String modelFolder) {
        String latinKey = latinModelKey(modelFolder);
        return latinKey != null ? latinKey : normalize(topFolder) + ":" + numberKey(modelFolder);
    }

    private static String modelKey(String collectionName, String leafTypeName) {
        String latinKey = latinModelKey(leafTypeName);
        return latinKey != null ? latinKey : normalize(collectionName == null ? "" : collectionName) + ":" + numberKey(leafTypeName);
    }

    // «КВАДРО QU 01» / «QUADRO QU 01» → «qu 1»; без латинского кода → null.
    private static String latinModelKey(String name) {
        String[] tokens = name.trim().split("\\s+");
        if (tokens.length < 2) {
            return null;
        }
        String code = tokens[tokens.length - 2];
        if (!LATIN_CODE.matcher(code).matches()) {
            return null;
        }
        return normalize(code) + " " + numberKey(tokens[tokens.length - 1]);
    }

    // Последний токен без ведущих нулей: «ВЕРТИКАЛЬ 01» → «1», «1» → «1».
    private static String numberKey(String name) {
        String[] tokens = name.trim().split("\\s+");
        String last = tokens[tokens.length - 1];
        String stripped = last.replaceFirst("^0+(?=.)", "");
        return normalize(stripped);
    }

    private static String key(String modelKey, String colourName) {
        return modelKey + "|" + normalize(colourName);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).trim().toLowerCase(Locale.ROOT);
    }
}
