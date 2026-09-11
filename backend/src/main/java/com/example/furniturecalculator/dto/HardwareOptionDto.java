package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record HardwareOptionDto(Long id, String colourName, BigDecimal retailPrice, BigDecimal dealerPrice) {
}
