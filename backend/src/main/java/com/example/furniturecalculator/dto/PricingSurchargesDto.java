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
        BigDecimal doubleSidedPaintingSurchargePercent) {
}
