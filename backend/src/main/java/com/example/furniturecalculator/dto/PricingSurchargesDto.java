package com.example.furniturecalculator.dto;

import java.math.BigDecimal;
import java.util.List;

public record PricingSurchargesDto(
        BigDecimal reverseSurchargePercent,
        List<DimensionSurchargeRuleDto> dimensionSurchargeRules,
        List<MirrorFinishSurchargeDto> mirrorFinishSurcharges,
        List<GlazingSurchargeDto> glazingSurcharges,
        List<ColourSurchargeDto> colourSurcharges,
        List<PogonazhSurchargeRuleDto> pogonazhSurchargeRules) {
}
