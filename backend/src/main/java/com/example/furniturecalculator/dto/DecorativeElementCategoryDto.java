package com.example.furniturecalculator.dto;

import java.util.List;

public record DecorativeElementCategoryDto(ReferenceDto category, List<DecorativeElementTypeDto> types) {
}
