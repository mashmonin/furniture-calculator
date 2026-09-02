package com.example.furniturecalculator.dto;

import java.util.List;

public record ComponentCatalogDto(
        ReferenceDto type,
        ReferenceDto collection,
        List<LinerDimensionOptionDto> dimensionOptions,
        List<ColourOptionDto> colourOptions,
        List<FramePostDto> posts,
        // Допустимые исполнения зеркала (mirror_finish_option) — заполняется только для leaf-компонента
        // (см. change add-mirror-finish-leaf-option); для остальных компонентов всегда пустой список.
        List<ReferenceDto> mirrorFinishOptions) {
}
