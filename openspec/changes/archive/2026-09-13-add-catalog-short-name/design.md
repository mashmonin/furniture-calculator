## Context

`CatalogType` — общий интерфейс (`id`, `code`, `name`), который реализуют 11 доменных классов-справочников: `EdgeType`, `DoorCasingType`, `FrameExtensionsType`, `PostType`, `FrameType`, `HardwareCategory`, `HardwareType`, `LinerDimensionType`, `ColourType`, `LeafType`, `LeafCollection`. Их общая точка выхода на фронт — `ReferenceDto(id, code, name)`, собираемый статическим методом `ReferenceDto.from(CatalogType)`.

`MirrorFinishType` не реализует `CatalogType` (у него нет `code`, зато есть `surcharge_percent`) и живёт отдельно: своя таблица, свой репозиторий, свой DTO (`MirrorFinishSurchargeDto`), а также ручная сборка `ReferenceDto(id, null, name)` в `DoorConfigurationCatalogService.toDto(MirrorFinishOption)` для списка допустимых исполнений зеркала конкретного полотна в каталоге конфигураций.

См. proposal.md — Why/What Changes для мотивации и точного списка значений.

## Goals / Non-Goals

**Goals:**
- Единый способ добавить необязательное краткое наименование к любому `CatalogType`-справочнику, не создавая для каждого свой DTO/эндпоинт.
- Отдельно покрыть `MirrorFinishType`, не пытаясь притянуть его к `CatalogType` (у него другая форма и назначение).
- Заполнить краткие наименования только для пяти конкретных значений, перечисленных в proposal.md; остальные строки остаются с `short_name = NULL`.

**Non-Goals:**
- Не вводится администраторский UI/API для управления краткими наименованиями — заполнение остальных значений в будущем идёт через новые Liquibase changeset'ы (данные), как и сегодня для name/code.
- Не меняется форма id/code/name — краткое наименование только добавляется как новое поле.
- Фронтенд-места, где короткое имя должно замещать полное в отображении, не меняются в рамках этого changeset массово — только там, где это явно уместно (см. tasks.md); остальные экраны продолжают показывать `name` без изменений.

## Decisions

### 1. short_name — колонка на каждой таблице, а не отдельная таблица-расширение
Отдельная таблица `catalog_short_name(owner_table, owner_id, short_name)` была бы избыточна: `CatalogType`-таблицы и так однородны по форме (id, name, code), добавление одной nullable-колонки в каждую — минимальное расширение, согласуется с тем, как уже устроены сами эти таблицы. Обратная сторона — 11 Liquibase changeset'ов вместо одного, но каждый тривиален (`addColumn`).

### 2. short_name — на уровне интерфейса CatalogType, а не только у конкретных реализаций
Раз колонка появляется во всех backing-таблицах, интерфейс `CatalogType` получает `String getShortName()` (может возвращать `null`), а `ReferenceDto.from(CatalogType)` — четвёртое поле `shortName`. Это даёт то самое «добавлю позже для любого другого справочника» без переоткрытия этого дизайна.

### 3. MirrorFinishType — своя колонка и своё поле в DTO, без общего контракта
`MirrorFinishType` не участвует в `CatalogType` и обладает собственной семантикой (наценка, а не code). Тянуть его в общий интерфейс ради одного поля short_name усложнило бы контракт без пользы. Вместо этого: собственная колонка `short_name` в `mirror_finish_type`, собственное поле `shortName` в `MirrorFinishSurchargeDto`, и его же значение — в ручной сборке `ReferenceDto` в `DoorConfigurationCatalogService.toDto(MirrorFinishOption)` (там `ReferenceDto` и так собирается вручную, а не через `CatalogType.from`, так что это просто ещё один аргумент конструктора).

### 4. Данные заполняются отдельными changeset'ами поверх уже применённых
Существующие changeset'ы (`0005-...`, `0060-...`), вставившие сами строки edge_type/mirror_finish_type, не редактируются задним числом (см. CLAUDE.md). Новые changeset'ы: (а) `addColumn short_name` на каждой из 12 таблиц (11 CatalogType + mirror_finish_type), (б) `update` пяти конкретных строк по их существующему уникальному ключу (`code` для edge_type, `name` для mirror_finish_type — у него нет code) нужным значением short_name.

## Risks / Trade-offs

- [11 отдельных `addColumn` changeset'ов ради одного логического изменения] → приемлемо: каждый changeset маленький и однотипный, а Liquibase уже используется в этом проекте именно так (см. `0005-*.yaml` с несколькими changeset'ами подряд за одно предложение).
- [MirrorFinishType обновляется по `name`, а не по стабильному коду, — совпадение текста name станет более значимым] → допустимо: тексты трёх наименований уникальны и не имеют отдельного code уже сегодня (mirror_finish_type не имеет колонки code вовсе); дальнейшее итеративное заполнение так же будет ссылаться на name.
- [Расширение `ReferenceDto` — общего DTO для всего каталога] → аддитивно (новое nullable-поле в конце), существующие потребители на фронте, читающие id/code/name, не ломаются.
