package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

// article — артикул цветового варианта (null, если не задан); imageUrl — ссылка на фото варианта
// (null, если фото нет).
public record HardwareOptionDto(
        Long id, String colourName, BigDecimal retailPrice, BigDecimal dealerPrice, String article, String imageUrl) {
}
