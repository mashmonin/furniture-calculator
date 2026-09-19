package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record ColourSurchargeDto(Long id, String name, BigDecimal surchargePercent) {
}
