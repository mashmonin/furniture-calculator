package com.example.furniturecalculator.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.furniturecalculator.dto.SpecificationExportRequestDto;
import com.example.furniturecalculator.service.SpecificationExportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/specification")
@RequiredArgsConstructor
public class SpecificationExportController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final DateTimeFormatter FILENAME_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yy");

    private final SpecificationExportService specificationExportService;

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody SpecificationExportRequestDto request) {
        byte[] file = specificationExportService.export(request);
        String filename = "Заказ-от-" + LocalDate.now().format(FILENAME_DATE_FORMAT) + ".xlsx";
        // Кириллица в имени файла требует явного RFC 5987-кодирования (filename*=UTF-8''...) — без него
        // Tomcat падает с UnmappableCharacterException при попытке записать заголовок в ISO-8859-1
        // (см. filename(String) без charset — не кодирует автоматически в этой версии Spring).
        ContentDisposition contentDisposition = ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(file);
    }
}
