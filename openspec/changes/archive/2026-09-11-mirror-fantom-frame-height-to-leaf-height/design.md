## Context

См. proposal.md — «Why» и «What Changes». Ключевые факты, установленные до принятия решений:

1. **Короб «Фантом» (`FT-001`) сегодня не имеет ни одной `liner_dimension_option`** ни на одной оси (высота, длина, толщина) — только `colour_option` (4 цвета) и `configuration_price`, ключуемая по цвету. Миграции `0051`–`0053`, `0056` (frame-конфигурации, кромки, цвета постов, реверс) ни разу не вставляют для `FT-001` строку `liner_dimension_option`. Шаг «Высота» для этого короба в текущем интерфейсе уже фактически скрыт — `OptionGroup` ничего не рендерит при пустом списке опций.
2. **Цена короба определяется только цветом.** `framePostPrice` вызывает `findMostSpecificPrice(frameType, null, null, null, colourOption)` — `null` передаётся явно вместо length/height/thickness для ЛЮБОГО короба, включая «НЕО»/«Компланар». Высота короба уже сегодня нигде не входит в подбор цены — это не меняется и для «Фантом».
3. **Существующий диапазонный механизм (`HEIGHT_RANGE_FRAME_TYPE_CODES` = `{FT-002, FT-003}`, `validateHeightWithinLeafRange`) неприменим напрямую** — он проверяет попадание в `[min_value, max_value]` выбранной каталожной `liner_dimension_option`; у «Фантом» такой опции нет и не будет (данные не добавляются, см. proposal.md). Нужна проверка другого типа — на точное числовое равенство, а не на диапазон.
4. **Механизм произвольного значения размера уже существует, но жёстко ограничен полотном.** `customLengthValueMm`/`customHeightValueMm` в `ComponentSelectionDto` сегодня допустимы только для `LeafType` — общая проверка в начале `addComponentIfPresent` (до ветки `FrameType`) отклоняет их 400 для любого другого компонента:
   ```java
   if ((selection.customLengthValueMm() != null || selection.customHeightValueMm() != null) && !(type instanceof LeafType)) {
       throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
               "произвольное значение размера допустимо только для компонента leaf, а не для " + componentName);
   }
   ```
   Это единственное существующее поле, пригодное для передачи «высоты короба «Фантом»» без ввода нового поля в `ComponentSelectionDto` и без миграции каталога — но требует точечного исключения именно для высоты и именно для кода `FT-001`.
5. **`resolveAxisSurchargeMultiplier`/`dimension_surcharge_rule` для наценки на нестандартный размер полотна вызываются только для `LeafType`** и не имеют отношения к этой задаче — высота короба «Фантом» не наценивается (см. факт 2), только валидируется на равенство.
6. **Высота полотна (`leafHeightValue`) уже вычисляется один раз в `calculate()`** до вызовов `addComponentIfPresent` — `leafHeightOption != null ? leafHeightOption.getValue() : leafSelection.customHeightValueMm()`. Это единое значение уже учитывает оба способа задания высоты полотна (каталожный вариант и произвольное значение) и передаётся во все компоненты без изменений — переиспользуется как есть.

## Goals / Non-Goals

**Goals:**
- Высота короба «Фантом» (`FT-001`) всегда в точности равна высоте полотна той же конфигурации, независимо от того, как эта высота полотна была задана (каталожный вариант или произвольное значение).
- Переиспользовать существующее поле `customHeightValueMm` и существующий механизм «leaf вычисляет `leafHeightValue` один раз» — не вводить новое поле API и не менять схему БД.
- Backend продолжает быть источником истины: даже если frontend не отправит корректное значение, эндпоинт расчёта стоимости сам отклонит несовпадение.

**Non-Goals:**
- Не вводится выбор высоты короба «Фантом» из каталожного списка — таких данных не появляется (см. proposal.md, «`door-configuration-catalog` не меняется»).
- Не меняется цена короба «Фантом» — как и раньше, она зависит только от цвета.
- Не переносится этот принцип на короба «НЕО»/«Компланар» или на любые другие компоненты — гейт по коду `frame_type`, набор `HEIGHT_MIRROR_FRAME_TYPE_CODES` содержит только `FT-001`.
- Не вводится наценка за размер короба «Фантом» — `dimension_surcharge_rule` эту величину не затрагивает.

