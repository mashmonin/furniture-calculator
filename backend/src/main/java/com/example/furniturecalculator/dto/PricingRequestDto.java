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
        List<HardwareSelectionDto> hardware) {

    public PricingRequestDto(
            ComponentSelectionDto leaf, ComponentSelectionDto frame, ComponentSelectionDto edge,
            ComponentSelectionDto doorCasing, ComponentSelectionDto frameExtensions) {
        this(leaf, frame, edge, doorCasing, frameExtensions, null);
    }
}
