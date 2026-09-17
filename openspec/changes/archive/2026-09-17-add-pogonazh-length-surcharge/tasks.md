## 1. Схема БД

- [x] 1.1 Новый Liquibase changelog `backend/src/main/resources/db/changelog/changes/0094-pogonazh-surcharge-rule.yaml`: `createTable pogonazh_surcharge_rule` (id, frame_type_id nullable FK, door_casing_type_id nullable FK, frame_extensions_type_id nullable FK, value NUMERIC NOT NULL, surcharge_percent NUMERIC NOT NULL).
- [x] 1.2 В том же changelog: CHECK-constraint «ровно один из трёх FK не NULL» и три частичных уникальных индекса — `(frame_type_id, value) WHERE frame_type_id IS NOT NULL`, `(door_casing_type_id, value) WHERE door_casing_type_id IS NOT NULL`, `(frame_extensions_type_id, value) WHERE frame_extensions_type_id IS NOT NULL` (см. design.md — «Decisions», «Migration Plan»).
- [x] 1.3 Подключить новый changelog в `db.changelog-master.yaml`.
- [x] 1.4 Сид-данные короба «НЕО»/«Компланар» (FT-002, FT-003): строки для каждого каталожного значения высоты, отличного от базового 2170 мм (2400, 2700, для «Компланар» дополнительно 3000 — см. миграции 0063/0064), surcharge_percent = 30.
- [x] 1.5 Сид-данные наличника (все 9 door_casing_type DCT-001…DCT-009): строки для каждого каталожного значения длины, отличного от базового 2250 мм, per владельцу — см. миграции 0070 (DCT-003/004: 2400, 2700), 0072 (DCT-001: 2400/2700/3000; DCT-002,005-009: 2700), surcharge_percent = 30.
- [x] 1.6 Сид-данные добора (все 10 frame_extensions_type FET-004…FET-013): строки для каждого каталожного значения длины, отличного от базового 2170 мм, per владельцу — см. миграции 0066 (FET-004-007: 2400, 2700), 0074 (FET-008-013: 2400, 2700, плюс 3000 для FET-008/009/010 или 2950 для FET-011/012/013 согласно миграции 0074), surcharge_percent = 30.
- [x] 1.7 Сид-данные короба «Фантом» (FT-001): строки для значений 2050, 2150, 2200, 2250, 2300, surcharge_percent = 30 (значения 1900/1950/2000/2100 — базовые, строк для них не создавать, см. design.md — «Отсутствие строки = базовое значение»).
- [x] 1.8 Добавить JPA-сущность `PogonazhSurchargeRule` (аналогично `DimensionSurchargeRule`) и `PogonazhSurchargeRuleRepository` с методами поиска по (frame_type_id, value) / (door_casing_type_id, value) / (frame_extensions_type_id, value).

## 2. Бэкенд: применение наценки в расчёте стоимости

- [x] 2.1 В `DoorConfigurationPricingService.addComponentIfPresent`, ветка `FrameType`: определить эффективное значение высоты (`frameHeightOption.getValue()` для «НЕО»/«Компланар», `frameHeightMmOverride` для «Фантом»), найти правило `pogonazh_surcharge_rule` по `frame_type_id` и этому значению, применить найденный процент к результату `framePostPrice(...)` (округление после применения, как для leaf) — итоговая цена компонента отличается от базовой (`framePostPrice`) ровно на эту наценку.
- [x] 2.2 В общей ветке (`DoorCasingType`/`FrameExtensionsType`): после вычисления `lengthOption` найти правило по `door_casing_type_id`/`frame_extensions_type_id` и `lengthOption.getValue()`, применить найденный процент к цене из `findMostSpecificPrice` **до** умножения на `quantity`; базовая цена компонента — цена до наценки, умноженная на то же количество.
- [x] 2.3 Проверить (тестами), что наценка за погонаж не влияет на leaf/edge и не взаимодействует с `applySequentialSurcharges` (наценки leaf) — независимые пути.
- [x] 2.4 Убедиться, что наценка одинаково применяется во всех вызывающих `addComponentIfPresent` методах: `calculate()`, `calculateForFrameGroup()`, `resolveSpecificationComponents()` (без отдельного кода в каждом — за счёт общей точки интеграции).

