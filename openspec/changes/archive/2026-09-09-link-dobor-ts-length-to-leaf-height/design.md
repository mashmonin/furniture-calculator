## Context

См. proposal.md — «Why» и «What Changes». Ключевые технические факты о текущем состоянии, важные для решения:

1. **Механизм диапазонов (min_value/max_value на liner_dimension_option) уже существует и уже обобщён на несколько владельцев.** Он был введён в `link-edge-height-to-leaf-height` (кромка, ось «Высота», диапазон [min_value, value]) и расширен в `link-frame-neo-height-to-leaf-height`/`link-komplanar-height-to-leaf-height` (короб, та же ось «Высота», диапазон [min_value, max_value] с фолбэком на value). Ни схема БД, ни спецификация `door-configuration-catalog` для этого расширения меняться не должны — там уже написано «например, высоты полотна» и «например, для одного и того же frame_type», без ограничения осью или типом владельца.
2. **Валидирующий метод в бэкенде (`validateHeightWithinLeafRange`) сегодня жёстко гейтит себя по оси «Высота»** (`HEIGHT_TYPE_CODE.equals(heightOption.getLinerDimensionType().getCode())`, иначе no-op). Оба существующих вызова (кромка, короб) передают именно опцию высоты, поэтому этот гейт до сих пор ни разу не проверялся на опции другой оси. Для добора «ТС» нужна опция оси «Длина» (`DT-001`) — с текущим гейтом метод её молча пропустит.
3. **Добор «ТС» (frame_extensions_type с кодом FET-004–FET-007) сегодня имеет ровно одну строку liner_dimension_option на ось «Длина»** (value=2170, без min_value/max_value) и ровно одну строку `configuration_price`, у которой `length_option_id` указывает именно на эту строку (миграция 0014). Ветка `addComponentIfPresent` для `FrameExtensionsType` не имеет специального `instanceof`-блока — она проходит через общий путь (как leaf/edge/doorCasing), где `lengthOption` ищется через `validatedDimensionOption` и передаётся в `findMostSpecificPrice` для сопоставления с `configuration_price.length_option_id`.
4. **Цена добора «ТС» формально зависит от length_option_id**, хотя фактически business-намерение (как и для короба «НЕО»/«Компланар» по высоте) — что размер только гейтит допустимость выбора, а не меняет цену. Сейчас это незаметно, потому что опция длины ровно одна; при добавлении новых опций длины (2400 ×2, 2700) без изменения `configuration_price` добор «ТС» перестал бы находить цену для любой длины, кроме 2170.
5. Frontend уже имеет обобщённый geometry-хелпер `frameHeightRangeOptionCoversLeafHeight(option, leafHeightValue)` (App.tsx), который не завязан на конкретную ось — сравнивает `min_value`/`max_value ?? value` с высотой полотна независимо от того, что именно описывает опция (высоту короба или, в нашем случае, длину добора). Его вызывающие функции (`frameHeightRangeOptions`, `frameCoversHeight`) сами фильтруют по `option.dimensionType.code === HEIGHT_TYPE_CODE` — для добора «ТС» нужен тот же геометрический хелпер, но с фильтром по `LENGTH_TYPE_CODE`.
6. `frameExtensions` уже участвует и в `COMPONENT_ORDER`/`CASCADE_ORDER`, и в двухпроходном построении `buildCascadeSteps` с уже вычисленным `leafHeightValue` (нужен только для шага `frame` сегодня) — добавление аналогичной фильтрации для шага `frameExtensions` не требует новой архитектуры, только расширения уже существующего условия.
7. **Добор «ТС» структурно недоступен для реверсивных конфигураций — как и короб «НЕО».** Во всех миграциях, где вообще упоминается добор «ТС» (`0015`, `0016`, `0043`, `0046`, `0049`), он вставляется исключительно в паре с `frame_type.code = 'FT-003'` («НЕО») — ни одной строки `door_configuration` с `frame_extensions_type_id` из FET-004–FET-007 и другим frame_type не существует. При этом короб «НЕО» ни разу не участвует в реверсивных миграциях (`0054`–`0059` заводят реверс только для «Фантом» FT-001 и «Компланар» FT-002/бывшего FT-004) — то есть для FT-003 нет ни одной строки с is_reverse = true. Следствие: комбинация «добор «ТС» + is_reverse = true» в каталоге не встречается и появиться не может, пока короб «НЕО» не получит реверс-исполнение (а это отдельное, более крупное изменение, не в объёме этой задачи). Поэтому эта задача не проверяет и не описывает поведение для реверса отдельно — оно не отличается от общего пути (`addComponentIfPresent` передаёт `applyReverseSurcharge=false` для frameExtensions безусловно, а новая ветка проверки добора «ТС» не читает `configuration.isReverse()`), но тестировать эту недостижимую комбинацию нет смысла.

