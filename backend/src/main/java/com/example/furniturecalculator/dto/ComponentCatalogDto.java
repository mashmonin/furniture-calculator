package com.example.furniturecalculator.dto;

import java.util.List;

public record ComponentCatalogDto(
        ReferenceDto type,
        ReferenceDto collection,
        List<LinerDimensionOptionDto> dimensionOptions,
        List<ColourOptionDto> colourOptions) {
}
