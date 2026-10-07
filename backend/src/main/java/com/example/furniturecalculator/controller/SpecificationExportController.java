package com.example.furniturecalculator.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.furniturecalculator.dto.OrderLineExportRequestDto;
import com.example.furniturecalculator.dto.SpecificationExportRequestDto;
import com.example.furniturecalculator.service.CommercialOfferService;
import com.example.furniturecalculator.service.OrderExportService;
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
    private final OrderExportService orderExportService;
    private final CommercialOfferService commercialOfferService;

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody SpecificationExportRequestDto request) {
        byte[] file = specificationExportService.export(request);
        return xlsxResponse(file);
    }

    // Выгрузка всего заказа (корзины) одним файлом — сводный лист по всем позициям и лист детализации на
    // каждую (см. change add-order-cart-screen, order-export-api). Корзина не хранится на backend — список
    // позиций целиком приходит в теле запроса при каждой выгрузке.
    @PostMapping("/export-order")
    public ResponseEntity<byte[]> exportOrder(@RequestBody List<OrderLineExportRequestDto> lines) {
        byte[] file = orderExportService.export(lines);
        return xlsxResponse(file);
    }

    // Коммерческое предложение (PDF) по тем же позициям заказа — см. change add-commercial-offer-pdf-export,
    // commercial-offer-export.
    @PostMapping("/export-offer")
    public ResponseEntity<byte[]> exportOffer(@RequestBody List<OrderLineExportRequestDto> lines) {
        CommercialOfferService.Offer offer = commercialOfferService.export(lines);
        byte[] file = offer.content();
        String filename = "КП-" + offer.number() + ".pdf";
        ContentDisposition contentDisposition = ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(file);
    }

    private ResponseEntity<byte[]> xlsxResponse(byte[] file) {
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
