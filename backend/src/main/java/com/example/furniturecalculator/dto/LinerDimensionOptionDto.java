package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record LinerDimensionOptionDto(
        Long id, ReferenceDto dimensionType, BigDecimal value, BigDecimal minValue, BigDecimal maxValue, boolean standard) {
}
