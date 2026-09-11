package com.example.furniturecalculator.dto;

import java.util.List;

public record PricingRequestDto(
        ComponentSelectionDto leaf,
        ComponentSelectionDto frame,
        ComponentSelectionDto edge,
        ComponentSelectionDto doorCasing,
        ComponentSelectionDto frameExtensions,
        // Список позиций фурнитуры, независимый от door_configuration (см. change add-hardware-catalog).
        // Null-безопасно трактуется как пустой список.
        List<HardwareSelectionDto> hardware,
        // Явный признак реверса для расчёта стоимости отдельного полотна (см. change
        // add-standalone-leaf-pricing) — у такого запроса нет door_configuration, откуда обычно
        // берётся is_reverse, поэтому клиент передаёт его сам. Эндпоинтом расчёта по door_configuration
        // (calculate()) не читается — там реверс определяется исключительно самой конфигурацией.
        // Null-безопасно трактуется как false.
        Boolean isReverse) {

    public PricingRequestDto(
            ComponentSelectionDto leaf, ComponentSelectionDto frame, ComponentSelectionDto edge,
            ComponentSelectionDto doorCasing, ComponentSelectionDto frameExtensions) {
        this(leaf, frame, edge, doorCasing, frameExtensions, null, null);
    }

    public PricingRequestDto(
            ComponentSelectionDto leaf, ComponentSelectionDto frame, ComponentSelectionDto edge,
            ComponentSelectionDto doorCasing, ComponentSelectionDto frameExtensions, List<HardwareSelectionDto> hardware) {
        this(leaf, frame, edge, doorCasing, frameExtensions, hardware, null);
    }
}
