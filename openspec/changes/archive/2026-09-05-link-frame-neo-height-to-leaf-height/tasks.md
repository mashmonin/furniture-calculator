## 1. Liquibase-миграции

- [x] 1.1 `0061-liner-dimension-option-max-value.yaml`: `addColumn max_value DECIMAL` (nullable) в `liner_dimension_option`, подключить в `db.changelog-master.yaml`.
- [x] 1.2 `0062-frame-type-height-range-index.yaml`: `DROP INDEX uk_liner_dimension_option_frame_type`, затем `CREATE UNIQUE INDEX uk_liner_dimension_option_frame_type ON liner_dimension_option (frame_type_id, liner_dimension_type_id, value, COALESCE(min_value, -1), COALESCE(max_value, -1)) WHERE frame_type_id IS NOT NULL`, подключить в master.
- [x] 1.3 `0063-neo-frame-height-configurations.yaml`: вставить 4 строки `liner_dimension_option` для `frame_type_id` = id `FT-003`, `liner_dimension_type_id` = id `DT-002` (высота), `is_standard = true`:
  - value=2170, min_value=1900, max_value=2100
  - value=2400, min_value=2150, max_value=2250
  - value=2400, min_value=2300, max_value=2300
  - value=2700, min_value=2350, max_value=2550
  подключить в master.

## 2. Backend

- [x] 2.1 `LinerDimensionOption`: добавить поле `maxValue` (`@Column(name = "max_value")`).
- [x] 2.2 `LinerDimensionOptionDto`: добавить `maxValue`.
- [x] 2.3 `DoorConfigurationCatalogService.toDto(LinerDimensionOption)`: передавать `maxValue` в DTO.
- [x] 2.4 `DoorConfigurationPricingService.validateHeightWithinLeafRange`: обобщить верхнюю границу — `option.getMaxValue() != null ? option.getMaxValue() : option.getValue()` (для кромки поведение не меняется, `maxValue` у неё всегда `null`).
- [x] 2.5 `DoorConfigurationPricingService.addComponentIfPresent`, ветка `FrameType`: получить `heightOption` через `validatedDimensionOption(componentName, frameType, selection.heightOptionId())`; если `heightOption != null` и `frameType.getCode().equals("FT-003")` — вызвать `validateHeightWithinLeafRange`. Не передавать `heightOption` в `framePostPrice`/`findMostSpecificPrice` (стоимость короба по-прежнему не зависит от высоты).
- [x] 2.6 Вынести код `"FT-003"` в именованную константу рядом с `HEIGHT_TYPE_CODE`/`LENGTH_TYPE_CODE` (например, `NEO_FRAME_TYPE_CODE`).

## 3. Тесты backend

- [x] 3.1 `DoorConfigurationPricingServiceTest`: высота полотна внутри диапазона короба «НЕО» → расчёт выполняется.
- [x] 3.2 Высота полотна вне диапазона короба «НЕО» → 400.
- [x] 3.3 Высота короба «НЕО» выбрана без высоты полотна → 400.
- [x] 3.4 Высота полотна попадает в разрыв между диапазонами (например, 2260) → 400.
- [x] 3.5 Высота полотна ровно 2300 выбирает опцию value=2400 с диапазоном [2300, 2300], а не диапазон [2150, 2250] → расчёт выполняется.
- [x] 3.6 Другой тип короба (не FT-003) с опцией высоты, если такая появится в тестовых данных, не подвергается проверке диапазона.

## 4. Frontend

- [x] 4.1 `frontend/src/api/types.ts`: добавить `maxValue: number | null` в `LinerDimensionOptionDto`.
- [x] 4.2 `frontend/src/App.tsx`: добавить `frameNeoHeightOptions(component, leafHeightValue)` по образцу `edgeHeightOptions`, использующую `minValue`/`maxValue` (с фолбэком на `value`, если `maxValue` не задан).
- [x] 4.3 В рендере группы «Высота» (~строка 614) добавить ветку для `step.key === 'frame' && component.type.code === 'FT-003'`, использующую `frameNeoHeightOptions`.
- [x] 4.4 В `updateSelection`/`isLeafHeightChange`: сбрасывать `next.frame.heightOptionId` при смене высоты полотна (аналогично уже существующему сбросу `next.edge.heightOptionId`).

## 5. Проверка

- [x] 5.1 `./gradlew test --tests "*DoorConfigurationPricingServiceTest*"` (из `backend/`).
- [x] 5.2 `npm run build` (из `frontend/`) — проверка типов и сборки.
- [x] 5.3 Ручная проверка в браузере: выбрать конфигурацию с коробом «НЕО», убедиться, что группа «Высота» короба скрыта до выбора высоты полотна, показывает верный вариант(ы) после выбора, и сбрасывается при смене высоты полотна.

## 6. Backend: высота обязательна для короба «НЕО»

- [x] 6.1 `DoorConfigurationPricingService.addComponentIfPresent`, ветка `FrameType`: если `NEO_FRAME_TYPE_CODE.equals(frameType.getCode())` и `selection.heightOptionId() == null` — `ResponseStatusException(BAD_REQUEST, "для короба «НЕО» необходимо выбрать высоту")`, до текущей проверки `if (heightOption != null && ...)`.
- [x] 6.2 Тест в `DoorConfigurationPricingServiceTest`: короб «НЕО» без выбранной высоты (`heightOptionId=null`) → 400, независимо от того, выбрана ли высота полотна.
- [x] 6.3 Регрессионный тест/проверка: для другого типа короба (не FT-003) отсутствие `heightOptionId` по-прежнему не является ошибкой.

## 7. Frontend: исключение короба «НЕО» из каскада при несовместимой высоте полотна

- [x] 7.1 Вынести формулу диапазона из `frameNeoHeightOptions` в переиспользуемый предикат (например, `neoFrameCoversHeight(component, leafHeightValue)`), используемый и текущей фильтрацией высоты, и новой фильтрацией каскада.
- [x] 7.2 `buildCascadeSteps`: добавить необязательный параметр `leafHeightValue: number | undefined`; перед `applyCascadeStep('frame', ...)` отфильтровать `candidates`, убрав конфигурации с `frame.type.code === NEO_FRAME_TYPE_CODE`, чьи опции высоты не покрывают `leafHeightValue` (когда оно задано).
- [x] 7.3 В месте вызова (App.tsx:387): вызвать `buildCascadeSteps` дважды — первый раз как сейчас (чтобы получить `leafComponent`/`leafHeightValue`), второй раз с этим `leafHeightValue` — и использовать результат второго вызова как `cascadeSteps`/`selectedConfiguration`.
- [x] 7.4 Ручная проверка в браузере: при высоте полотна, попадающей в разрыв (например, 2700 мм), короб «НЕО» не отображается в списке доступных типов короба; при совместимой высоте — снова отображается.

## 8. Повторная проверка после доп. правки

- [x] 8.1 `./gradlew test --tests "*DoorConfigurationPricingServiceTest*"` (из `backend/`).
- [x] 8.2 `npm run build` (из `frontend/`).
- [x] 8.3 Ручная проверка в браузере, объединяющая отложенный пункт 5.3 и новый пункт 7.4.
