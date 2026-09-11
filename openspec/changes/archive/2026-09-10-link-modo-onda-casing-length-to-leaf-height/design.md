## Context

См. proposal.md — «Why» и «What Changes». Это третье применение одного и того же принципа диапазонов (после короба «НЕО»/«Компланар» по высоте и добора «ТС» по длине) — вся инфраструктура для этого уже существует и обобщена, задача сводится к данным + новому гейту по коду типа + переиспользованию уже обобщённого кода. Ключевые факты:

1. **Наличник уже на оси «Длина».** `link-frame-neo-height-to-leaf-height` и `link-dobor-ts-length-to-leaf-height` ввели и обобщили механизм диапазонов (min_value/max_value, фолбэк на value) на уровне `liner_dimension_option`/`configuration_price` для любого владельца и любой оси — без изменений схемы и специфики door-configuration-catalog. `general-fixes-leaf-step-colour-casing-length` (2026-09-09) перенёс единственную опцию размера ВСЕХ 9 существующих `door_casing_type` (включая «Модо» DCT-003 и «Онда» DCT-004) с оси «Высота» на «Длина» — сегодня у «Модо»/«Онда» уже есть ровно одна liner_dimension_option на оси DT-001 (value=2100, без диапазона), и соответствующая строка `configuration_price` уже ссылается через `length_option_id` (не `height_option_id`).
2. **Backend уже умеет валидировать диапазон на оси «Длина» для не-frame владельца.** `validateHeightWithinLeafRange(componentName, expectedDimensionTypeCode, dimensionOption, leafHeightValue)` (обобщена в `link-dobor-ts-length-to-leaf-height`) не завязана на конкретный тип владельца — только на переданный код оси и опцию. Единственное, чего не хватает — вызова этого метода для `DoorCasingType` в общей (не-`FrameType`) ветке `addComponentIfPresent`, аналогично уже существующему для `FrameExtensionsType`.
3. **Блок «обязательная длина + диапазон» для добора «ТС» — единственный экземпляр этого паттерна в общей ветке.** Сейчас (после `link-dobor-ts-length-to-leaf-height`) в `addComponentIfPresent` есть один такой блок, гейтящийся `type instanceof FrameExtensionsType && LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES.contains(code)`. Добавление второго, структурно идентичного блока для `DoorCasingType` — повод вынести общую часть (проверка «lengthOptionId не null» + вызов `validateHeightWithinLeafRange` с `LENGTH_TYPE_CODE`) в приватный метод, вместо третьего копипаста паттерна, который уже был обоснованно продублирован один раз (frame/высота и добор-ТС/длина имели разные детали — разный код ошибки, разная ось), но для добора «ТС» и наличника «Модо»/«Онда» детали совпадают полностью (оба — общая ветка, ось «Длина», разница только в наборе кодов и тексте ошибки).
4. **Frontend уже имеет ось-нейтральную геометрию диапазона.** `dimensionRangeCoversLeafHeight` (переименована в `link-dobor-ts-length-to-leaf-height` из `frameHeightRangeOptionCoversLeafHeight`) ни на что не завязана, кроме чисел. `doborTsLengthOptions`/`doborTsCoversHeight` — тонкие обёртки над ней с фильтром по `LENGTH_TYPE_CODE`, без единой строчки, специфичной для добора «ТС» — вся специфика живёт в вызывающем коде (`DOBOR_TS_FRAME_EXTENSIONS_TYPE_CODES.includes(component.type.code)`). Эти две функции подходят для наличника «Модо»/«Онда» без изменений — специфично только имя, которое сейчас вводит в заблуждение (`doborTs...` для кода, который скоро будет использоваться и для наличника).
5. **Наличники «Модо» (DCT-003) и «Онда» (DCT-004) сегодня в каталоге всегда идут в паре с коробом «НЕО» (FT-003)** — во всех миграциях, где они вообще упоминаются (`0013`, `0016`, `0043`, `0046`, `0049`), условие `ft.code = 'FT-003'`; ни одна из этих миграций не затрагивает `is_reverse`. Как и в `link-dobor-ts-length-to-leaf-height` (после исправления), это означает, что наличники «Модо»/«Онда» сегодня недостижимы в реверсивных конфигурациях — правило не проверяется и не документируется отдельно для реверса по той же причине (см. design.md того change, Context п. 7).

## Goals / Non-Goals

