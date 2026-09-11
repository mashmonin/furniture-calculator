package com.example.furniturecalculator.dto;

import java.util.List;

public record HardwareTypeDto(ReferenceDto type, String unit, List<HardwareOptionDto> options) {
}
