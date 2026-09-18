## 1. Схема: owner-скоуп в dimension_surcharge_rule

- [x] 1.1 Создать `backend/src/main/resources/db/changelog/changes/0097-dimension-surcharge-rule-leaf-height-2800-2900.yaml`: changeset `ALTER TABLE dimension_surcharge_rule ADD COLUMN leaf_type_id BIGINT NULL` с `foreignKeyName` на `leaf_type(id)` (декларативный `addColumn`, как и остальные nullable FK-колонки в проекте).
- [x] 1.2 Changeset (raw SQL): `ALTER TABLE dimension_surcharge_rule ADD COLUMN leaf_type_key BIGINT GENERATED ALWAYS AS (CASE WHEN leaf_type_id IS NOT NULL THEN leaf_type_id ELSE -id END)`.
- [x] 1.3 Changeset: `DROP INDEX uk_dimension_surcharge_rule_type_value`.
- [x] 1.4 Changeset: `CREATE UNIQUE INDEX uk_dimension_surcharge_rule_type_value ON dimension_surcharge_rule (liner_dimension_type_id, value, leaf_type_key)`.
- [x] 1.5 Changeset: `INSERT INTO dimension_surcharge_rule (liner_dimension_type_id, value, surcharge_percent, leaf_type_id) SELECT dt.id, v.value, 80, lt.id FROM liner_dimension_type dt CROSS JOIN (VALUES (2800::numeric), (2900::numeric)) AS v(value) CROSS JOIN leaf_type lt WHERE dt.code = 'DT-002' AND lt.code <> 'LT-041'` (44 модели × 2 значения = 88 строк; «СИБИРЬ 03» = LT-041 не получает строк).
- [x] 1.6 Подключить `changes/0097-dimension-surcharge-rule-leaf-height-2800-2900.yaml` в `db.changelog-master.yaml`. По факту потребовалась разбивка по context (в отличие от первоначального плана): PostgreSQL требует `GENERATED ALWAYS AS (...) STORED`, а H2 ключевое слово `STORED` не принимает вовсе — воспроизведено на реальной БД (`ERROR: syntax error at or near ";"` без STORED на Postgres; `JdbcSQLSyntaxErrorException` на H2 с STORED). Добавлен `changes-desktop-overrides/0097-...yaml` (без STORED) и context-разбивка `!desktop`/`desktop` в master changelog, по аналогии с 0006/0062/0065/0069/0094.

## 2. Backend: домен, репозиторий, резолвер

- [x] 2.1 `DimensionSurchargeRule`: добавить `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "leaf_type_id") private LeafType leafType;` (nullable, без `optional = false`).
- [x] 2.2 `DimensionSurchargeRuleRepository`: заменить `findByLinerDimensionTypeIdAndValue` на `findByLinerDimensionTypeIdAndValueAndLeafTypeId(Long, BigDecimal, Long)` и `findByLinerDimensionTypeIdAndValueAndLeafTypeIsNull(Long, BigDecimal)`.
- [x] 2.3 `DoorConfigurationPricingService.resolveAxisSurchargeMultiplier(...)`: сначала искать правило через `findByLinerDimensionTypeIdAndValueAndLeafTypeId(dimensionType.getId(), customValue, leafType.getId())`, при пустом результате — через `findByLinerDimensionTypeIdAndValueAndLeafTypeIsNull(dimensionType.getId(), customValue)`; если оба пусты — как и сейчас, `ResponseStatusException(BAD_REQUEST, "фабрика не производит полотно с размером ...")`.

## 3. Backend: DTO и эндпоинт

- [x] 3.1 `DimensionSurchargeRuleDto`: добавить поле `Long leafTypeId` (nullable).
- [x] 3.2 `PricingSurchargesService`: при построении `DimensionSurchargeRuleDto` передавать `rule.getLeafType() != null ? rule.getLeafType().getId() : null`.

## 4. Frontend

- [x] 4.1 `frontend/src/api/types.ts`: `DimensionSurchargeRuleDto` — добавить `leafTypeId: number | null`.
- [x] 4.2 `frontend/src/App.tsx`, `computeSurchargeBreakdown(...)`: добавить параметр `leafTypeId: number | undefined`; при поиске `heightRule` (и, для консистентности, `lengthRule`) сначала искать правило с `rule.leafTypeId === leafTypeId`, при отсутствии — с `rule.leafTypeId === null`. Обновить единственный вызов `computeSurchargeBreakdown(...)` (см. `leafTypeId` — переменная уже вычислена в компоненте), передав туда `leafTypeId`.

## 5. Проверка

- [x] 5.1 Точечно прогнать `DesktopProfileTest` (`--tests`, не полный `gradlew test`), чтобы убедиться, что миграция 0097 (ADD COLUMN/generated-колонка/DROP+CREATE INDEX/INSERT 88 строк) применяется и на H2 (desktop-профиль). Прогнан — BUILD SUCCESSFUL. Потребовалось обновить существующие unit-тесты `DoorConfigurationPricingServiceTest` (10 мест мокали старый `findByLinerDimensionTypeIdAndValue`, переименованный в `findByLinerDimensionTypeIdAndValueAndLeafTypeIsNull`) — точечно прогнан и этот тестовый класс, BUILD SUCCESSFUL.
- [x] 5.2 Вручную проверить через `GET /api/pricing-surcharges` и расчёт стоимости полотна: любая модель, кроме «СИБИРЬ 03» (LT-041), с произвольной высотой 2800 или 2900 мм — принимается, наценка 80%; модель «СИБИРЬ 03» с высотой 2800 или 2900 мм — 400 «фабрика не производит полотно с размером...»; существующие значения (например, 2700 мм, наценка 50%) продолжают работать для всех моделей без изменений. Проверено пользователем в backend, запущенном из IDE — после обновления страницы (правила надбавок грузятся один раз при монтировании) всё показывается корректно.