**Goals:**
- Ограничить выбор длины наличников «Модо»/«Онда» (DCT-003, DCT-004) диапазоном высоты полотна, не затрагивая другие наличники.
- Переиспользовать уже обобщённый механизм (backend: `validateHeightWithinLeafRange`; frontend: `dimensionRangeCoversLeafHeight`) без дублирования логики диапазона в третий раз.
- Сделать цену наличников «Модо»/«Онда» независимой от выбранной длины — симметрично добору «ТС».

**Non-Goals:**
- Не переносится диапазонное ограничение на другие наличники (DCT-001, DCT-002, DCT-005–DCT-009) — для них сегодня нет такого бизнес-требования.
- Не меняется цена наличников «Модо»/«Онда» по сравнению с текущей (1898/1084 и 2760/1577 соответственно) — меняется только то, от чего эта цена формально зависит.
- Не проверяется и не документируется поведение для реверсивных конфигураций (см. Context, п. 5 — недостижимая комбинация, как и для добора «ТС»).
- Не меняется схема таблиц — используются уже существующие колонки `min_value`/`max_value`/`length_option_id`.

## Decisions

### 1. Вынести общий блок «обязательная длина + диапазон» в приватный метод

Текущий блок для добора «ТС» в `addComponentIfPresent`:
```java
if (type instanceof FrameExtensionsType frameExtensionsType
        && LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES.contains(frameExtensionsType.getCode())) {
    if (selection.lengthOptionId() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "для добора «ТС» необходимо выбрать длину");
    }
    if (lengthOption != null) {
        validateHeightWithinLeafRange(componentName, LENGTH_TYPE_CODE, lengthOption, leafHeightValue);
    }
}
```
выносится в:
```java
private void requireLengthWithinLeafRange(
        String componentName, String ownerCode, Set<String> lengthRangeCodes, String missingLengthMessage,
        ComponentSelectionDto selection, LinerDimensionOption lengthOption, BigDecimal leafHeightValue) {
    if (!lengthRangeCodes.contains(ownerCode)) {
        return;
    }
    if (selection.lengthOptionId() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, missingLengthMessage);
    }
    if (lengthOption != null) {
        validateHeightWithinLeafRange(componentName, LENGTH_TYPE_CODE, lengthOption, leafHeightValue);
    }
}
```
Оба вызова (для `FrameExtensionsType` и для `DoorCasingType`) становятся однострочными:
```java
if (type instanceof FrameExtensionsType frameExtensionsType) {
    requireLengthWithinLeafRange(componentName, frameExtensionsType.getCode(), LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES,
            "для добора «ТС» необходимо выбрать длину", selection, lengthOption, leafHeightValue);
}
if (type instanceof DoorCasingType doorCasingType) {
    requireLengthWithinLeafRange(componentName, doorCasingType.getCode(), LENGTH_RANGE_DOOR_CASING_TYPE_CODES,
            "для этого наличника необходимо выбрать длину", selection, lengthOption, leafHeightValue);
}
```
Поведение для добора «ТС» не меняется — это чистый рефакторинг существующего блока, а не новая логика.

**Альтернатива (отклонена):** скопировать блок ещё раз для `DoorCasingType`, как это уже сделано один раз для добора «ТС» (по аналогии с тем, как короб «НЕО» и добор «ТС» не переиспользовали код друг у друга, а просто оба вызывали уже обобщённый `validateHeightWithinLeafRange`). Отклонено — там разница была содержательной (разная ось, разный текст ошибки, разное место в коде — внутри `instanceof FrameType`-ветки с `return` против общей ветки); здесь оба места идентичны по структуре (общая ветка, ось «Длина», разница только в наборе кодов и тексте сообщения) — третья копия того же самого паттерна была бы чистым дублированием, а не оправданной специализацией.

### 2. Гейт по набору кодов `DoorCasingType`, а не по структуре данных

Аналогично `LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES`, вводится `LENGTH_RANGE_DOOR_CASING_TYPE_CODES = Set.of("DCT-003", "DCT-004")`. Та же причина, что и в `link-dobor-ts-length-to-leaf-height` (Decision 2): явный бизнес-ключ читаемее и не затрагивает прочие наличники, даже если для них в будущем появятся диапазонные данные без такого намерения.

### 3. Данные: переиспользование существующей опции 2100 мм под первый диапазон

