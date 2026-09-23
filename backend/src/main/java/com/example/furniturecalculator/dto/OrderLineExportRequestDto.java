package com.example.furniturecalculator.dto;

// Одна позиция заказа при выгрузке всего заказа целиком (см. change add-order-cart-screen,
// order-export-api) — то же тело, что принимает POST /api/specification/export (specification), плюс
// количество и отображаемое наименование позиции, используемое в сводке и в названии её листа детализации.
public record OrderLineExportRequestDto(
        String displayName,
        Integer quantity,
        SpecificationExportRequestDto specification) {
}
