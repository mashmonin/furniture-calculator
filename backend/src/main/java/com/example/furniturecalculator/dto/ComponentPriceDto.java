package com.example.furniturecalculator.dto;

import java.math.BigDecimal;

public record ComponentPriceDto(
        String component,
        boolean priced,
        BigDecimal retailPrice,
        BigDecimal dealerPrice,
        // Цена компонента до применения наценок за нестандартный размер полотна и надбавки за реверс
        // (см. change redesign-door-configurator-flow). Null, если priced = false. Для всех компонентов,
        // кроме leaf, наценки никогда не применяются — базовая цена совпадает с retailPrice/dealerPrice.
        BigDecimal baseRetailPrice,
        BigDecimal baseDealerPrice) {
}
