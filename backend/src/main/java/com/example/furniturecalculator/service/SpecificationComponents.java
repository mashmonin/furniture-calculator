package com.example.furniturecalculator.service;

import java.math.BigDecimal;
import java.util.List;

import com.example.furniturecalculator.dto.HardwarePriceDto;

// Результат полного резолва конфигурации для выгрузки спецификации (см. change add-specification-export) —
// leaf всегда присутствует (обязателен), остальные компоненты — null, если не указаны в запросе (см.
// DoorConfigurationPricingService.resolveSpecificationComponents). leafHeightValue — отдельным полем, а не
// через leaf.heightOption(): у leaf-компонента высота никогда не сопоставляется с каталожной опцией
// напрямую (см. комментарий в addComponentIfPresent), поэтому ResolvedComponent.heightOption для leaf
// всегда null — значение высоты полотна нужно передать отдельно, тем же способом, что уже используется
// для наценки за размер и диапазонных проверок короба/наличника/добора.
record SpecificationComponents(
        ResolvedComponent leaf,
        BigDecimal leafHeightValue,
        ResolvedComponent edge,
        ResolvedComponent frame,
        ResolvedComponent doorCasing,
        ResolvedComponent frameExtensions,
        List<HardwarePriceDto> hardware) {
}
