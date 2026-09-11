package com.example.furniturecalculator.dto;

import java.util.List;

public record HardwareCategoryDto(ReferenceDto category, List<HardwareTypeDto> types) {
}
