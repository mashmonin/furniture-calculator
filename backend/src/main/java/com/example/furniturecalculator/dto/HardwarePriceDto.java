package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record HardwarePriceDto(
        ReferenceDto category,
        ReferenceDto type,
        String colourName,
        int quantity,
        // Стоимость позиции = цена цветового варианта × quantity (без каких-либо надбавок,
        // см. design.md изменения add-hardware-catalog).
        BigDecimal retailPrice,
        BigDecimal dealerPrice) {
}
