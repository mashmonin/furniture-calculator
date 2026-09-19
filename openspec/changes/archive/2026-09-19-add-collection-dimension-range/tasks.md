## 1. Схема: collection_dimension_range

- [x] 1.1 Создать `backend/src/main/resources/db/changelog/changes/0099-collection-dimension-range.yaml`: changeset `CREATE TABLE collection_dimension_range` — `id` PK, `collection_id BIGINT NOT NULL` FK на `collection(id)`, `liner_dimension_type_id BIGINT NOT NULL` FK на `liner_dimension_type(id)`, `min_value DECIMAL NOT NULL`, `max_value DECIMAL NOT NULL` (декларативный `createTable`, тот же стиль, что и у `pogonazh_surcharge_rule`/`dimension_surcharge_rule`).
- [x] 1.2 Changeset: `CREATE UNIQUE INDEX uk_collection_dimension_range_collection_type ON collection_dimension_range (collection_id, liner_dimension_type_id)`.
- [x] 1.3 Changeset (raw SQL): CHECK-констрейнт `chk_collection_dimension_range_min_le_max`: `min_value <= max_value`.
- [x] 1.4 Подключить `changes/0099-collection-dimension-range.yaml` в `db.changelog-master.yaml`. Без сид-данных диапазонов в этом change (см. proposal.md) — таблица остаётся пустой. Чистый DDL без generated-колонок/partial-индексов — по опыту 0096/0098 не должно требовать desktop-override, но проверить точечным прогоном `DesktopProfileTest` (задача 6.1), прежде чем считать это окончательным (по опыту 0097 предположение однажды не подтвердилось).

## 2. Backend: домен и репозиторий

- [x] 2.1 Создать `backend/src/main/java/com/example/furniturecalculator/domain/CollectionDimensionRange.java` — `@Entity @Table(name = "collection_dimension_range")`: `id`, `@ManyToOne(optional = false) collection` (`LeafCollection`, `collection_id`), `@ManyToOne(optional = false) linerDimensionType` (`liner_dimension_type_id`), `minValue`/`maxValue` (`BigDecimal`, `nullable = false`). Тот же паттерн Lombok (`@Getter`, `@NoArgsConstructor(access = PROTECTED)`), что и у `DimensionSurchargeRule`.
- [x] 2.2 Создать `backend/src/main/java/com/example/furniturecalculator/repository/CollectionDimensionRangeRepository.java`: `Optional<CollectionDimensionRange> findByCollectionIdAndLinerDimensionTypeId(Long, Long)` (для резолвера наценки) и `List<CollectionDimensionRange> findByCollectionId(Long)` (для сборки каталога).

## 3. Backend: резолвер наценки и единый текст ошибки

- [x] 3.1 `DoorConfigurationPricingService.resolveAxisSurchargeMultiplier(...)`: после проверки на совпадение со стандартным каталожным размером и до поиска точной строки `dimension_surcharge_rule` — если `collectionDimensionRangeRepository.findByCollectionIdAndLinerDimensionTypeId(leafType.getCollection().getId(), dimensionType.getId())` присутствует и `customValue` вне `[minValue, maxValue]` — сразу `ResponseStatusException(BAD_REQUEST, "Данная нестандартная величина не поддерживается для данной модели")`, не обращаясь к `dimensionSurchargeRuleRepository`.
- [x] 3.2 Там же: заменить текст в оставшемся (уже существующем) `ResponseStatusException` в конце метода — с «фабрика не производит полотно с размером ... мм для этой оси» на «Данная нестандартная величина не поддерживается для данной модели» (тот же текст, что и в 3.1 — единая формулировка для обоих случаев отказа).

## 4. Backend: DTO и сборка каталога

