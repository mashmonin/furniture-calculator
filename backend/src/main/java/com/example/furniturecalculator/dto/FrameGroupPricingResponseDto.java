package com.example.furniturecalculator.dto;

import java.math.BigDecimal;
import java.util.List;

public record FrameGroupPricingResponseDto(
        BigDecimal totalRetailPrice,
        BigDecimal totalDealerPrice,
        List<ComponentPriceDto> components) {
}
