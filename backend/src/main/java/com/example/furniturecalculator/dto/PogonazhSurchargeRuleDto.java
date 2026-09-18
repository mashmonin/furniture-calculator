package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

// ownerType — дискриминатор владельца правила (frame/doorCasing/frameExtensions), тот же набор имён
// компонентов, что и ComponentPriceDto.component; ownerId — id соответствующего frame_type/
// door_casing_type/frame_extensions_type (см. change add-pogonazh-length-surcharge).
public record PogonazhSurchargeRuleDto(
        String ownerType,
        Long ownerId,
        BigDecimal value,
        BigDecimal minValueExclusive,
        BigDecimal maxValueInclusive,
        BigDecimal surchargePercent) {
}
