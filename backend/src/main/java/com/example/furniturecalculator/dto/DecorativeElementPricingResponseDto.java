package com.example.furniturecalculator.dto;

import java.math.BigDecimal;
import java.util.List;

public record DecorativeElementPricingResponseDto(
        BigDecimal totalRetailPrice,
        BigDecimal totalDealerPrice,
        List<DecorativeElementPriceDto> decorativeElements) {
}
