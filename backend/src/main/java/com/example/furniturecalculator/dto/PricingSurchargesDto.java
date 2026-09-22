package com.example.furniturecalculator.dto;

import java.math.BigDecimal;
import java.util.List;

public record PricingSurchargesDto(
        BigDecimal reverseSurchargePercent,
        List<DimensionSurchargeRuleDto> dimensionSurchargeRules,
        List<MirrorFinishSurchargeDto> mirrorFinishSurcharges,
        List<GlazingSurchargeDto> glazingSurcharges,
        List<ColourSurchargeDto> colourSurcharges,
        List<PogonazhSurchargeRuleDto> pogonazhSurchargeRules,
        // См. change add-leaf-double-sided-painting — фиксированный процент, как и reverseSurchargePercent.
        BigDecimal doubleSidedPaintingSurchargePercent,
        // См. change add-leaf-quarter-attribute — фиксированный процент, как и reverseSurchargePercent;
        // применяется ли он для конкретного расчёта (только когда «Четверть» истинна сама по себе, а не
        // как следствие уже применённой наценки за реверс/толщину 59мм) решает backend при расчёте — этот
        // процент лишь то же значение, что уже применено, для отображения на фронте без пересчёта.
        BigDecimal quarterSurchargePercent) {
}
