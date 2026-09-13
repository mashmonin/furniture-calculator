package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record MirrorFinishSurchargeDto(Long id, String name, String shortName, BigDecimal surchargePercent) {
}
