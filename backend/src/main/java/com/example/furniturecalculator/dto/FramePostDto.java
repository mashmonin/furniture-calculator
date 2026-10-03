package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

// heightOptionId — опция высоты короба, к которой привязана позиция (null — позиция не зависит от высоты).
public record FramePostDto(
        Long id, ReferenceDto postType, Integer quantity, BigDecimal length, BigDecimal retailPrice, BigDecimal dealerPrice,
        Long heightOptionId) {
}
