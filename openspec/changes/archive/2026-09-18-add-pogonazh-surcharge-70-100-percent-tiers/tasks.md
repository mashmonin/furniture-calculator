## 1. Схема: диапазоны в pogonazh_surcharge_rule

- [x] 1.1 Создать `backend/src/main/resources/db/changelog/changes/0096-pogonazh-surcharge-rule-70-100-percent-tiers.yaml`: changeset `ALTER TABLE pogonazh_surcharge_rule ALTER COLUMN value DROP NOT NULL` + `ADD COLUMN min_value_exclusive DECIMAL NULL` + `ADD COLUMN max_value_inclusive DECIMAL NULL`.
- [x] 1.2 В том же файле — changeset с CHECK-констрейнтом `chk_pogonazh_surcharge_rule_point_or_range`: `(CASE WHEN value IS NOT NULL THEN 1 ELSE 0 END) + (CASE WHEN min_value_exclusive IS NOT NULL OR max_value_inclusive IS NOT NULL THEN 1 ELSE 0 END) = 1`.
- [x] 1.3 Changeset: `UPDATE pogonazh_surcharge_rule SET surcharge_percent = 70 WHERE value = 3000 AND frame_type_id = (SELECT id FROM frame_type WHERE code = 'FT-002')`.
- [x] 1.4 Changeset: `UPDATE pogonazh_surcharge_rule SET surcharge_percent = 70 WHERE value = 3000 AND door_casing_type_id = (SELECT id FROM door_casing_type WHERE code = 'DCT-001')`.
- [x] 1.5 Changeset: `UPDATE pogonazh_surcharge_rule SET surcharge_percent = 70 WHERE (value = 3000 OR value = 2950) AND frame_extensions_type_id IN (SELECT id FROM frame_extensions_type WHERE code IN ('FET-008','FET-009','FET-010','FET-011','FET-012','FET-013'))`.
- [x] 1.6 Changeset: `INSERT INTO pogonazh_surcharge_rule (frame_type_id, min_value_exclusive, max_value_inclusive, surcharge_percent) SELECT id, 2400, 2700, 70 FROM frame_type WHERE code = 'FT-001'`.
- [x] 1.7 Changeset: `INSERT INTO pogonazh_surcharge_rule (frame_type_id, min_value_exclusive, max_value_inclusive, surcharge_percent) SELECT id, 2700, NULL, 100 FROM frame_type WHERE code = 'FT-001'`.
- [x] 1.8 Подключить `changes/0096-pogonazh-surcharge-rule-70-100-percent-tiers.yaml` в `db.changelog-master.yaml` (без разбивки по context — DDL и SQL из 1.1–1.7 не используют partial/functional-индексы, совместимы с H2 напрямую, как и 0095).

## 2. Backend: домен, репозиторий, резолвер

- [x] 2.1 `PogonazhSurchargeRule`: сделать поле `value` nullable (убрать `nullable = false` из `@Column`), добавить поля `minValueExclusive`/`maxValueInclusive` (`@Column(name = "min_value_exclusive")`/`@Column(name = "max_value_inclusive")`, без `nullable = false`).
- [x] 2.2 `PogonazhSurchargeRuleRepository`: добавить `List<PogonazhSurchargeRule> findByFrameTypeId(Long frameTypeId)`.
- [x] 2.3 `DoorConfigurationPricingService.resolvePogonazhSurchargeMultiplier(FrameType, BigDecimal)`: сначала точный поиск по `value` (как сейчас, через существующий метод), при отсутствии — искать среди `findByFrameTypeId(...)` диапазонную строку, где `(minValueExclusive == null || heightValue.compareTo(minValueExclusive) > 0) && (maxValueInclusive == null || heightValue.compareTo(maxValueInclusive) <= 0)`; методы для `DoorCasingType`/`FrameExtensionsType` не менять.

## 3. Backend: DTO и эндпоинт

- [x] 3.1 `PogonazhSurchargeRuleDto`: сделать `value` nullable (`BigDecimal`, без изменений типа — Java `BigDecimal` уже nullable), добавить поля `minValueExclusive`/`maxValueInclusive` (`BigDecimal`, nullable).
- [x] 3.2 `PricingSurchargesService.pogonazhSurchargeRuleDto(...)`: передавать `rule.getMinValueExclusive()`/`rule.getMaxValueInclusive()` в новый DTO (для всех трёх веток owner — frame/doorCasing/frameExtensions).

## 4. Frontend

- [x] 4.1 `frontend/src/api/types.ts`: `PogonazhSurchargeRuleDto` — `value: number | null`, добавить `minValueExclusive: number | null` и `maxValueInclusive: number | null`.
- [x] 4.2 `frontend/src/App.tsx`, `pogonazhSurchargePercent(...)`: если точное совпадение по `rule.value === value` не найдено, искать диапазонное правило того же `ownerType`/`ownerId`, где `(rule.minValueExclusive === null || value > rule.minValueExclusive) && (rule.maxValueInclusive === null || value <= rule.maxValueInclusive)`.

## 5. Проверка

- [x] 5.1 Точечно прогнать `DesktopProfileTest` (`--tests`, не полный `gradlew test`), чтобы убедиться, что миграция 0096 (ALTER/CHECK/UPDATE/INSERT) применяется и на H2 (desktop-профиль). Прогнан — BUILD SUCCESSFUL (компиляция + миграция на H2 прошли).
- [x] 5.2 Вручную проверить через `GET /api/pricing-surcharges` и расчёт стоимости: короб «Компланар» на 3000мм, наличник «Эво» на 3000мм, добор «Компланар» на 2950/3000мм — 70%; короб «Фантом» с высотой полотна 2550мм и 2650мм — 70%; короб «Фантом» с высотой полотна 2750мм и, например, 3200мм (значение без каталожной точки вообще) — 100%; короб «Фантом» на 2400мм — по-прежнему 50%, на 2300мм — по-прежнему 30%. Проверено пользователем вручную в backend, запущенном из IDE — миграция применилась.