## Goals / Non-Goals

**Goals:**
- Ограничить выбор длины добора «ТС» (FET-004–FET-007) диапазоном высоты полотна, не затрагивая добор «КОМПЛАНАР» (FET-008–FET-013) и другие компоненты.
- Обобщить `validateHeightWithinLeafRange` на произвольную ось размера без изменения поведения существующих вызовов (кромка, короб «НЕО»/«Компланар»).
- Сделать цену добора «ТС» независимой от выбранной длины — длина только гейтит допустимость выбора, симметрично тому, как высота короба «НЕО»/«Компланар» не влияет на его цену.

**Non-Goals:**
- Не меняется цена добора «ТС» по сравнению с текущей (розничная/дилерская цена за штуку остаётся прежней для каждого из 4 кодов) — меняется только то, от чего эта цена формально зависит.
- Не переносится диапазонное ограничение на добор «КОМПЛАНАР» — для него сегодня нет такого бизнес-требования.
- Не создаются новые типы добора и не меняется механизм «количество» (quantity), уже существующий для frameExtensions.
- Не меняется схема таблиц `liner_dimension_option`/`configuration_price` — используются уже существующие колонки `min_value`/`max_value`/`length_option_id`.

## Decisions

### 1. Обобщение `validateHeightWithinLeafRange` по оси, а не только по формуле границ

Внутренний гейт `if (!HEIGHT_TYPE_CODE.equals(...)) return;` заменяется параметром — ожидаемым кодом оси, который передаёт вызывающая сторона:

```java
private void validateHeightWithinLeafRange(
        String componentName, String expectedDimensionTypeCode, LinerDimensionOption dimensionOption,
        BigDecimal leafHeightValue) {
    if (!expectedDimensionTypeCode.equals(dimensionOption.getLinerDimensionType().getCode())) {
        return;
    }
    // остальное тело — без изменений: min/max-фолбэк на value, сравнение с leafHeightValue, 400 при выходе за диапазон
}
```

Существующие вызовы (кромка, короб «НЕО»/«Компланар») передают `HEIGHT_TYPE_CODE` явно — их поведение не меняется. Новый вызов для добора «ТС» передаёт `LENGTH_TYPE_CODE`.

**Альтернатива (отклонена):** завести отдельный одноимённый метод `validateLengthWithinLeafRange`, дублирующий тело. Отклонено — тело метода (фолбэк min/max→value, сравнение с высотой полотна, текст ошибки) уже одинаково подходит для любой оси; дублирование увеличило бы риск рассинхронизации при будущих правках (как уже произошло один раз при обобщении под max_value в `link-frame-neo-height-to-leaf-height`).

**Альтернатива (отклонена):** убрать гейт по оси совсем, доверившись вызывающей стороне. Отклонено — минимальный readable safety-net дешевле, чем риск случайно передать не ту опцию в будущем вызове; передача ожидаемого кода явно документирует намерение каждого вызова, как уже делает `HEIGHT_RANGE_FRAME_TYPE_CODES`/`NEO_FRAME_TYPE_CODE` для короба.

### 2. Проверка гейтится набором кодов добора «ТС», а не структурой данных

Аналогично `HEIGHT_RANGE_FRAME_TYPE_CODES` для короба, вводится `DOBOR_TS_FRAME_EXTENSIONS_TYPE_CODES = Set.of("FET-004", "FET-005", "FET-006", "FET-007")`. В общей (не-`FrameType`) ветке `addComponentIfPresent`, после вычисления `lengthOption`, добавляется:

```java
if (type instanceof FrameExtensionsType frameExtensionsType && DOBOR_TS_FRAME_EXTENSIONS_TYPE_CODES.contains(frameExtensionsType.getCode())) {
    if (selection.lengthOptionId() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "для добора «ТС» необходимо выбрать длину");
    }
    if (lengthOption != null) {
        validateHeightWithinLeafRange(componentName, LENGTH_TYPE_CODE, lengthOption, leafHeightValue);
    }
}
```

Именно по коду типа (явный бизнес-ключ), а не «если у опции заполнены границы» — та же причина, что и в `link-frame-neo-height-to-leaf-height` (Decision 3): делает намерение читаемым и не затрагивает добор «КОМПЛАНАР», даже если для него в будущем появятся строки с диапазоном длины без такого намерения.

### 3. Цена добора «ТС» перестаёт зависеть от length_option_id

