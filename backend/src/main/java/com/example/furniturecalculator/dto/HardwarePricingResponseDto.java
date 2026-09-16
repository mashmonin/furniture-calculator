package com.example.furniturecalculator.dto;

import java.math.BigDecimal;
import java.util.List;

public record HardwarePricingResponseDto(
        BigDecimal totalRetailPrice,
        BigDecimal totalDealerPrice,
        List<HardwarePriceDto> hardware) {
}
