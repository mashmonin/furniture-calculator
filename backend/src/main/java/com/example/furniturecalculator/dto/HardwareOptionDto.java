package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

// article — артикул цветового варианта (null, если не задан).
public record HardwareOptionDto(Long id, String colourName, BigDecimal retailPrice, BigDecimal dealerPrice, String article) {
}
