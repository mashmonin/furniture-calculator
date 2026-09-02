package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record ComponentSelectionDto(
        Long lengthOptionId,
        Long heightOptionId,
        Long thicknessOptionId,
        Long colourOptionId,
        BigDecimal customLengthValueMm,
        BigDecimal customHeightValueMm) {

    public static final ComponentSelectionDto EMPTY = new ComponentSelectionDto(null, null, null, null, null, null);
}
