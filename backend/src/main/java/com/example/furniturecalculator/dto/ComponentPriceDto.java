package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record ComponentPriceDto(String component, boolean priced, BigDecimal retailPrice, BigDecimal dealerPrice) {
}