## Decisions

### 1. Точечное исключение общей проверки «произвольное значение только для leaf», а не отдельное поле в ComponentSelectionDto

Общая проверка в начале `addComponentIfPresent` разбивается на две — по длине (без изменений, остаётся leaf-only для всех компонентов) и по высоте (с исключением для короба из `HEIGHT_MIRROR_FRAME_TYPE_CODES`):

```java
if (selection.customLengthValueMm() != null && !(type instanceof LeafType)) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "произвольное значение размера допустимо только для компонента leaf, а не для " + componentName);
}
boolean customHeightAllowedForFrame =
        type instanceof FrameType frameType && HEIGHT_MIRROR_FRAME_TYPE_CODES.contains(frameType.getCode());
if (selection.customHeightValueMm() != null && !(type instanceof LeafType) && !customHeightAllowedForFrame) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "произвольное значение размера допустимо только для компонента leaf, а не для " + componentName);
}
```

**Альтернатива (отклонена):** добавить в `ComponentSelectionDto` отдельное поле, например `mirroredHeightValueMm`, специфичное для этого правила. Отклонено — `customHeightValueMm` уже означает ровно «высота этого компонента, заданная значением, а не id каталожной опции»; для короба «Фантом» это буквально то же самое понятие (значения нет в каталоге совсем, а не «есть, но клиент предпочёл другое»), заводить второе поле с тем же смыслом было бы дублированием контракта API без выигрыша.

### 2. Гейт по коду `frame_type`, отдельная константа `HEIGHT_MIRROR_FRAME_TYPE_CODES`

По аналогии с `HEIGHT_RANGE_FRAME_TYPE_CODES` вводится `HEIGHT_MIRROR_FRAME_TYPE_CODES = Set.of("FT-001")` — раздельно от диапазонного набора (коробы не пересекаются). Явный бизнес-ключ вместо «эвристики по отсутствию каталожных опций высоты» — та же причина, что и во всех предыдущих `*_TYPE_CODES` наборах этого файла: устойчивость к будущим данным (если для «Фантом» когда-нибудь появятся каталожные опции по другой причине, это не должно молча включить диапазонную проверку или молча выключить зеркалирование).

### 3. Ветка `FrameType`: два независимых пути вместо расширения диапазонной проверки

```java
if (type instanceof FrameType frameType) {
    ColourOption colourOption = validatedColourOption(componentName, frameType, selection.colourOptionId());
    if (HEIGHT_MIRROR_FRAME_TYPE_CODES.contains(frameType.getCode())) {
        // heightOptionId для такого короба не может ссылаться на существующую опцию (их нет) —
        // validatedDimensionOption(...) уже отклонит любое переданное значение как непринадлежащее.
        validatedDimensionOption(componentName, frameType, selection.heightOptionId());
        requireHeightMirrorsLeaf(componentName, selection.customHeightValueMm(), leafHeightValue);
    } else {
        if (HEIGHT_RANGE_FRAME_TYPE_CODES.contains(frameType.getCode()) && selection.heightOptionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "для этого короба необходимо выбрать высоту");
        }
        LinerDimensionOption heightOption = validatedDimensionOption(componentName, frameType, selection.heightOptionId());
        if (heightOption != null && HEIGHT_RANGE_FRAME_TYPE_CODES.contains(frameType.getCode())) {
            validateHeightWithinLeafRange(componentName, HEIGHT_TYPE_CODE, heightOption, leafHeightValue);
        }
    }
    components.add(framePostPrice(componentName, frameType, colourOption));
    return;
}
```

Новый приватный метод, по образцу `requireLengthWithinLeafRange` (обязательность + совместимость в одном месте), но с проверкой на точное равенство вместо диапазона:

```java
private void requireHeightMirrorsLeaf(String componentName, BigDecimal customHeightValueMm, BigDecimal leafHeightValue) {
    if (customHeightValueMm == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "для этого короба необходимо выбрать высоту");
    }
    if (leafHeightValue == null || customHeightValueMm.compareTo(leafHeightValue) != 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "высота короба должна совпадать с высотой полотна");
    }
}
```

