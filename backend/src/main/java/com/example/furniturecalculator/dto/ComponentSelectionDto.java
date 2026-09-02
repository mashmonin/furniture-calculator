package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record ComponentSelectionDto(
        Long lengthOptionId,
        Long heightOptionId,
        Long thicknessOptionId,
        Long colourOptionId,
        BigDecimal customLengthValueMm,
        BigDecimal customHeightValueMm,
        Integer quantity,
        // mirror_finish_type.id (не mirror_finish_option.id) — глобальный, тот же id, что и в каталоге
        // и в ответе GET /api/pricing-surcharges (см. change add-mirror-finish-leaf-option).
        Long mirrorFinishTypeId) {

    public static final ComponentSelectionDto EMPTY =
            new ComponentSelectionDto(null, null, null, null, null, null, null, null);
}