Единственная существующая строка `liner_dimension_option` (value=2100, без диапазона) для `DCT-003`/`DCT-004` обновляется: `value=2250, min_value=1900, max_value=2100` — по тому же приёму, что и в `link-dobor-ts-length-to-leaf-height` (переиспользование существующей строки вместо `INSERT`+`DELETE`; в кодовой базе вообще нет ни одного `DELETE FROM liner_dimension_option`). В отличие от прецедента добора «ТС» (где старое значение 2170 совпало с одним из значений новой бизнес-таблицы), здесь ни одно из новых значений (2250/2400/2700) не совпадает со старым 2100 — переиспользуется именно строка (её `id`), а не значение; какой из четырёх диапазонов получает переиспользованную строку, значения не имеет (id — суррогатный ключ, наружу не виден). Добавляются 3 новые строки на каждый из 2 кодов: value=2400 (диапазон [2150, 2250]), value=2700 (диапазон-точка [2300, 2300]), value=2700 (диапазон [2350, 2550]).

**Альтернатива (отклонена):** `DELETE` старой строки + `INSERT` четырёх новых. Отклонено — потребовало бы сначала обнулить `configuration_price.length_option_id` (см. Decision 5), чтобы не нарушить FK, добавляя порядковую зависимость между миграциями без выигрыша; переиспользование строки через `UPDATE` не создаёт такой зависимости и следует уже установленному в проекте прецеденту.

### 4. Расширение уникального индекса `uk_liner_dimension_option_door_casing_type`

Как и в `link-dobor-ts-length-to-leaf-height` (Decision 4), но для владельца `door_casing_type_id`:
```sql
DROP INDEX uk_liner_dimension_option_door_casing_type;
CREATE UNIQUE INDEX uk_liner_dimension_option_door_casing_type ON liner_dimension_option (
  door_casing_type_id, liner_dimension_type_id, value, COALESCE(min_value, -1), COALESCE(max_value, -1)
) WHERE door_casing_type_id IS NOT NULL;
```
Нужно, чтобы разрешить две строки с одинаковым value=2700 для одного и того же `door_casing_type_id`, но с непересекающимися диапазонами (2300–2300 и отдельно 2350–2550).

Для desktop-профиля (H2) — параллельная миграция по образцу `changes-desktop-overrides/0065-frame-extensions-type-length-range-index.yaml`: колонки `door_casing_type_key`/`min_value_norm`/`max_value_norm` уже существуют (добавлены `changes-desktop-overrides/0006-door-configuration-catalog.yaml` и `.../0062-frame-type-height-range-index.yaml` для всей таблицы `liner_dimension_option`) — новой миграции достаточно `DROP`+`CREATE UNIQUE INDEX` на уже существующих generated-колонках.

### 5. Цена наличников «Модо»/«Онда» перестаёт зависеть от length_option_id

По той же причине и тем же приёмом, что и в `link-dobor-ts-length-to-leaf-height` (Decision 3): `UPDATE configuration_price SET length_option_id = NULL WHERE door_casing_type_id IN (id DCT-003, id DCT-004)`. `matchesDimension`/`findMostSpecificPrice` не меняются — `priceOption == null` уже трактуется как «подходит для любого выбора».

### 6. Frontend: переименование `doborTsLengthOptions`/`doborTsCoversHeight` в осе-нейтральные имена

Обе функции уже не содержат ничего, специфичного для добора «ТС» — только фильтр по `LENGTH_TYPE_CODE` и вызов `dimensionRangeCoversLeafHeight`. Переименовываются в `lengthRangeOptions`/`lengthRangeCoversHeight` и переиспользуются в двух местах: для добора «ТС» (существующие вызовы, гейт — `DOBOR_TS_FRAME_EXTENSIONS_TYPE_CODES`) и для наличников «Модо»/«Онда» (новые вызовы, гейт — `LENGTH_RANGE_DOOR_CASING_TYPE_CODES`).

**Альтернатива (отклонена):** завести `modoOndaLengthOptions`/`modoOndaCoversHeight` — копии добор-ТС-функций под новым именем. Отклонено — тело функций идентично, разница есть только в наборе кодов, который и так передаётся не в эти функции, а используется вызывающим кодом для решения, какую ветку рендера показывать; копирование было бы чистым дублированием без единой содержательной разницы.

Рендер группы «Длина» получает третью ветку (после `frameExtensions`): `step.key === 'doorCasing' && LENGTH_RANGE_DOOR_CASING_TYPE_CODES.includes(component.type.code)` → `lengthRangeOptions(...)`. `buildCascadeSteps` получает третий аналогичный блок фильтрации для `key === 'doorCasing'`. Сброс `selection.doorCasing.lengthOptionId` при смене высоты полотна добавляется в `updateSelection` рядом с уже существующими условными сбросами.

### 7. Тестовая инфраструктура: `TestEntities.doorCasingType(id, code)`

