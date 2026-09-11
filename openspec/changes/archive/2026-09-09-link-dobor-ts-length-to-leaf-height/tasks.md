## 1. Liquibase-миграции

- [x] 1.1 `0065-frame-extensions-type-length-range-index.yaml`: `DROP INDEX uk_liner_dimension_option_frame_extensions_type`, затем `CREATE UNIQUE INDEX uk_liner_dimension_option_frame_extensions_type ON liner_dimension_option (frame_extensions_type_id, liner_dimension_type_id, value, COALESCE(min_value, -1), COALESCE(max_value, -1)) WHERE frame_extensions_type_id IS NOT NULL`; подключить в master с `context: "!desktop"`.
- [x] 1.2 `changes-desktop-overrides/0065-frame-extensions-type-length-range-index.yaml`: `DROP INDEX` + `CREATE UNIQUE INDEX uk_liner_dimension_option_frame_extensions_type ON liner_dimension_option (frame_extensions_type_key, liner_dimension_type_id, value, min_value_norm, max_value_norm)`, переиспользуя уже существующие `frame_extensions_type_key`/`min_value_norm`/`max_value_norm` (ALTER TABLE не требуется); подключить в master с `context: "desktop"`.
- [x] 1.3 `0066-dobor-ts-length-range-configurations.yaml`:
  - UPDATE 4 существующих строк `liner_dimension_option` (value=2170, `frame_extensions_type_id` в (id FET-004, id FET-005, id FET-006, id FET-007), `liner_dimension_type_id` = id DT-001) → `min_value=1900, max_value=2100`.
  - INSERT по 1 строке на каждый из 4 кодов: value=2400, min_value=2150, max_value=2250, is_standard=true.
  - INSERT по 1 строке на каждый из 4 кодов: value=2400, min_value=2300, max_value=2300, is_standard=true.
  - INSERT по 1 строке на каждый из 4 кодов: value=2700, min_value=2350, max_value=2550, is_standard=true.
  - подключить в master.
- [x] 1.4 `0067-dobor-ts-configuration-price-length-decouple.yaml`: `UPDATE configuration_price SET length_option_id = NULL WHERE frame_extensions_type_id IN (SELECT id FROM frame_extensions_type WHERE code IN ('FET-004','FET-005','FET-006','FET-007'))`; подключить в master.

## 2. Backend

- [x] 2.1 `DoorConfigurationPricingService`: добавить константу `LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES = Set.of("FET-004", "FET-005", "FET-006", "FET-007")` (или `DOBOR_TS_FRAME_EXTENSIONS_TYPE_CODES`, см. design.md) рядом с `HEIGHT_RANGE_FRAME_TYPE_CODES`.
- [x] 2.2 `validateHeightWithinLeafRange`: добавить параметр `String expectedDimensionTypeCode`, заменить внутренний гейт `HEIGHT_TYPE_CODE.equals(...)` на `expectedDimensionTypeCode.equals(...)`; обновить оба существующих вызова (edge, frame «НЕО»/«Компланар»), передав `HEIGHT_TYPE_CODE` явно.
- [x] 2.3 В общей (не-`FrameType`) ветке `addComponentIfPresent`, после вычисления `lengthOption`: если `type instanceof FrameExtensionsType frameExtensionsType` и `frameExtensionsType.getCode()` входит в набор из 2.1 — при `selection.lengthOptionId() == null` бросить 400 («для добора «ТС» необходимо выбрать длину»); иначе, если `lengthOption != null`, вызвать `validateHeightWithinLeafRange(componentName, LENGTH_TYPE_CODE, lengthOption, leafHeightValue)`.

## 3. Тесты backend

