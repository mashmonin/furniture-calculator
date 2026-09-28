package com.example.furniturecalculator.dto;

import java.math.BigDecimal;
import java.util.List;

// Объединяет поля трёх этапных запросов расчёта стоимости (см. change add-staged-pricing-endpoints,
// frontend-staged-pricing) в один запрос выгрузки спецификации (см. change add-specification-export) —
// leafTypeId/edgeTypeId/frameTypeId/doorCasingTypeId/frameExtensionsTypeId все являются полями тела,
// а не path-параметром, поскольку у объединённого запроса нет единого владельца пути.
public record SpecificationExportRequestDto(
        Long leafTypeId,
        ComponentSelectionDto leaf,
        Long edgeTypeId,
        ComponentSelectionDto edge,
        Boolean isReverse,
        Long frameTypeId,
        ComponentSelectionDto frame,
        Long doorCasingTypeId,
        ComponentSelectionDto doorCasing,
        Long frameExtensionsTypeId,
        ComponentSelectionDto frameExtensions,
        BigDecimal leafHeightValue,
        List<HardwareSelectionDto> hardware,
        List<DecorativeElementSelectionDto> decorativeElements) {

    // Обратная совместимость с вызывающим кодом/тестами, написанными до появления decorativeElements (см.
    // change add-decorative-elements-plinth) — тот же принцип, что и у укороченных конструкторов
    // PricingRequestDto.
    public SpecificationExportRequestDto(
            Long leafTypeId, ComponentSelectionDto leaf, Long edgeTypeId, ComponentSelectionDto edge, Boolean isReverse,
            Long frameTypeId, ComponentSelectionDto frame, Long doorCasingTypeId, ComponentSelectionDto doorCasing,
            Long frameExtensionsTypeId, ComponentSelectionDto frameExtensions, BigDecimal leafHeightValue,
            List<HardwareSelectionDto> hardware) {
        this(leafTypeId, leaf, edgeTypeId, edge, isReverse, frameTypeId, frame, doorCasingTypeId, doorCasing,
                frameExtensionsTypeId, frameExtensions, leafHeightValue, hardware, null);
    }
}
