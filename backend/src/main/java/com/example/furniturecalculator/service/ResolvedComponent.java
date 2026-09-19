package com.example.furniturecalculator.service;

import java.math.BigDecimal;
import java.util.List;

import com.example.furniturecalculator.domain.CatalogType;
import com.example.furniturecalculator.domain.ColourOption;
import com.example.furniturecalculator.domain.FramePost;
import com.example.furniturecalculator.domain.LinerDimensionOption;
import com.example.furniturecalculator.dto.ComponentPriceDto;

// Результат резолва одного компонента внутри DoorConfigurationPricingService.addComponentIfPresent —
// помимо уже посчитанной цены (price, как и раньше единственное, что уходит в calculate()/
// calculateForLeaf()/calculateForFrameGroup()) хранит и использованные для резолва описательные объекты
// (тип, выбранные размеры/цвет, позиции короба, количество), нужные только выгрузке спецификации (см.
// change add-specification-export) — чтобы не резолвить их заново отдельным путём и не дублировать
// бизнес-логику поиска цены. Поля, неприменимые для конкретного компонента (например, размеры у короба), —
// null. quantity — уже применённое к price количество (см. resolveQuantity — применимо только к
// doorCasing/frameExtensions, для остальных компонентов всегда 1). surcharges — применённые к price
// надбавки (см. LeafPriceSurcharge); непустой список возможен только для leaf, для остальных компонентов —
// всегда пустой список (реверс — единственная надбавка, теоретически применимая не только к leaf, но
// addComponentIfPresent вызывается с applyReverseSurcharge=false для всех компонентов, кроме leaf, — см.
// resolveSpecificationComponents/calculate/calculateForLeaf). selectedOptions — наименования выбранных опций
// (исполнение зеркала/вид остекления), также непустой список возможен только для leaf. heightMmOverride —
// значение высоты для компонентов, у которых оно не сопоставляется с каталожной опцией heightOption напрямую
// (короб из HEIGHT_MIRROR_FRAME_TYPE_CODES — высота мирроритcя от leafHeightValue, а не выбирается из
// каталога); для остальных компонентов — null, высота (если применима) берётся из heightOption.
// colourOption — фронтальный (единственный вне двусторонней покраски) цвет; backColourOption — задний цвет
// полотна при включённой двусторонней покраске (см. change add-leaf-double-sided-painting), для остальных
// случаев (не leaf, либо leaf без двусторонней покраски) — всегда null.
record ResolvedComponent(
        CatalogType type,
        ComponentPriceDto price,
        LinerDimensionOption lengthOption,
        LinerDimensionOption heightOption,
        LinerDimensionOption thicknessOption,
        ColourOption colourOption,
        ColourOption backColourOption,
        List<FramePost> framePosts,
        int quantity,
        List<LeafPriceSurcharge> surcharges,
        List<String> selectedOptions,
        BigDecimal heightMmOverride) {
}
