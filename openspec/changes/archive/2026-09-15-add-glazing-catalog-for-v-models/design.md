## Context

См. proposal.md - Why. Точный образец — `mirror_finish_type`/`mirror_finish_option` (changeset `0060-mirror-finish.yaml`, `backend/.../domain/MirrorFinishType.java`, `MirrorFinishOption.java`, `MirrorFinishOptionRepository.java`, маппинг в `DoorConfigurationCatalogService.buildLeafComponent`/`toDto(MirrorFinishOption)`, поле `mirrorFinishOptions` в `ComponentCatalogDto`). Glazing повторяет эту структуру почти один в один, без наценки.

Целевые 12 leaf_type и их текущее состояние (проверено на dev-БД):
- Коды: `LT-017/018/019` (Элегант 01V/02V/021V), `LT-023/024/025` (Гармония 01V/02V/021V), `LT-030/031/032/033` (Свобода 01V/02V/021V/01G), `LT-036/037` (Геометрия 01V/02V).
- Сейчас у всех panel_type = MIRRORED, и у каждого — ровно 3 строки mirror_finish_option (все три исполнения) — итого 36 строк на удаление.

## Goals / Non-Goals

**Goals:**
- Ввести реальный справочник видов остекления и первую партию реально остеклённых моделей.
- Переклассифицировать 12 названных моделей: MIRRORED → GLAZED, с удалением их mirror_finish_option (подтверждено пользователем).
- Отдать виды остекления в каталоге конфигураций для leaf, по образцу зеркала.

**Non-Goals:**
- Наценка за остекление (glazing_type без surcharge_percent — колонку не добавляем; понадобится, добавится отдельным change, как это в своё время сделали для зеркала в `0060`, а не для короткого имени).
- Выбор конкретного вида остекления в запросе расчёта стоимости (`ComponentSelectionDto`) и его валидация/наценка в `DoorConfigurationPricingService` — выбор на фронте остаётся чисто локальным (см. ниже, добавлено по обратной связи).
- Любые другие leaf_type, помимо перечисленных 12 — только они переклассифицируются в этом change.

**Добавлено по обратной связи после первой реализации** (изначально отображение вида остекления было в Non-Goals — пользователь попросил добавить сразу вслед за backend-частью):
- Отображение вида остекления на фронте — выпадающий список рядом с шагом «Полотно», по образцу того, как уже отображается «Исполнение с зеркалом» в том же месте (см. change `filter-by-leaf-panel-type`, где этот шаг перенесён в один ряд с «Полотно»). Переключатель «Тип полотна» уже умел сужать каталог по значению «С остеклением» (см. `filter-by-leaf-panel-type`) — это не менялось, добавлено только отображение конкретных видов остекления.

## Decisions

- **Схема БД**: `glazing_type(id, name)` — без `surcharge_percent` (в отличие от `mirror_finish_type`) и без `short_name` (не запрошено, добавим при необходимости отдельным change по образцу `catalog-reference-short-name`). `glazing_option(id, glazing_type_id FK, leaf_type_id FK)` + `UNIQUE(glazing_type_id, leaf_type_id)` — структурная копия `mirror_finish_option`.
- **Один changeset, пять шагов** (по образцу `0060-mirror-finish.yaml` + `0021-5-backfill-leaf-type-collection` для стиля backfill):
  1. `createTable glazing_type`
  2. `createTable glazing_option` + unique index
  3. `INSERT INTO glazing_type` — 6 строк
  4. `INSERT INTO glazing_option` — `CROSS JOIN glazing_type × leaf_type`, отфильтрованный по `leaf_type.code IN (12 кодов)` — 72 строки
  5. `DELETE FROM mirror_finish_option WHERE leaf_type_id IN (SELECT id FROM leaf_type WHERE code IN (12 кодов))` — 36 строк
  6. `UPDATE leaf_type SET panel_type = 'GLAZED' WHERE code IN (12 кодов)`
  Шаги 5 и 6 идут после наполнения glazing_option, чтобы на каждом промежуточном шаге данные были непротиворечивы (сначала полотно получает новую доступность, затем теряет старую и меняет классификацию).
