package com.example.furniturecalculator.dto;

import java.math.BigDecimal;
import java.util.List;

public record PricingResponseDto(
        BigDecimal totalRetailPrice,
        BigDecimal totalDealerPrice,
        List<ComponentPriceDto> components,
        List<HardwarePriceDto> hardware,
        List<DecorativeElementPriceDto> decorativeElements) {
}
