package com.example.furniturecalculator.dto;

import java.util.List;

import com.example.furniturecalculator.domain.LeafPanelType;

public record ComponentCatalogDto(
        ReferenceDto type,
        ReferenceDto collection,
        List<LinerDimensionOptionDto> dimensionOptions,
        List<ColourOptionDto> colourOptions,
        List<FramePostDto> posts,
        // Допустимые исполнения зеркала (mirror_finish_option) — заполняется только для leaf-компонента
        // (см. change add-mirror-finish-leaf-option); для остальных компонентов всегда пустой список.
        List<ReferenceDto> mirrorFinishOptions,
        // Тип полотна (глухое/остеклённое/с зеркалом) — заполняется только для leaf-компонента
        // (см. change add-leaf-panel-type); для остальных компонентов всегда null.
        LeafPanelType panelType,
        // Допустимые виды остекления (glazing_option) — заполняется только для leaf-компонента
        // (см. change add-glazing-catalog-for-v-models); для остальных компонентов всегда пустой список.
        List<ReferenceDto> glazingOptions,
        // Диапазоны допустимой нестандартной длины/высоты по коллекции (collection_dimension_range) —
        // заполняется только для leaf-компонента (см. change add-collection-dimension-range); для
        // остальных компонентов всегда пустой список.
        List<DimensionRangeDto> dimensionRanges) {
}
