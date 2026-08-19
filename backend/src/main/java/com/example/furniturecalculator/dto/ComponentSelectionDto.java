package com.example.furniturecalculator.dto;

public record ComponentSelectionDto(Long lengthOptionId, Long heightOptionId, Long thicknessOptionId, Long colourOptionId) {

    public static final ComponentSelectionDto EMPTY = new ComponentSelectionDto(null, null, null, null);
}