**Альтернатива (отклонена):** переиспользовать `validateHeightWithinLeafRange`, обернув точное значение в диапазон `[value, value]` (создав временный `LinerDimensionOption`-подобный объект или перегрузку, принимающую `min == max == customHeightValueMm`). Отклонено — `validateHeightWithinLeafRange` принимает `LinerDimensionOption` (сущность с `id`, использующимся в тексте ошибки) и написан специально для каталожных опций; городить фиктивную сущность или менять сигнатуру ради переиспользования пяти строк общей логики (`min == null` фолбэк, `max = maxValue ?? value`, сравнение) сложнее и менее читаемо, чем прямое сравнение на равенство в новом трёхстрочном методе.

### 4. Frontend: кнопка с единственным вариантом (как у диапазонных коробов), значение которого скопировано из высоты полотна

Первая версия этого решения отображала высоту короба «Фантом» как неинтерактивный текст и подставляла `customHeightValueMm` только при отправке запроса, в обход `selection`. Отклонено по прямой правке пользователя после ручной проверки: не соответствует UX прочих коробов, где даже единственно возможный вариант требует явного клика (см. существующее требование «Явный выбор варианта даже при единственной альтернативе»), и не заполняло `selection.frame` до расчёта — приводило к 400 «для этого короба необходимо выбрать высоту», если пользователь не понимал, что клик не требуется.

Вместо этого группа «Высота» для кода из `HEIGHT_MIRROR_FRAME_TYPE_CODES` рендерится как обычный `OptionGroup` с ровно одним синтетическим вариантом, значение которого скопировано из `leafHeightValue`; клик по этому варианту, как и по любому другому в приложении, обязателен и сохраняется в `selection.frame.customHeightValueMm`:

```tsx
{step.key === 'frame' && HEIGHT_MIRROR_FRAME_TYPE_CODES.includes(component.type.code) ? (
  <OptionGroup
    label="Высота"
    options={leafHeightValue === undefined ? [] : [{ id: MIRROR_HEIGHT_OPTION_ID, label: String(leafHeightValue) }]}
    selectedId={selection.frame.customHeightValueMm !== undefined ? MIRROR_HEIGHT_OPTION_ID : undefined}
    onChange={(id) =>
      updateSelection('frame', { customHeightValueMm: id === MIRROR_HEIGHT_OPTION_ID ? leafHeightValue : undefined })
    }
  />
) : (
  <OptionGroup
    label="Высота"
    options={(step.key === 'edge'
      ? edgeHeightOptions(component, leafHeightValue)
      : step.key === 'frame' && HEIGHT_RANGE_FRAME_TYPE_CODES.includes(component.type.code)
        ? frameHeightRangeOptions(component, leafHeightValue)
        : component.dimensionOptions.filter((option) => option.dimensionType.code === HEIGHT_TYPE_CODE)
    ).map((option) => ({ id: option.id, label: String(option.value) }))}
    selectedId={selection[step.key].heightOptionId}
    onChange={(id) => updateSelection(step.key, { heightOptionId: id, customHeightValueMm: undefined })}
  />
)}
```

`MIRROR_HEIGHT_OPTION_ID = -1` — синтетический id (по образцу уже существующего `NONE_OPTION_ID = 0` для варианта «без компонента»), который никогда не отправляется на backend: `onChange` транслирует его в `customHeightValueMm: leafHeightValue`, а не в `heightOptionId`. Список вариантов пуст, пока `leafHeightValue === undefined` — `OptionGroup` уже умеет ничего не рендерить при пустом списке (та же логика, что скрывает группу «Высота» диапазонных коробов до выбора высоты полотна).

Поскольку значение теперь хранится в `selection.frame.customHeightValueMm` обычным образом, `handleCalculate` не требует специального случая для frame — `selection[key]` отправляется как есть, как и для остальных компонентов.

Как и для `heightOptionId` диапазонных коробов (`HEIGHT_RANGE_FRAME_TYPE_CODES`), при смене высоты полотна `updateSelection` сбрасывает `next.frame.customHeightValueMm` для короба из `HEIGHT_MIRROR_FRAME_TYPE_CODES` — иначе после смены высоты полотна кнопка осталась бы «выбранной» со старым значением, не совпадающим с новой высотой полотна, и расчёт отклонялся бы 400 до повторного клика.