- **Backend**: `GlazingType`/`GlazingOption` — точные структурные копии `MirrorFinishType`/`MirrorFinishOption` (без поля наценки у типа). `GlazingOptionRepository.findByLeafTypeId`. `ComponentCatalogDto` — новое поле `glazingOptions: List<ReferenceDto>` (после `mirrorFinishOptions`, тем же способом заполняется только для leaf). `DoorConfigurationCatalogService.buildLeafComponent` — аналогичный `toDto(GlazingOption)`, маппящий на `ReferenceDto(glazingType.id, null, glazingType.name, null)` (short_name отсутствует — всегда null, по образцу mirror до появления его short_name).
- **Почему не наращивать существующий `mirror_finish_option`/не вводить общий «finish_option»**: у зеркала и остекления разные владельцы наценки и разная жизненная логика (наценка против её отсутствия); отдельные таблицы дешевле держать порознь, чем городить полиморфную структуру ради демо-масштаба (12 моделей, 2 справочника).
- **Frontend**: `resolveGlazingStep(configurations)` — точная копия `resolveMirrorFinishStep` (видимость = есть хотя бы один вариант среди `configuration.leaf.glazingOptions`), вызывается только когда `resolvedPanelType === 'GLAZED'`, от `panelTypeFilteredConfigurations`. Состояние `glazingTypeId`; `handleGlazingTypeChange` — **не** по образцу `handleMirrorFinishTypeChange` (пересмотрено по обратной связи: первая версия по аналогии с зеркалом сбрасывала коллекцию/каскад/уже введённые опции при каждом выборе вида остекления — но, в отличие от зеркала, вид остекления ни на что не влияет и ничего не сужает, поэтому такой сброс был чистой UX-регрессией: выбор цвета стекла стирал уже выбранную модель полотна). `handleGlazingTypeChange` — просто `setGlazingTypeId(id)`, без побочных сбросов. Рендерится `OptionGroup` с `variant="select"` (в отличие от `mirrorFinishStep`, который использует вариант по умолчанию — ряд кнопок; по явному запросу пользователя вид остекления должен быть выпадающим списком) в том же `flex`-ряду, что «Полотно» и «Исполнение с зеркала» — мутуально исключены (`resolvedPanelType` не может быть одновременно `MIRRORED` и `GLAZED`), поэтому визуально занимают одно и то же место.
- **Почему без наценки/запроса расчёта**: `ComponentSelectionDto` (backend) не имеет поля под вид остекления, и его добавление осталось вне рамок (см. Non-Goals) — выбранный `glazingTypeId` не попадает в `PricingRequestDto`, в отличие от `mirrorFinishTypeId`.
- **Попутный фикс `handleMirrorFinishTypeChange` (по обратной связи «та же самая проблема с зеркалом»)**: при отладке сброса у остекления обнаружилось, что `handleMirrorFinishTypeChange` — источник, с которого была скопирована ошибка, — сам годами сбрасывал коллекцию/каскад/опции при каждой смене исполнения зеркала, хотя реального сужения каталога по `mirrorFinishTypeId` в коде нет (отпало при переходе на сужение по «Тип полотна», см. `filter-by-leaf-panel-type` — `mirrorFinishTypeId` используется только для `PricingRequestDto` и подписи наценки). Приведено к тому же виду, что и `handleGlazingTypeChange`: только `setMirrorFinishTypeId(id)`, без сбросов. Спецификация «Выбор исполнения зеркала» — единственное требование, которое пришлось REMOVE+ADD (а не MODIFIED) в этом change: два её сценария утверждали как раз сужение и сброс, а `openspec validate` не даёт молча уронить сценарий из MODIFIED-блока — только явной заменой требования с новым именем «Выбор конкретного исполнения зеркала» и Reason/Migration.

## Risks / Trade-offs

- [Удаление 36 строк mirror_finish_option — необратимо простым способом (кроме отдельного отката changeset'а)] → осознанное решение, подтверждено пользователем; данные каталога, не заказы клиентов — риск для демо-стенда минимален.
- [Пользователь выбирает конкретный вид остекления в интерфейсе, но это никак не влияет на расчёт стоимости и не сохраняется при отправке] → осознанное ограничение этой сессии (см. Non-Goals) — выбор чисто ознакомительный/визуальный; наценка и передача в запрос расчёта — предмет отдельного будущего change, по аналогии с тем, как это в своё время было сделано для зеркала.
