package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

// Расчёт стоимости короба и, опционально, наличника/добора независимо от полотна, кромки и фурнитуры
// (см. change add-staged-pricing-endpoints) — frameTypeId передаётся отдельным path-параметром эндпоинта,
// не полем этого DTO.
public record FrameGroupPricingRequestDto(
        ComponentSelectionDto frame,
        Long doorCasingTypeId,
        ComponentSelectionDto doorCasing,
        Long frameExtensionsTypeId,
        ComponentSelectionDto frameExtensions,
        // Значение высоты полотна, уже известное клиенту с этапа «Полотно» — нужно только для диапазонных
        // проверок короба/наличника/добора, ранее вычислявшихся из опций полотна той же door_configuration.
        BigDecimal leafHeightValue) {
}
