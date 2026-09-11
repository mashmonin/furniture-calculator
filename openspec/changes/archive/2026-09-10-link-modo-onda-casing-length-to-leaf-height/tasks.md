## 1. Liquibase-миграции

- [x] 1.1 `0069-door-casing-type-length-range-index.yaml`: `DROP INDEX uk_liner_dimension_option_door_casing_type`, затем `CREATE UNIQUE INDEX uk_liner_dimension_option_door_casing_type ON liner_dimension_option (door_casing_type_id, liner_dimension_type_id, value, COALESCE(min_value, -1), COALESCE(max_value, -1)) WHERE door_casing_type_id IS NOT NULL`; подключить в master с `context: "!desktop"`.
- [x] 1.2 `changes-desktop-overrides/0069-door-casing-type-length-range-index.yaml`: `DROP INDEX` + `CREATE UNIQUE INDEX uk_liner_dimension_option_door_casing_type ON liner_dimension_option (door_casing_type_key, liner_dimension_type_id, value, min_value_norm, max_value_norm)`, переиспользуя уже существующие `door_casing_type_key`/`min_value_norm`/`max_value_norm`; подключить в master с `context: "desktop"`.
- [x] 1.3 `0070-modo-onda-length-range-configurations.yaml`:
  - UPDATE существующей строки `liner_dimension_option` (value=2100, `door_casing_type_id` в (id DCT-003, id DCT-004), `liner_dimension_type_id` = id DT-001) → `value=2250, min_value=1900, max_value=2100`.
  - INSERT по 1 строке на каждый из 2 кодов: value=2400, min_value=2150, max_value=2250, is_standard=true.
  - INSERT по 1 строке на каждый из 2 кодов: value=2700, min_value=2300, max_value=2300, is_standard=true.
  - INSERT по 1 строке на каждый из 2 кодов: value=2700, min_value=2350, max_value=2550, is_standard=true.
  - подключить в master.
- [x] 1.4 `0071-modo-onda-configuration-price-length-decouple.yaml`: `UPDATE configuration_price SET length_option_id = NULL WHERE door_casing_type_id IN (SELECT id FROM door_casing_type WHERE code IN ('DCT-003','DCT-004'))`; подключить в master.

## 2. Backend

- [x] 2.1 `DoorConfigurationPricingService`: добавить константу `LENGTH_RANGE_DOOR_CASING_TYPE_CODES = Set.of("DCT-003", "DCT-004")` рядом с `LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES`.
- [x] 2.2 Вынести текущий блок «обязательная длина + диапазон» для `FrameExtensionsType` в приватный метод `requireLengthWithinLeafRange(String componentName, String ownerCode, Set<String> lengthRangeCodes, String missingLengthMessage, ComponentSelectionDto selection, LinerDimensionOption lengthOption, BigDecimal leafHeightValue)`; заменить существующий блок вызовом этого метода (без изменения поведения для добора «ТС»).
- [x] 2.3 В общей (не-`FrameType`) ветке `addComponentIfPresent`, после блока для `FrameExtensionsType`: добавить `if (type instanceof DoorCasingType doorCasingType) { requireLengthWithinLeafRange(componentName, doorCasingType.getCode(), LENGTH_RANGE_DOOR_CASING_TYPE_CODES, "для этого наличника необходимо выбрать длину", selection, lengthOption, leafHeightValue); }`.

## 3. Тесты backend

- [x] 3.1 `TestEntities`: добавить перегрузку `doorCasingType(long id, String code)` по образцу `frameExtensionsType(long id, String code)`.
- [x] 3.2 `DoorConfigurationPricingServiceTest`: высота полотна внутри диапазона длины наличника «Модо»/«Онда» → расчёт выполняется.
- [x] 3.3 Высота полотна вне диапазона длины наличника → 400.
- [x] 3.4 Длина наличника выбрана без высоты полотна → 400.
- [x] 3.5 Высота полотна попадает в разрыв между диапазонами длины (например, 2260) → 400.
- [x] 3.6 Высота полотна ровно 2300 выбирает опцию value=2700 с диапазоном [2300, 2300], а не диапазон [2150, 2250] → расчёт выполняется.
- [x] 3.7 Наличник «Модо»/«Онда» без выбранной длины (`lengthOptionId=null`) → 400, независимо от того, выбрана ли высота полотна.
- [x] 3.8 Другой наличник (не из набора DCT-003–DCT-004, например «Эво») с опцией длины и без выбранного `lengthOptionId` — не является ошибкой, проверка диапазона не применяется.
- [x] 3.9 Цена наличника «Модо»/«Онда» не зависит от выбранной длины: два запроса с одинаковым типом наличника, но разными допустимыми `lengthOptionId` (в пределах их диапазонов), возвращают одинаковую цену компонента.
- [x] 3.10 Регрессия: существующие тесты диапазона добора «ТС» (`высота_полотна_внутри_диапазона_длины_добора_тс_расчёт_выполняется` и соседние) по-прежнему проходят после рефакторинга `requireLengthWithinLeafRange` (задача 2.2) — без изменений в самих тестах. Подтверждено: `./gradlew test --tests "*DoorConfigurationPricingServiceTest*"` — BUILD SUCCESSFUL.

## 4. Frontend

- [x] 4.1 `frontend/src/App.tsx`: переименовать `doborTsLengthOptions` → `lengthRangeOptions` и `doborTsCoversHeight` → `lengthRangeCoversHeight`; обновить существующие вызовы для добора «ТС» (без изменения поведения).
- [x] 4.2 Добавить константу `LENGTH_RANGE_DOOR_CASING_TYPE_CODES = ['DCT-003', 'DCT-004']`.
- [x] 4.3 В рендере группы «Длина» добавить ветку: `step.key === 'doorCasing' && LENGTH_RANGE_DOOR_CASING_TYPE_CODES.includes(component.type.code)` → `lengthRangeOptions(component, leafHeightValue)`, иначе — текущий фильтр по `LENGTH_TYPE_CODE` без изменений.
- [x] 4.4 В `buildCascadeSteps`: добавить блок фильтрации `candidates` для `key === 'doorCasing'` (по образцу блоков для `key === 'frame'`/`key === 'frameExtensions'`), исключающий конфигурации, где `configuration.doorCasing` есть, его тип — код из `LENGTH_RANGE_DOOR_CASING_TYPE_CODES`, и `!lengthRangeCoversHeight(configuration.doorCasing, leafHeightValue)`.
- [x] 4.5 В `updateSelection`/`isLeafHeightChange`: сбрасывать `next.doorCasing.lengthOptionId`, если резолвленный тип doorCasing-компонента входит в `LENGTH_RANGE_DOOR_CASING_TYPE_CODES` (по аналогии с существующим условным сбросом `next.frameExtensions.lengthOptionId`).

## 5. Проверка

- [x] 5.1 `npm run build` (из `frontend/`) — проверка типов и сборки.
- [x] 5.2 Ручная проверка в браузере: выбрать конфигурацию с наличником «Модо» или «Онда», убедиться, что группа «Длина» скрыта до выбора высоты полотна, показывает верный вариант(ы) после выбора (включая точку 2300 → 2700), пуста при высоте в разрыве (например, 2700 мм высоты полотна → наличник недоступен), и сбрасывается при смене высоты полотна; убедиться, что наличник «Модо»/«Онда» исчезает из каскадного выбора при несовместимой высоте полотна и появляется вновь при совместимой; прочие наличники — без изменений в поведении. Подтверждено пользователем.
