## 1. Миграция БД (changeset 0090)

- [x] 1.1 `createTable glazing_type(id, name NOT NULL)`; подключить changeset в `db.changelog-master.yaml`
- [x] 1.2 `createTable glazing_option(id, glazing_type_id FK NOT NULL, leaf_type_id FK NOT NULL)` + `UNIQUE(glazing_type_id, leaf_type_id)`
- [x] 1.3 `INSERT INTO glazing_type` — 6 строк: «Прозрачное», «Прозрачное серое», «Прозрачное бронза», «Сатинированное», «Сатинированное серое», «Сатинированное бронзовое»
- [x] 1.4 `INSERT INTO glazing_option` — `glazing_type CROSS JOIN leaf_type`, отфильтрованный по `leaf_type.code IN ('LT-017','LT-018','LT-019','LT-023','LT-024','LT-025','LT-030','LT-031','LT-032','LT-033','LT-036','LT-037')` — 72 строки
- [x] 1.5 `DELETE FROM mirror_finish_option WHERE leaf_type_id IN (SELECT id FROM leaf_type WHERE code IN (те же 12 кодов))` — 36 строк
- [x] 1.6 `UPDATE leaf_type SET panel_type = 'GLAZED' WHERE code IN (те же 12 кодов)`

## 2. Backend: домен, репозиторий, маппинг

- [x] 2.1 Добавить `GlazingType` (id, name) и `GlazingOption` (id, glazingType, leafType) — структурные копии `MirrorFinishType`/`MirrorFinishOption`, без поля наценки
- [x] 2.2 Добавить `GlazingOptionRepository.findByLeafTypeId`
- [x] 2.3 Добавить поле `glazingOptions: List<ReferenceDto>` в `ComponentCatalogDto` (после `mirrorFinishOptions`)
- [x] 2.4 В `DoorConfigurationCatalogService` — внедрить `GlazingOptionRepository`, заполнить `glazingOptions` только для leaf-компонента (`toDto(GlazingOption)` → `ReferenceDto(glazingType.id, null, glazingType.name, null)`); для остальных компонентов — пустой список

## 3. Тесты

- [x] 3.1 Backend: тест на отклонение вставки `glazing_type` без `name` и на повторную пару `glazing_option` (unique constraint) — `DoorConfigurationApiIntegrationTest`; вставка без FK-ссылок отдельно не тестировалась (NOT NULL на колонках), как и для остальных подобных таблиц в проекте
- [x] 3.2 Backend (интеграционный, `DoorConfigurationApiIntegrationTest`): каталог конфигураций отдаёт виды остекления только для leaf-компонента, пустой список — если их нет; плюс unit-тесты в `DoorConfigurationCatalogServiceTest`
- [x] 3.3 Проверено на dev-БД (миграция уже применена пользователем через IDE): у всех 12 leaf_type `panel_type = GLAZED`, у каждого ровно 6 `glazing_option` и 0 `mirror_finish_option`

## 4. Frontend: отображение вида остекления (добавлено по обратной связи)

- [x] 4.1 Добавить поле `glazingOptions: ReferenceDto[]` в `ComponentCatalogDto` (`frontend/src/api/types.ts`)
- [x] 4.2 Добавить `GLAZING_LABEL`, интерфейс `GlazingStep` и `resolveGlazingStep` (по образцу `MirrorFinishStep`/`resolveMirrorFinishStep`) в `App.tsx`
- [x] 4.3 Добавить состояние `glazingTypeId` и `handleGlazingTypeChange`; сброс `glazingTypeId` в `handlePanelTypeChange` и `handleClearAll`
- [x] 4.3.1 Исправлено по обратной связи: `handleGlazingTypeChange` изначально сбрасывал коллекцию/каскад/опции по образцу `handleMirrorFinishTypeChange` — стирало уже выбранную модель полотна при каждом выборе вида остекления. Убран лишний сброс: `handleGlazingTypeChange` теперь только `setGlazingTypeId(id)`, т.к. вид остекления ни на что не влияет
- [x] 4.4 Вычислить `glazingStep` (видим только при `resolvedPanelType === 'GLAZED'`, от `panelTypeFilteredConfigurations`) и отрендерить `OptionGroup` `variant="select"` в одном ряду с «Полотно» (там же, где «Исполнение с зеркалом»)
- [x] 4.5 Проверить, что выбор вида остекления НЕ передаётся в `PricingRequestDto` и не сужает каталог (сознательное ограничение, см. design.md)
- [x] 4.6 Собрать (`tsc -b --noEmit`) и прогнать `npm run lint` — оба чистые

## 5. Попутный фикс продуктивности (вне первоначального плана)

- [x] 5.1 `DoorConfigurationCatalogService.getAllConfigurations` строил компоненты повторно на каждую из тысяч строк `door_configuration` вместо одного раза на уникальный тип — из-за этого каталог отдавался 35–67 с; переписано на дедупликацию по `id` (без изменения контракта API), см. proposal.md Impact

## 6. Попутный фикс сброса каскада у «Исполнение с зеркалом» (по обратной связи «та же самая проблема с зеркалом»)

- [x] 6.1 `handleMirrorFinishTypeChange` сбрасывал коллекцию/каскад/опции при каждой смене исполнения зеркала — тот же баг, что и у остекления (задача 4.3.1), только исходный источник копирования. Убран лишний сброс: теперь только `setMirrorFinishTypeId(id)`
- [x] 6.2 Спецификация `door-configurator-ui`: требование «Выбор исполнения зеркала» заменено (REMOVED+ADDED, не MODIFIED — см. design.md) на «Выбор конкретного исполнения зеркала»: убраны сценарии, утверждавшие сужение каталога (не было реализовано) и сброс каскада (был баг, исправлен); сценарий передачи id в запрос расчёта сохранён без изменений
- [x] 6.3 Собрать (`tsc -b --noEmit`) и прогнать `npm run lint` — оба чистые; `openspec validate --strict` — чисто
