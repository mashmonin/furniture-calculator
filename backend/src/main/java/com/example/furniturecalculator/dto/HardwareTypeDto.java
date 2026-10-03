package com.example.furniturecalculator.dto;

import java.util.List;

// brand — бренд позиции (null, если не задан).
public record HardwareTypeDto(
        ReferenceDto type, String unit, String brand, PriceListDto priceList, List<HardwareOptionDto> options) {
}