По образцу `TestEntities.frameExtensionsType(id, code)` (введённой в `link-dobor-ts-length-to-leaf-height`) добавляется перегрузка для `DoorCasingType` — без неё тесты не могут создать компонент с кодом `DCT-003`/`DCT-004` для проверки диапазонной логики.

## Risks / Trade-offs

- **[Риск]** Обнуление `length_option_id` в существующих строках `configuration_price` наличников «Модо»/«Онда» и переиспользование существующей строки `liner_dimension_option` — изменение данных, вставленных другой (уже применённой) миграцией `0012`. → Новые `UPDATE`-changeset'ы, не редактирующие сам `0012`; тот же приём уже применён дважды (`link-dobor-ts-length-to-leaf-height`, `general-fixes-leaf-step-colour-casing-length`).
- **[Риск]** Рефакторинг общего блока «обязательная длина + диапазон» в приватный метод (Decision 1) технически меняет код, обслуживающий уже работающую логику добора «ТС», хотя и без изменения поведения. → Митигируется тем, что рефакторинг чисто механический (условие идентично, тела блоков идентичны за вычетом кода/сообщения/набора), и существующие тесты добора «ТС» (`DoorConfigurationPricingServiceTest`) проверяют его поведение без изменений в самих тестах — регрессия обнаружилась бы компиляцией/поведением этих тестов.
- **[Риск]** Как и для добора «ТС», при несовместимой высоте полотна и отсутствии альтернативного наличника шаг «Наличник» может исчезнуть из UI вместо явного сообщения о недоступности. → Тот же принятый риск: backend в любом случае не даст завершить такую конфигурацию расчётом.
- **[Риск]** Переименование `doborTsLengthOptions`/`doborTsCoversHeight` затрагивает уже существующий код добора «ТС» (два вызова). → Низкий риск: чисто механическое переименование, сигнатуры и поведение не меняются; проверяется сборкой (`npm run build`).

## Migration Plan

1. Liquibase-миграция №1: `DROP INDEX` + `CREATE UNIQUE INDEX uk_liner_dimension_option_door_casing_type` с колонками диапазона (Decision 4); параллельная desktop-миграция для H2.
2. Liquibase-миграция №2: данные диапазона длины наличников «Модо»/«Онда» — `UPDATE` существующей строки (value 2100→2250, диапазон) + `INSERT` 6 новых строк (value=2400, value=2700×2, на каждый из 2 кодов) (Decision 3).
3. Liquibase-миграция №3: `UPDATE` строк `configuration_price` наличников «Модо»/«Онда» — `length_option_id = NULL` (Decision 5).
4. Backend-код: вынесение `requireLengthWithinLeafRange` (Decision 1), константа `LENGTH_RANGE_DOOR_CASING_TYPE_CODES`, новый вызов для `DoorCasingType` в общей ветке `addComponentIfPresent`.
5. Тестовая инфраструктура: `TestEntities.doorCasingType(id, code)` (Decision 7); новые тесты в `DoorConfigurationPricingServiceTest` по аналогии с тестами добора «ТС» (в диапазоне / вне диапазона / без высоты полотна / разрыв между диапазонами / точка 2300 / без выбранной длины / другой наличник не затронут / цена не зависит от длины); регрессионная проверка, что существующие тесты добора «ТС» по-прежнему проходят после рефакторинга (Decision 1).
6. Frontend-код: переименование `doborTsLengthOptions`/`doborTsCoversHeight` → `lengthRangeOptions`/`lengthRangeCoversHeight` (Decision 6), константа `LENGTH_RANGE_DOOR_CASING_TYPE_CODES`, изменение рендера группы «Длина» для doorCasing, сброс выбора при смене высоты полотна, расширение `buildCascadeSteps`.
7. Проверка: `npm run build` (из `frontend/`); backend-тесты не запускаются (см. ограничение пользователя в предыдущих сессиях — тесты сейчас сломаны); ручная проверка в браузере — выбор наличника «Модо»/«Онда» с высотой полотна в разных диапазонах, проверка исчезновения/появления в каскаде и сброса выбора длины при смене высоты полотна.

Откат — стандартный: новые миграции — новые changeset'ы, откат через Liquibase `rollback`, если потребуется; данные и код валидации наличников «Модо»/«Онда» откатываются вместе, не затрагивая добор «ТС» и остальные компоненты (рефакторинг общего метода откатывается вместе со всем изменением, поведение добора «ТС» при этом не меняется ни в исходном, ни в откаченном состоянии).
