package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record DecorativeElementPriceDto(
        ReferenceDto category,
        ReferenceDto type,
        BigDecimal lengthMm,
        BigDecimal widthMm,
        BigDecimal thicknessMm,
        int quantity,
        // Стоимость позиции = цена типа × quantity (без каких-либо надбавок, см. design.md изменения
        // add-decorative-elements-plinth — тот же принцип, что и у HardwarePriceDto).
        BigDecimal retailPrice,
        BigDecimal dealerPrice) {
}
