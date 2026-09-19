package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record ComponentSelectionDto(
        Long lengthOptionId,
        Long heightOptionId,
        Long thicknessOptionId,
        // Для leaf при doubleSidedPainting = true трактуется как цвет фронтальной стороны (см.
        // add-leaf-double-sided-painting); для остальных компонентов и для leaf без двусторонней
        // покраски — единственный цвет, как и раньше.
        Long colourOptionId,
        BigDecimal customLengthValueMm,
        BigDecimal customHeightValueMm,
        Integer quantity,
        // mirror_finish_type.id (не mirror_finish_option.id) — глобальный, тот же id, что и в каталоге
        // и в ответе GET /api/pricing-surcharges (см. change add-mirror-finish-leaf-option).
        Long mirrorFinishTypeId,
        // glazing_type.id (не glazing_option.id) — по тому же принципу, что и mirrorFinishTypeId
        // (см. change add-glazing-price-surcharge).
        Long glazingTypeId,
        // Цвет задней стороны полотна — допустим только для leaf и только при doubleSidedPainting = true
        // (см. change add-leaf-double-sided-painting).
        Long backColourOptionId,
        // Признак двусторонней покраски полотна — допустим только для leaf (см. change
        // add-leaf-double-sided-painting).
        Boolean doubleSidedPainting) {

    public static final ComponentSelectionDto EMPTY =
            new ComponentSelectionDto(null, null, null, null, null, null, null, null, null, null, null);
}