- [x] 3.1 `TestEntities`: добавить перегрузку `frameExtensionsType(long id, String code)` по образцу `frameType(long id, String code)`.
- [x] 3.2 `DoorConfigurationPricingServiceTest`: высота полотна внутри диапазона длины добора «ТС» → расчёт выполняется.
- [x] 3.3 Высота полотна вне диапазона длины добора «ТС» → 400.
- [x] 3.4 Длина добора «ТС» выбрана без высоты полотна → 400.
- [x] 3.5 Высота полотна попадает в разрыв между диапазонами длины (например, 2260) → 400.
- [x] 3.6 Высота полотна ровно 2300 выбирает опцию value=2400 с диапазоном [2300, 2300], а не диапазон [2150, 2250] → расчёт выполняется.
- [x] 3.7 Добор «ТС» без выбранной длины (`lengthOptionId=null`) → 400, независимо от того, выбрана ли высота полотна.
- [x] 3.8 Другой тип добора (не из набора FET-004–FET-007, например добор «КОМПЛАНАР») с опцией длины и без выбранного `lengthOptionId` — не является ошибкой, проверка диапазона не применяется.
- [x] 3.9 Цена добора «ТС» не зависит от выбранной длины: две конфигурации с одинаковым типом добора, но разными допустимыми `lengthOptionId` (в пределах их диапазонов), возвращают одинаковую цену компонента (регрессия к существующему тесту `количество_умножает_цену_добора`, где `length_option_id` в `configuration_price` теперь `null`).

## 4. Frontend

- [x] 4.1 `frontend/src/App.tsx`: переименовать `frameHeightRangeOptionCoversLeafHeight` в `dimensionRangeCoversLeafHeight`, обновить оба существующих вызова (`frameHeightRangeOptions`, `frameCoversHeight`).
- [x] 4.2 Добавить константу `DOBOR_TS_FRAME_EXTENSIONS_TYPE_CODES = ['FET-004', 'FET-005', 'FET-006', 'FET-007']`.
- [x] 4.3 Добавить `doborTsLengthOptions(component, leafHeightValue)` (фильтр по `LENGTH_TYPE_CODE` + `dimensionRangeCoversLeafHeight`) и `doborTsCoversHeight(component, leafHeightValue)` — по образцу `frameHeightRangeOptions`/`frameCoversHeight`.
- [x] 4.4 В рендере группы «Длина» (~строка 645) добавить ветку: `step.key === 'frameExtensions' && DOBOR_TS_FRAME_EXTENSIONS_TYPE_CODES.includes(component.type.code)` → `doborTsLengthOptions(component, leafHeightValue)`, иначе — текущий фильтр по `LENGTH_TYPE_CODE` без изменений.
- [x] 4.5 В `buildCascadeSteps`: добавить блок фильтрации `candidates` для `key === 'frameExtensions'` (по образцу блока для `key === 'frame'`), исключающий конфигурации, где `configuration.frameExtensions` есть, его тип — код из `DOBOR_TS_FRAME_EXTENSIONS_TYPE_CODES`, и `!doborTsCoversHeight(configuration.frameExtensions, leafHeightValue)`.
- [x] 4.6 В `updateSelection`/`isLeafHeightChange`: сбрасывать `next.frameExtensions.lengthOptionId`, если резолвленный тип frameExtensions-компонента входит в `DOBOR_TS_FRAME_EXTENSIONS_TYPE_CODES` (по аналогии с существующим условным сбросом `next.frame.heightOptionId`).

## 5. Проверка

- [ ] 5.1 `./gradlew test --tests "*DoorConfigurationPricingServiceTest*"` (из `backend/`) — пропущено по просьбе пользователя: бэкенд-тесты сейчас сломаны.
- [x] 5.2 `npm run build` (из `frontend/`) — проверка типов и сборки.
- [x] 5.3 Ручная проверка в браузере: выбрать конфигурацию с добором «ТС», убедиться, что группа «Длина» скрыта до выбора высоты полотна, показывает верный вариант(ы) после выбора (включая точку 2300), пуста при высоте в разрыве (например, 2700 → добор «ТС» недоступен), и сбрасывается при смене высоты полотна; убедиться, что добор «ТС» исчезает из каскадного выбора при несовместимой высоте полотна и появляется вновь при совместимой; добор «КОМПЛАНАР» — без изменений в поведении. Подтверждено пользователем.