**Альтернатива (отклонена):** хранить зеркальное значение в `selection.frame.customHeightValueMm` через `updateSelection`, реагируя на изменение высоты полотна (как это сделано для сброса `heightOptionId` диапазонных коробов в `updateSelection`). Отклонено — это ввело бы лишнее хранимое состояние, которое обязано быть синхронизировано с `leafHeightValue` в каждый момент времени (иначе рассинхронизация даст неверное значение в запросе); вычисление на месте, при построении запроса, устраняет саму возможность рассинхронизации ценой одной дополнительной ветки в уже существующем тернарном выражении.

### 5. Что произойдёт, если высота полотна не выбрана

И backend (`requireHeightMirrorsLeaf` — `leafHeightValue == null` → 400), и frontend (условие `leafHeightValue !== undefined` перед отображением) одинаково трактуют отсутствие высоты полотна как «высота короба «Фантом» пока не определена» — блок не отображается на фронтенде и отклоняется явной ошибкой на бэкенде, если запрос всё же отправлен. Это симметрично уже принятому поведению для коробов «НЕО»/«Компланар» и для наличников/добора с диапазонным ограничением.

## Risks / Trade-offs

- **[Риск]** Разбиение единой проверки «произвольное значение только для leaf» на две (по длине и по высоте) — небольшое, но сквозное изменение существующего защитного кода, затрагивающее поведение для ВСЕХ компонентов, не только короба «Фантом». → Митигируется тем, что для любого компонента, кроме frame с кодом `FT-001`, поведение обеих проверок идентично прежнему единому условию (проверено регрессионными тестами на edge/doorCasing/frameExtensions/другие типы короба).
- **[Риск]** `requireHeightMirrorsLeaf` — третий по счёту специализированный метод «обязательность + совместимость» рядом с `validateHeightWithinLeafRange` и `requireLengthWithinLeafRange`, но не переиспользующий ни один из них напрямую (см. Decision 3, отклонённая альтернатива). → Осознанный трейд-офф: разная природа проверки (равенство, а не диапазон) делает переиспользование более сложным, чем дублированием; метод короткий (3 строки логики) и локальный.
- **[Риск]** Явный клик по единственному варианту высоты короба «Фантом» — дополнительное действие пользователя, которого не было бы при полностью автоматическом подходе (первая отклонённая версия Decision 4). → Осознанный трейд-офф, сделанный по прямой правке пользователя после ручной проверки: единообразие со всеми остальными шагами каскада (везде явный клик обязателен, даже при единственной альтернативе) важнее экономии одного клика, а неявное автозаполнение уже показало себя источником путаницы (400 без очевидной причины).

## Migration Plan

Миграций БД нет — изменение затрагивает только код (`DoorConfigurationPricingService`, `App.tsx`) и не меняет схему или данные.

1. Backend: константа `HEIGHT_MIRROR_FRAME_TYPE_CODES`; разделение проверки «произвольное значение только для leaf» (Decision 1); ветвление в блоке `FrameType` (Decision 3); новый метод `requireHeightMirrorsLeaf`.
2. Тесты backend: новые кейсы (высота короба «Фантом» совпадает/не совпадает/отсутствует/высота полотна не выбрана/heightOptionId отклоняется/цена не зависит от высоты); регрессия — существующие тесты диапазонной проверки коробов «НЕО»/«Компланар» и leaf-only проверки произвольных значений не меняют поведения.
3. Frontend: константы `HEIGHT_MIRROR_FRAME_TYPE_CODES` и `MIRROR_HEIGHT_OPTION_ID`; условный рендер `OptionGroup` «Высота» с единственным синтетическим вариантом вместо каталожного списка (Decision 4); сброс `customHeightValueMm` короба при смене высоты полотна в `updateSelection` (Decision 4).
4. Проверка: `npm run build` (из `frontend/`); backend — компиляция и точечный прогон `DoorConfigurationPricingServiceTest` (не полный `./gradlew test`, см. предыдущее ограничение пользователя); ручная проверка в браузере — выбор короба «Фантом», клик по единственному варианту высоты (равному высоте полотна, заданной стандартным вариантом и произвольным значением), сброс выбора высоты короба при смене высоты полотна, расчёт стоимости после повторного клика.

Откат — стандартный: новый код изолирован в новые/точечно изменённые ветки, поведение коробов «НЕО»/«Компланар» и всех прочих компонентов не меняется ни в исходном, ни в откаченном состоянии.
