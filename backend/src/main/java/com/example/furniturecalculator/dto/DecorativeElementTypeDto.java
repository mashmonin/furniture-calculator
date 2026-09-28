package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record DecorativeElementTypeDto(ReferenceDto type, BigDecimal lengthMm, BigDecimal retailPrice, BigDecimal dealerPrice) {
}
