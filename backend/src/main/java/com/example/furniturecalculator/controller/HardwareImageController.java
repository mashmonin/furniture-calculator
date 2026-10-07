package com.example.furniturecalculator.controller;

import java.time.Duration;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.furniturecalculator.service.HardwareImageCatalog;

import lombok.RequiredArgsConstructor;

// Отдаёт фото фурнитуры по ссылке из каталога (imageUrl). Имя берётся только из индекса каталога, поэтому выйти
// за каталог фото нельзя; файлы меняются только релизом — кэшируются на сутки.
@RestController
@RequestMapping("/api/hardware-images")
@RequiredArgsConstructor
public class HardwareImageController {

    private static final MediaType WEBP = MediaType.parseMediaType("image/webp");

    private final HardwareImageCatalog hardwareImageCatalog;

    @GetMapping("/{fileName}")
    public ResponseEntity<Resource> getImage(@PathVariable String fileName) {
        return hardwareImageCatalog.findResource(fileName)
                .map(resource -> ResponseEntity.ok()
                        .contentType(WEBP)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                        .body(resource))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
