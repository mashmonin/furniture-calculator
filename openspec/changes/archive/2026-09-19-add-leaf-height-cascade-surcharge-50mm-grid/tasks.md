## 1. Схема: колонка unavailable и блокирующие строки

- [x] 1.1 Создать `backend/src/main/resources/db/changelog/changes/0098-dimension-surcharge-rule-height-cascade.yaml`: changeset `ADD COLUMN unavailable BOOLEAN NOT NULL DEFAULT false` (декларативный `addColumn` с `defaultValueBoolean: false`).
- [x] 1.2 Changeset: `dropNotNullConstraint` на `surcharge_percent` (тот же Liquibase changeType, что уже применялся в проекте, например для `value` в `pogonazh_surcharge_rule`).
- [x] 1.3 Changeset (raw SQL): CHECK-констрейнт `chk_dimension_surcharge_rule_percent_or_unavailable`: `(CASE WHEN surcharge_percent IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN unavailable THEN 1 ELSE 0 END) = 1`.
- [x] 1.4 Changeset (raw SQL): CHECK-констрейнт `chk_dimension_surcharge_rule_unavailable_requires_leaf_type`: `NOT unavailable OR leaf_type_id IS NOT NULL`.
- [x] 1.5 Changeset: `INSERT INTO dimension_surcharge_rule (liner_dimension_type_id, value, unavailable, leaf_type_id) SELECT dt.id, v.value, true, lt.id FROM liner_dimension_type dt CROSS JOIN (VALUES (2800::numeric), (2900::numeric)) AS v(value) CROSS JOIN leaf_type lt WHERE dt.code = 'DT-002' AND lt.code = 'LT-041'` (2 блокирующие строки — «СИБИРЬ 03» на 2800/2900мм).
- [x] 1.6 Подключить `changes/0098-dimension-surcharge-rule-height-cascade.yaml` в `db.changelog-master.yaml`. Чистый DDL/CHECK/INSERT без generated-колонок и partial-индексов — по опыту 0096 не должно требовать desktop-override, но проверить точечным прогоном `DesktopProfileTest` (задача 5.1) прежде чем считать это окончательным (по опыту 0097 такое предположение уже однажды не подтвердилось).

## 2. Backend: домен и резолвер

- [x] 2.1 `DimensionSurchargeRule`: сделать `surchargePercent` nullable (убрать `nullable = false`), добавить `@Column(nullable = false) private boolean unavailable;`.
- [x] 2.2 `DoorConfigurationPricingService`: добавить константы `HEIGHT_GRID_FLOOR = BigDecimal.valueOf(1900)`, `HEIGHT_GRID_STEP = BigDecimal.valueOf(50)`, `CASCADE_STEP_PERCENT = BigDecimal.valueOf(20)`, `MAX_CASCADE_STEPS = 200`.
- [x] 2.3 `DoorConfigurationPricingService`: новый приватный метод `Optional<BigDecimal> resolveHeightCascadeSurchargePercent(LeafType leafType, LinerDimensionType dimensionType, BigDecimal value)` — если `value` ниже `HEIGHT_GRID_FLOOR` или не кратно `HEIGHT_GRID_STEP` от неё, возвращает `Optional.empty()`; иначе итеративно спускается с шагом 50 (не больше `MAX_CASCADE_STEPS` итераций), на каждом шаге ищет точную строку (leaf-специфичную, затем общую, как и в существующем точном резолве) — если строка `unavailable` — возвращает `Optional.empty()` (блокировка); если обычная — возвращает `surcharge_percent + 20 × число_пройденных_шагов`; если сетка кончилась раньше `HEIGHT_GRID_FLOOR` без находки — `Optional.empty()`.
- [x] 2.4 `DoorConfigurationPricingService.resolveAxisSurchargeMultiplier(...)`: если точная строка не найдена — если найденная точная строка `unavailable`, сразу `ResponseStatusException(BAD_REQUEST, ...)` (без каскада); если строки нет вовсе и `dimensionTypeCode` равен `HEIGHT_TYPE_CODE` — попробовать `resolveHeightCascadeSurchargePercent(...)`, при успехе применить как наценку; иначе (как и сейчас) — `ResponseStatusException(BAD_REQUEST, "фабрика не производит полотно с размером ...")`.

## 3. Backend: эндпоинт

- [x] 3.1 `PricingSurchargesService`: при построении списка `dimensionSurchargeRules` отфильтровать строки с `unavailable = true` (`.filter(rule -> !rule.isUnavailable())` перед `.map(...)`) — блокирующие строки не относятся к «правилам наценки» для отображения клиенту.

## 4. Frontend

- [x] 4.1 `frontend/src/App.tsx`: добавить константы `HEIGHT_GRID_FLOOR = 1900`, `HEIGHT_GRID_STEP = 50`, `CASCADE_STEP_PERCENT = 20`.
- [x] 4.2 `frontend/src/App.tsx`: новая функция `findHeightCascadeSurchargePercent(pricingSurcharges, leafTypeId, value): number | undefined` — та же логика спуска, что и в 2.3 (без учёта блокирующих строк — см. design.md, «Фронтенд дублирует ровно ту же каскадную логику, без блокирующих строк»); используется только для `HEIGHT_TYPE_CODE`.
- [x] 4.3 `computeSurchargeBreakdown(...)`: если `findDimensionSurchargeRule` для высоты не находит точного правила — пробовать `findHeightCascadeSurchargePercent(...)` и использовать его результат, если он есть.

## 5. Проверка

- [x] 5.1 Точечно прогнать `DesktopProfileTest` (`--tests`), чтобы убедиться, что миграция 0098 применяется и на H2 (desktop-профиль); если нет — по образцу 0097 добавить `changes-desktop-overrides/` и context-разбивку в master changelog. Прогнан — BUILD SUCCESSFUL, на этот раз desktop-override не потребовался (чистый DDL/CHECK/INSERT без generated-колонок). По пути исправлена ошибка компиляции — `probe` в цикле не effectively final для лямбды, введена `probeValue`.
- [x] 5.2 Точечно прогнать `DoorConfigurationPricingServiceTest` (`--tests`) — новая логика каскада не ломает существующие тесты резолва наценки за размер полотна. Прогнан — BUILD SUCCESSFUL, без изменений в тестах потребовалось.
- [x] 5.3 Вручную проверить через `GET /api/pricing-surcharges` и расчёт стоимости полотна: высота 2250/2350/2450/2550/2650/2750мм для обычной модели — принимается с ожидаемой каскадной наценкой (40/40/50/70/70/70% соответственно); высота 2750мм для «СИБИРЬ 03» — принимается, 70%; высота 2800/2850/2900мм для «СИБИРЬ 03» — отклоняется 400; высота 1850мм — отклоняется; высота 2013мм (не на сетке) — отклоняется; уже существующие точные значения (например, 2400мм, 30%) продолжают работать без изменений. Проверено пользователем вручную в backend, запущенном из IDE — всё корректно.
