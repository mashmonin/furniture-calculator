package com.example.furniturecalculator.dto;

import java.util.List;

// Одна позиция заказа при выгрузке всего заказа целиком (см. change add-order-cart-screen,
// order-export-api) — то же тело, что принимает POST /api/specification/export (specification), плюс
// количество и отображаемое наименование позиции, используемое в сводке и в названии её листа детализации.
// attributeTags (см. change refine-order-export-layout) — теги атрибутов конфигурации (реверс, четверть и
// т.д.), уже вычисленные на фронтенде (CartItem.attributeTags) и переданные как есть, без пересчёта или
// валидации по каталогу — тем же принципом, что и displayName; опционально, null трактуется как пустой
// список (см. OrderExportService).
public record OrderLineExportRequestDto(
        String displayName,
        Integer quantity,
        SpecificationExportRequestDto specification,
        List<String> attributeTags) {
}
