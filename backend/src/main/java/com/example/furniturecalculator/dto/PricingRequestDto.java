package com.example.furniturecalculator.dto;

public record PricingRequestDto(
        ComponentSelectionDto leaf,
        ComponentSelectionDto frame,
        ComponentSelectionDto edge,
        ComponentSelectionDto doorCasing,
        ComponentSelectionDto frameExtensions) {
}
