## 1. Миграция базы данных

- [x] 1.1 Создать `backend/src/main/resources/db/changelog/changes/0091-glazing-type-surcharge.yaml`: добавить колонку `glazing_type.surcharge_percent` (NUMERIC, NOT NULL) и заполнить её для всех шести существующих строк по имени: «Прозрачное» = 0, «Прозрачное серое» = 5, «Прозрачное бронза» = 5, «Сатинированное» = 5, «Сатинированное серое» = 10, «Сатинированное бронзовое» = 10 (см. design.md, «Новый Liquibase changeset»). Не редактировать `0090-glazing-catalog.yaml`.
- [x] 1.2 Подключить новый changeset в `db.changelog-master.yaml`.

## 2. Backend: домен и репозитории

- [x] 2.1 Добавить поле `surchargePercent` (BigDecimal, `nullable = false`) в `GlazingType`.
- [x] 2.2 Добавить в `GlazingOptionRepository` метод `findByGlazingTypeIdAndLeafTypeId(Long glazingTypeId, Long leafTypeId)` — по образцу `MirrorFinishOptionRepository.findByMirrorFinishTypeIdAndLeafTypeId`.

## 3. Backend: расчёт стоимости

- [x] 3.1 Добавить `glazingTypeId` в `ComponentSelectionDto` (backend).
- [x] 3.2 В `DoorConfigurationPricingService` добавить `resolveGlazingMultiplier(componentName, type, glazingTypeId)` — по образцу `resolveMirrorFinishMultiplier`: `glazingTypeId == null` → `BigDecimal.ONE`; тип не `LeafType` → ошибка 400; иначе поиск через `GlazingOptionRepository.findByGlazingTypeIdAndLeafTypeId`, ошибка 400 при отсутствии, иначе `1 + surchargePercent/100` (для «Прозрачное» с `surchargePercent = 0` естественно даёт `BigDecimal.ONE`).
- [x] 3.3 Вызвать `resolveGlazingMultiplier` в `addComponentIfPresent` и передать результат в `componentPriceFrom`/`applySequentialSurcharges`, встроив шаг между наценкой за исполнение зеркала и надбавкой за реверс (длина → высота → зеркало → остекление → реверс, см. design.md).
- [x] 3.4 Обновить комментарий у `applySequentialSurcharges`/`componentPriceFrom`, описывающий порядок шагов, добавив шаг «остекление».

## 4. Backend: эндпоинт процентов надбавок

- [x] 4.1 Добавить `GlazingSurchargeDto(Long id, String name, BigDecimal surchargePercent)`.
- [x] 4.2 Добавить поле `glazingSurcharges: List<GlazingSurchargeDto>` в `PricingSurchargesDto`.
- [x] 4.3 В `PricingSurchargesService` подтянуть `GlazingTypeRepository.findAll()` (репозиторий уже существует — проверить/использовать) и замаппить в `glazingSurcharges`.

## 5. Frontend: типы и запрос расчёта

- [x] 5.1 Добавить `glazingTypeId?: number` в `ComponentSelectionDto` (`frontend/src/api/types.ts`).
- [x] 5.2 Добавить `GlazingSurchargeDto` и поле `glazingSurcharges: GlazingSurchargeDto[]` в `PricingSurchargesDto` (`frontend/src/api/types.ts`).
- [x] 5.3 В `App.tsx` включить `glazingTypeId` в `leafSelection` перед отправкой запроса расчёта (по образцу строки с `mirrorFinishTypeId` перед `setTimeout`), добавить `glazingTypeId` в зависимости соответствующего `useEffect`.

## 6. Frontend: отображение наценки

- [x] 6.1 В `computeSurchargeBreakdown` добавить параметр `glazingTypeId` и пункт «За вид остекления» (аналогично `mirrorFinishSurcharge`), пропуская его, если найденный `surchargePercent` равен 0 (базовое «Прозрачное»).
- [x] 6.2 Передать `glazingTypeId` в вызов `computeSurchargeBreakdown`.
- [x] 6.3 Обновить комментарии у `resolveGlazingStep` и `handleGlazingTypeChange`, описывающие, что вид остекления «ни на что не влияет» / «не входит в запрос расчёта» — они больше не верны.

## 7. Проверка

- [x] 7.1 Backend: точечные тесты на `DoorConfigurationPricingService` (или `PricingSurchargesService`) для наценки за остекление — ненулевая наценка применяется по правильной формуле, «Прозрачное» наценки не даёт, недопустимый для полотна вид остекления отклоняется 400. Запускать точечно (`./gradlew test --tests "<ИмяКласса>"`), не весь набор тестов.
- [x] 7.2 Вручную через `npm run dev` проверить на остеклённой модели (например, `LT-017`/ЭЛЕГАНТ 01V): выбор вида остекления передаётся в расчёт, наценка отображается в разбивке, «Прозрачное» наценку не показывает, каталог по-прежнему не сужается выбором вида остекления.
