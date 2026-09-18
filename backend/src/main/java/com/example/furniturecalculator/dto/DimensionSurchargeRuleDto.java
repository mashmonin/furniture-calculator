package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record DimensionSurchargeRuleDto(
        ReferenceDto dimensionType, BigDecimal value, Long leafTypeId, BigDecimal surchargePercent) {
}
