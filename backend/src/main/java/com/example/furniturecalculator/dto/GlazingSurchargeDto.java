package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record GlazingSurchargeDto(Long id, String name, BigDecimal surchargePercent) {
}
