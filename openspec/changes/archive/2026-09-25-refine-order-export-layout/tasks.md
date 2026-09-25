## 1. Передача тегов с фронтенда

- [x] 1.1 `frontend/src/api/types.ts`: новое поле `attributeTags: string[]` в `OrderLineExportRequestDto`.
- [x] 1.2 `backend/src/main/java/com/example/furniturecalculator/dto/OrderLineExportRequestDto.java`: новое поле `List<String> attributeTags` (`record`, зеркально фронтенду).
- [x] 1.3 `frontend/src/CartScreen.tsx`, `handleExportOrder()`: передавать `attributeTags: item.attributeTags ?? []` в каждой строке запроса `exportOrder(...)`.

## 2. Теги в ячейке наименования и снятие жирного начертания

- [x] 2.1 `backend/src/main/java/com/example/furniturecalculator/service/OrderExportService.java`, `writeConfigurationRow(...)`: текст ячейки «Конфигурация» — `line.displayName()`, и если `line.attributeTags()` не пуст (и не `null` — на случай отсутствия поля в запросе), дополнить через `" — " + String.join(", ", line.attributeTags())`. Реализовано новым приватным методом `configurationCellText(line)`.
- [x] 2.2 Там же: ячейка «Конфигурация» (`row.createCell(1)`) больше не получает `mainRowStyle` — оставить со стилем книги по умолчанию (без `setCellStyle`). Ячейка «№» (`row.createCell(0)`) стиль не менять — остаётся жирной.

## 3. Итоговая строка заказа — три ячейки

- [x] 3.1 `OrderExportService.writeOrderTotalRow(...)`: переписать на три подряд идущие ячейки, начиная с колонки A (индексы 0, 1, 2) — «Итого по заказу» (жирным), дилерская сумма по заказу (без жирного), клиентская сумма по заказу (без жирного). Убрать промежуточную ячейку-лейбл «Итого дилер, ₽».
- [x] 3.2 Обновить вызов `writeOrderTotalRow` в `export(...)` — порядок параметров изменён (дилерская сумма первая).

## 4. Тесты

- [x] 4.1 `backend/src/test/java/.../OrderExportServiceTest.java`: обновлён тест итоговой строки под новую раскладку (3 ячейки: индексы 0/1/2, дилер перед клиентом, только label жирный, `getCell(3)` — `null`); добавлен `lineWithTags(...)` и два новых теста — с тегами (`"Вертикаль 01 — РЕВЕРС, ЧЕТВЕРТЬ"`) и без (`"Вертикаль 01"`); добавлена проверка `isBold(...)` — «№» жирный, «Конфигурация» нет.
- [x] 4.2 `./gradlew test --tests "com.example.furniturecalculator.service.OrderExportServiceTest"` — 7/7 проходят.

## 5. Проверка и финализация

- [x] 5.1 `npm run lint` и `npm run build` (включая `tsc -b`) — чисто.
- [ ] 5.2 Ручная/визуальная проверка не выполнена — в этой среде нет headless-браузера/playwright (та же ограниченность, что и в предыдущих changes этой сессии). Вместо неё — `.xlsx` собран и проверен через реальный вызов `OrderExportService.export(...)` в unit-тестах (Apache POI): текст ячейки «Конфигурация» с тегами и без, отсутствие жирного у наименования при наличии жирного у «№», три ячейки итоговой строки (0/1/2) и отсутствие четвёртой (`getCell(3) == null`). `SpecificationExportServiceTest` (17/17) не затронут.
- [x] 5.3 `openspec validate "refine-order-export-layout" --strict` — проходит.
