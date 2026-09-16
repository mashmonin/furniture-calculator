package com.example.furniturecalculator.service;

import java.math.BigDecimal;

// Одна применённая к цене полотна надбавка — те же данные и формулировки, что и в разбивке «Надбавки к
// цене полотна» на фронте (см. App.tsx, computeSurchargeBreakdown), но посчитанные один раз здесь же, где
// уже резолвятся сами множители надбавок (см. addComponentIfPresent), — чтобы выгрузка спецификации (см.
// change add-specification-export) не пересчитывала их отдельным путём и не дублировала эту логику.
record LeafPriceSurcharge(String label, BigDecimal percent) {
}