4 существующие строки `configuration_price` для FET-004–FET-007 получают `length_option_id = NULL` (новый UPDATE-changeset, не редактирующий уже применённую миграцию 0014). Поскольку `matchesDimension(priceOption, selected)` уже трактует `priceOption == null` как «подходит для любого выбора» (см. код `findMostSpecificPrice`/`matchesDimension` — без изменений), это не требует правок в `DoorConfigurationPricingService`: `lengthOption`, вычисленный и провалидированный на диапазон, по-прежнему передаётся в `findMostSpecificPrice` как раньше, но теперь ни на что не влияет при сопоставлении цены.

Без этого шага добавление новых опций длины (2400 ×2, 2700) без соответствующих строк `configuration_price` привело бы к тому, что выбор любой длины, кроме 2170, возвращал бы «цена не найдена» для добора «ТС» — притом что бизнес-требование касается только допустимости длины, а не цены.

**Альтернатива (отклонена):** продублировать строку `configuration_price` на каждую новую опцию длины (как сделано для `configuration_price` разных `frame_extensions_type_id` в 0014/0019). Отклонено — по духу симметрично Non-Goal короба «НЕО»/«Компланар» («выбор высоты не влияет на цену») и не создаёт 3 лишних дублирующих строки на каждый из 4 кодов (12 новых строк), которые пришлось бы поддерживать синхронно при любом будущем изменении цены добора «ТС».

### 4. Расширение уникального индекса `uk_liner_dimension_option_frame_extensions_type`

Как и в `link-frame-neo-height-to-leaf-height` (Decision 4), но для владельца `frame_extensions_type_id`:

```sql
DROP INDEX uk_liner_dimension_option_frame_extensions_type;
CREATE UNIQUE INDEX uk_liner_dimension_option_frame_extensions_type ON liner_dimension_option (
  frame_extensions_type_id, liner_dimension_type_id, value, COALESCE(min_value, -1), COALESCE(max_value, -1)
) WHERE frame_extensions_type_id IS NOT NULL;
```

Нужно, чтобы разрешить две строки с одинаковым value=2400 для одного и того же frame_extensions_type_id, но с непересекающимися диапазонами (2150–2250 и отдельно 2300).

Для desktop-профиля (H2) — параллельная миграция по образцу `changes-desktop-overrides/0062-frame-type-height-range-index.yaml`: колонки `min_value_norm`/`max_value_norm` уже существуют (добавлены той миграцией для всей таблицы `liner_dimension_option`, не только для frame_type), а колонка `frame_extensions_type_key` уже существует (добавлена в `changes-desktop-overrides/0006-door-configuration-catalog.yaml`) — новой desktop-миграции достаточно DROP + CREATE UNIQUE INDEX на уже существующих generated-колонках, без ALTER TABLE.

### 5. Данные: границы диапазона длины добора «ТС»

Одна новая миграция (после DROP/CREATE индекса) вставляет и обновляет данные для каждого из 4 кодов (FET-004–FET-007):
- UPDATE существующей строки value=2170 → min_value=1900, max_value=2100.
- INSERT value=2400, min_value=2150, max_value=2250.
- INSERT value=2400, min_value=2300, max_value=2300 (диапазон-точка, как у короба «НЕО»).
- INSERT value=2700, min_value=2350, max_value=2550.

Диапазоны 2600–2850 и 2900–2950 намеренно не получают строк — добор «ТС» для этих высот полотна недоступен (тот же приём, что и разрывы у короба «НЕО»/«Компланар»).

### 6. Frontend: переиспользование геометрии диапазона под ось «Длина»

`frameHeightRangeOptionCoversLeafHeight` уже не завязан на конкретную ось (сравнивает только числа), поэтому переименовывается в axis-нейтральное `dimensionRangeCoversLeafHeight` (используется и для высоты короба, и для длины добора «ТС» — обе вызывающие стороны сами фильтруют по нужной оси). Добавляются:
- `doborTsLengthOptions(component, leafHeightValue)` — по образцу `frameHeightRangeOptions`, фильтр по `LENGTH_TYPE_CODE`.
- `doborTsCoversHeight(component, leafHeightValue)` — по образцу `frameCoversHeight`, тот же фильтр.
- Константа `DOBOR_TS_FRAME_EXTENSIONS_TYPE_CODES = ['FET-004', 'FET-005', 'FET-006', 'FET-007']`.

Рендер группы «Длина» (сегодня общий для всех компонентов, включая frameExtensions) получает ветку: если `step.key === 'frameExtensions' && DOBOR_TS_FRAME_EXTENSIONS_TYPE_CODES.includes(component.type.code)` — использовать `doborTsLengthOptions`, иначе — как раньше, `component.dimensionOptions.filter(...LENGTH_TYPE_CODE)`.

