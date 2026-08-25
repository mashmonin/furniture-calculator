package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record FramePostDto(
        Long id, ReferenceDto postType, Integer quantity, BigDecimal length, BigDecimal retailPrice, BigDecimal dealerPrice) {
}
