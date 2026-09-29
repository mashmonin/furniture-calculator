package com.example.furniturecalculator.dto;

import java.math.BigDecimal;
import java.util.List;

public record DecorativeElementPricingRequestDto(
        List<DecorativeElementSelectionDto> decorativeElements,
        BigDecimal leafLengthMm) {
}