`buildCascadeSteps` получает аналогичный второй фильтр для `key === 'frameExtensions'` (по образцу уже существующего фильтра для `key === 'frame'`), использующий `doborTsCoversHeight`. Сброс `selection.frameExtensions.lengthOptionId` при смене высоты полотна добавляется в `updateSelection` рядом с уже существующими сбросами (условие: резолвленный тип frameExtensions-компонента — код из набора добора «ТС»).

### 7. Тестовая инфраструктура: `TestEntities.frameExtensionsType(id, code)`

По образцу `TestEntities.frameType(id, code)` добавляется перегрузка, устанавливающая `code` через `ReflectionTestUtils` — без неё тесты не могут создать `FrameExtensionsType` с кодом `FET-004` и т.п. для проверки диапазонной логики (текущий `frameExtensionsType(id)` присваивает синтетический код вида `FrameExtensionsType-6`, не входящий в `DOBOR_TS_FRAME_EXTENSIONS_TYPE_CODES`).

## Risks / Trade-offs

- **[Риск]** Обнуление `length_option_id` в уже существующих строках `configuration_price` — изменение данных, вставленных другой (уже применённой) миграцией. → Это не редактирование самого changeset 0014 (его SQL не трогается), а новый UPDATE в новом changeset — соответствует правилу проекта «не редактировать применённые changeset'ы, только новые».
- **[Риск]** Как и в `link-frame-neo-height-to-leaf-height` (Decision 7), при несовместимой высоте полотна и отсутствии альтернативного добора шаг «Добор» может просто исчезнуть из UI вместо явного сообщения о недоступности. → Тот же принятый риск: backend (Decision 2) в любом случае не даст завершить такую конфигурацию расчётом; более точное «заблокированное» состояние шага вынесено за рамки этой доработки.
- **[Риск]** Переименование `frameHeightRangeOptionCoversLeafHeight` → `dimensionRangeCoversLeafHeight` затрагивает уже существующий код короба «НЕО»/«Компланар» (чисто механическое переименование двух вызовов), а не только новый код. → Низкий риск: сигнатура и поведение функции не меняются, меняется только имя; проверяется существующими тестами/сборкой (`npm run build`, `tsc`).
- **[Риск]** Расширение уникального индекса — `DROP INDEX` + `CREATE UNIQUE INDEX` на существующей таблице. → Как и в прецеденте: `liner_dimension_option` — небольшой справочник, простой краткий.

## Migration Plan

1. Liquibase-миграция №1 (PostgreSQL): `DROP INDEX` + `CREATE UNIQUE INDEX uk_liner_dimension_option_frame_extensions_type` с колонками диапазона (Decision 4); parallel desktop-override для H2 (context `desktop`/`!desktop`, по образцу 0062/0062-desktop).
2. Liquibase-миграция №2: данные диапазона длины добора «ТС» — UPDATE существующих 4 строк (value=2170) + INSERT 8 новых строк (value=2400 ×2, value=2700 ×1, на каждый из 4 кодов) (Decision 5).
3. Liquibase-миграция №3: UPDATE 4 строк `configuration_price` добора «ТС» — `length_option_id = NULL` (Decision 3).
4. Backend-код: обобщение `validateHeightWithinLeafRange` (Decision 1), новая ветка проверки для `FrameExtensionsType` в общей ветке `addComponentIfPresent` (Decision 2), константа `DOBOR_TS_FRAME_EXTENSIONS_TYPE_CODES`.
5. Тестовая инфраструктура: `TestEntities.frameExtensionsType(id, code)` (Decision 7); новые тесты в `DoorConfigurationPricingServiceTest` по аналогии с тестами короба «НЕО» (в диапазоне / вне диапазона / без высоты полотна / разрыв между диапазонами / точка 2300 / без выбранной длины / другой тип добора не затронут).
6. Frontend-код: `doborTsLengthOptions`, `doborTsCoversHeight`, переименование геометрического хелпера, изменение рендера группы «Длина» для frameExtensions, сброс выбора при смене высоты полотна, расширение `buildCascadeSteps`.
7. Проверка: `./gradlew test --tests "*DoorConfigurationPricingServiceTest*"` (из `backend/`); `npm run build` (из `frontend/`); ручная проверка в браузере — выбор добора «ТС» с высотой полотна в разных диапазонах (внутри, в разрыве, на границе 2300), проверка исчезновения/появления добора «ТС» в каскаде и сброса выбора длины при смене высоты полотна.

Откат — стандартный: новые миграции — новые changeset'ы, откат через Liquibase `rollback`, если потребуется; данные и код валидации добора «ТС» откатываются вместе, не затрагивая добор «КОМПЛАНАР» и остальные компоненты.