- [x] 4.1 Создать `backend/src/main/java/com/example/furniturecalculator/dto/DimensionRangeDto.java`: `public record DimensionRangeDto(ReferenceDto dimensionType, BigDecimal minValue, BigDecimal maxValue) {}`.
- [x] 4.2 `ComponentCatalogDto`: добавить поле `List<DimensionRangeDto> dimensionRanges` (после `glazingOptions`, как последнее поле record'а).
- [x] 4.3 `DoorConfigurationCatalogService`: в `buildComponent(...)` и `buildFrameComponent(...)` передать `List.of()` в новый параметр конструктора `ComponentCatalogDto`. В `buildLeafComponent(...)` — получить `collectionDimensionRangeRepository.findByCollectionId(leafType.getCollection().getId())`, смаппить в `List<DimensionRangeDto>` (аналогично существующему `toDto(LinerDimensionOption)` — `ReferenceDto.from(range.getLinerDimensionType())`, `range.getMinValue()`, `range.getMaxValue()`), передать в новый параметр конструктора.

## 5. Frontend

- [x] 5.1 `frontend/src/api/types.ts`: новый интерфейс `DimensionRangeDto { dimensionType: ReferenceDto; minValue: number; maxValue: number }`; `ComponentCatalogDto` — добавить `dimensionRanges: DimensionRangeDto[]`.
- [x] 5.2 `frontend/src/App.tsx`: рядом с полем «Другое значение» длины и рядом с полем «Другое значение» высоты полотна — найти в `leafComponent.dimensionRanges` запись с `dimensionType.code === LENGTH_TYPE_CODE` (соответственно `HEIGHT_TYPE_CODE`) и, если найдена, показать диапазон рядом с полем. По уточнению пользователя — тем же визуальным стилем `<Tag>Доступно: <strong>min–max</strong> мм</Tag>`, что и у бейджей «Длина погонажа: X мм»/«для высоты полотна: Y мм» кромки (`renderEdgeCard`), а не текстом `Typography.Text` как изначально.

## 6. Проверка

- [x] 6.1 Точечно прогнать `DesktopProfileTest` (`--tests`), чтобы убедиться, что миграция 0099 применяется и на H2 (desktop-профиль); если нет — по образцу 0097 добавить `changes-desktop-overrides/` и context-разбивку в master changelog. Прогнан — BUILD SUCCESSFUL, desktop-override не потребовался.
- [x] 6.2 Точечно прогнать `DoorConfigurationPricingServiceTest` (`--tests`) — новая проверка диапазона (при пустой таблице `collection_dimension_range`) не меняет поведение существующих тестов резолва наценки за размер полотна. Прогнан вместе с `DoorConfigurationCatalogServiceTest` (тоже задет расширением `ComponentCatalogDto`) — BUILD SUCCESSFUL, изменений в тестах не потребовалось.
- [x] 6.3 Собрать фронтенд (`npm run build`) — без ошибок типов после расширения `ComponentCatalogDto`/`DimensionRangeDto`. Прогнано — успешно.
- [x] 6.4 Вручную проверить через `GET /api/door-configurations` и расчёт стоимости полотна с реальными диапазонами (см. раздел 7): значение вне диапазона своей серии отклоняется с текстом «Данная нестандартная величина не поддерживается для данной модели» ещё до попытки найти наценку; значение внутри диапазона продолжает резолвиться как раньше; фронтенд показывает диапазон рядом с полем «Другое значение». Проверено пользователем вручную в backend, запущенном из IDE — всё корректно.

## 7. Сид-данные диапазонов (реальные значения от фабрики)

- [x] 7.1 Создать `backend/src/main/resources/db/changelog/changes/0100-collection-dimension-range-seed-data.yaml` — новый (не правка 0099) Liquibase changelog: changeset с `INSERT` высоты (`liner_dimension_type.code = 'DT-002'`) 1900–2950мм для всех 9 коллекций; changeset с `INSERT` ширины (`code = 'DT-001'`) по коллекциям — 600–1000мм для «Вертикаль» (LC-001), «Атмосфера» (LC-002), «Элегант» (LC-003), «Свобода» (LC-005); 400–1000мм для «Гармония» (LC-004), «Геометрия» (LC-006), «Сияние» (LC-007), «Сибирь» (LC-008), «Фантом» (LC-009).
- [x] 7.2 Подключить `changes/0100-collection-dimension-range-seed-data.yaml` в `db.changelog-master.yaml`.
- [x] 7.3 Точечно прогнать `DesktopProfileTest` — миграция 0100 применяется и на H2. Прогнан — BUILD SUCCESSFUL, desktop-override не потребовался.
