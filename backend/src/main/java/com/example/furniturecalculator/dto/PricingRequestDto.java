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
        Boolean isReverse,
        // edge_type.id (не edge_type владения через door_configuration) — по тому же принципу, что и
        // isReverse: читается только расчётом отдельного полотна (см. change add-staged-pricing-endpoints),
        // где кромка передаётся как самостоятельный компонент, а не через согласованную door_configuration.
        // Эндпоинтом расчёта по door_configuration (calculate()) не читается — там кромка определяется
        // исключительно значением configuration.getEdgeType().
        Long edgeTypeId,
        // Список позиций декоративных элементов, независимый от door_configuration (см. change
        // add-decorative-elements-plinth) — тем же принципом, что и hardware. Null-безопасно трактуется как
        // пустой список.
        List<DecorativeElementSelectionDto> decorativeElements) {

    public PricingRequestDto(
            ComponentSelectionDto leaf, ComponentSelectionDto frame, ComponentSelectionDto edge,
            ComponentSelectionDto doorCasing, ComponentSelectionDto frameExtensions) {
        this(leaf, frame, edge, doorCasing, frameExtensions, null, null, null, null);
    }

    public PricingRequestDto(
            ComponentSelectionDto leaf, ComponentSelectionDto frame, ComponentSelectionDto edge,
            ComponentSelectionDto doorCasing, ComponentSelectionDto frameExtensions, List<HardwareSelectionDto> hardware) {
        this(leaf, frame, edge, doorCasing, frameExtensions, hardware, null, null, null);
    }

    // Обратная совместимость с вызывающим кодом/тестами, написанными до появления decorativeElements (см.
    // change add-decorative-elements-plinth) — прежний канонический 8-аргументный конструктор.
    public PricingRequestDto(
            ComponentSelectionDto leaf, ComponentSelectionDto frame, ComponentSelectionDto edge,
            ComponentSelectionDto doorCasing, ComponentSelectionDto frameExtensions, List<HardwareSelectionDto> hardware,
            Boolean isReverse, Long edgeTypeId) {
        this(leaf, frame, edge, doorCasing, frameExtensions, hardware, isReverse, edgeTypeId, null);
    }
}
