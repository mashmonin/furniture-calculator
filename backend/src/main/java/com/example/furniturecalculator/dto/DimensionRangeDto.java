package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record DimensionRangeDto(ReferenceDto dimensionType, BigDecimal minValue, BigDecimal maxValue) {
}