## 3. Бэкенд: эндпоинт `GET /api/pricing-surcharges`

- [x] 3.1 Добавить `PogonazhSurchargeRuleDto` (владелец — дискриминатор/id-поля, value, surchargePercent) и поле `pogonazhSurchargeRules: List<PogonazhSurchargeRuleDto>` в `PricingSurchargesDto`.
- [x] 3.2 Реализовать сборку этого списка в `PricingSurchargesService` (все строки `pogonazh_surcharge_rule` из репозитория).
- [x] 3.3 Проверить, что пустой справочник возвращает 200 с пустым списком, не влияя на остальные поля ответа.

## 4. Тесты бэкенда (`./gradlew test --tests ...`, без полного прогона — см. память)

- [x] 4.1 Юнит/интеграционные тесты на `pogonazh_surcharge_rule`: constraint «ровно один владелец», уникальность (владелец, value) через частичные индексы, допустимость одинакового value для разных владельцев.
- [x] 4.2 Тесты `DoorConfigurationPricingService`/контроллера расчёта: наценка применяется к коробу «НЕО»/«Компланар» за нестандартную высоту, к коробу «Фантом» за нестандартную высоту полотна, к наличнику/добору за нестандартную длину; базовое значение — наценки нет; наценка и количество наличника/добора комбинируются корректно (наценка до умножения на количество).
- [x] 4.3 Тест на независимость: одно и то же значение высоты полотна одновременно триггерит `dimension_surcharge_rule` (leaf) и `pogonazh_surcharge_rule` (короб «Фантом») — обе наценки применяются к разным компонентам без взаимного влияния.
- [x] 4.4 Тест `GET /api/pricing-surcharges`: ответ содержит правила погонажа наряду с существующими полями.
- [x] 4.5 Тест этапного эндпоинта короба (`frame-group-standalone-pricing`): наценка отражена в цене короба/наличника/добора и в их базовой цене.

## 5. Фронтенд

- [x] 5.1 Расширить TS-тип ответа `GET /api/pricing-surcharges` полем правил погонажа.
- [x] 5.2 Реализовать функцию разбора наценки за погонаж по (тип владельца, id владельца, выбранное значение мм) — аналог `computeSurchargeBreakdown`, но для короба/наличника/добора (см. design.md — «Фронтенд»).
- [x] 5.3 Отобразить процент наценки за погонаж в общем блоке «Надбавки к цене за нестандарт» (переименован из «Надбавки к цене полотна»), по одной строке на уникальный процент, а не рядом с ценой каждого компонента — обновлено по просьбе пользователя после первичной реализации (изначально показывалось рядом с ценой каждого компонента).
- [ ] 5.4 Проверить вручную (`npm run dev`) сценарии: базовая высота короба «НЕО» (2170) — без наценки; нестандартная (2400) — наценка и базовая/итоговая цена короба различаются; то же для наличника/добора и для короба «Фантом» с произвольной высотой полотна. (Требует локального backend+БД — оставлено пользователю, см. итоговый отчёт.)

## 6. Финальная проверка

- [x] 6.1 `./gradlew compileJava compileTestJava` из `backend/` — успешно (полный `build`/`test` не запускался по просьбе пользователя: бэкенд-тесты сейчас нестабильны, и бэкенд запускается пользователем отдельно в IDE).
- [x] 6.2 `npm run build` и `npm run lint` из `frontend/` — оба успешно.
- [x] 6.3 Свериться со сценариями в specs delta (door-configuration-api, frame-group-standalone-pricing, door-configurator-ui, pogonazh-surcharge-rules) — каждый сценарий покрыт кодом или тестом (см. итоговый отчёт по /opsx:apply).
