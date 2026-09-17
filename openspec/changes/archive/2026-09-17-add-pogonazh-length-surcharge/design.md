## Context

См. proposal.md — «Why». Технический контекст для этого решения:

- `DoorConfigurationPricingService.addComponentIfPresent` — единая точка резолва цены каждого компонента (leaf/frame/edge/doorCasing/frameExtensions), переиспользуемая `calculate()`, `calculateForLeaf()`, `calculateForFrameGroup()` и `resolveSpecificationComponents()`. Наценка за погонаж должна встраиваться именно сюда, а не дублироваться в каждом вызывающем методе.
- Ветка `FrameType` внутри `addComponentIfPresent` сейчас всегда вызывает `findMostSpecificPrice(frameType, null, null, null, colourOption)` — высота никогда не участвует в поиске цены (`framePostPrice`). Для коробов «НЕО»/«Компланар» высота — каталожная `LinerDimensionOption` (`frameHeightOption`); для «Фантом» — `frameHeightMmOverride = leafHeightValue` (см. `HEIGHT_MIRROR_FRAME_TYPE_CODES`).
- Ветка для `DoorCasingType`/`FrameExtensionsType` уже ищет цену с учётом `lengthOption` через `findMostSpecificPrice`, но сама длина никогда не несёт наценки — `configuration_price` для конкретной длины хранит фиксированную цену.
- `dimension_surcharge_rule` (капабилити `dimension-surcharge-rules`) намеренно не привязан к владельцу — переиспользовать его для этой задачи нельзя, т.к. базовое значение разное для короба (2170), наличника (2250) и добора (2170), и наценка должна начисляться на разные компоненты, а не на leaf.
- `GET /api/pricing-surcharges` (`PricingSurchargesController` → `PricingSurchargesService` → `PricingSurchargesDto`) — уже существующий паттерн «справочник наценок для фронтенда»: фронтенд загружает его один раз при открытии страницы и сам локально пересчитывает разбивку (`computeSurchargeBreakdown` в `App.tsx`) — `calculate()` не возвращает структурированную разбивку наценок, только итоговую и базовую цену компонента.
- Отображение «базовая цена ≠ итоговая» на фронтенде (`item.baseRetailPrice !== item.retailPrice`) уже реализовано универсально для любого компонента разбивки, не только leaf — специальных изменений в этой части рендера не требуется.

## Goals / Non-Goals

**Goals:**
- Ввести новый справочник наценок за нестандартную длину/высоту погонажа, привязанный к владельцу.
- Применить эту наценку к цене frame/doorCasing/frameExtensions компонентов во всех точках, использующих `addComponentIfPresent` (основной расчёт, этапный расчёт короба, выгрузка спецификации).
- Отдать данные этого справочника через существующий `GET /api/pricing-surcharges` и показать применённую наценку на фронтенде рядом с соответствующим компонентом.

**Non-Goals:**
- Не меняется способ выбора каталожных опций длины/высоты (валидация диапазонов `HEIGHT_RANGE_FRAME_TYPE_CODES`/`LENGTH_RANGE_*` не меняется).
- Не вводится UI для редактирования правил наценки — данные правятся только через Liquibase-миграции, как и `dimension_surcharge_rule`/`configuration_price`.
- Наценка за погонаж не применяется к leaf и edge — эти компоненты вне скоупа.
- Не вводится единый механизм-правил-движок (Drools и т.п., упомянутый в комментарии к `REVERSE_SURCHARGE_MULTIPLIER`) — как и остальные наценки в этом сервисе сегодня, это временное решение на уровне сервиса и БД.

## Decisions

### Новая таблица `pogonazh_surcharge_rule`, привязанная к владельцу
Колонки: `id`, `frame_type_id` (nullable FK), `door_casing_type_id` (nullable FK), `frame_extensions_type_id` (nullable FK), `value NUMERIC NOT NULL`, `surcharge_percent NUMERIC NOT NULL`.

- CHECK-constraint: ровно один из трёх FK не NULL.
- Уникальность на (владелец, value) реализуется тремя частичными уникальными индексами (по одному на каждый FK, `WHERE <fk> IS NOT NULL`), а не одним составным индексом — в PostgreSQL составной UNIQUE не ловит дубликаты, если сравниваемые колонки NULL (NULL ≠ NULL), а тут два из трёх FK всегда NULL.

**Альтернатива (отклонена)**: расширить `dimension_surcharge_rule` необязательной ссылкой на владельца. Отклонено — этот справочник уже специфицирован (`dimension-surcharge-rules`) как принципиально независимый от владельца; смешение семантик («наценка на leaf» и «наценка на погонаж») в одной таблице усложнило бы обе капабилити и потребовало бы `MODIFIED` его собственной спеки, хотя её поведение не меняется.

**Альтернатива (отклонена)**: булев столбец `is_nonstandard_length` прямо на `liner_dimension_option`. Работает для короба «НЕО»/«Компланар», наличника и добора (все они — каталожные опции), но не работает для короба «Фантом», у которого высота — произвольное значение, а не строка `liner_dimension_option`. Единая таблица для всех четырёх случаев проще, чем столбец + отдельная таблица только для «Фантом».

### Отсутствие строки = базовое значение (без наценки)
Наценка применяется только тогда, когда для (владелец, value) есть строка `pogonazh_surcharge_rule`; отсутствие строки = 0%, а не ошибка. Это безопасно, потому что к моменту проверки погонажа значение уже прошло собственную валидацию по другому пути: каталожная опция короба/наличника/добора уже проверена `validatedDimensionOption`, а высота полотна (для «Фантом») уже провалидирована как существующий каталожный размер leaf или совпавшая с `dimension_surcharge_rule` leaf. Строгий отказ («значение неизвестно системе») здесь не нужен — в отличие от `resolveAxisSurchargeMultiplier` для leaf, где отсутствие правила действительно означает «фабрика не производит такой размер».

Практическое следствие: сид-данные должны перечислять только **нестандартные** значения (2400/2700/3000 и т.п. для короба/наличника/добора; 2050/2150/2200/2250/2300 для «Фантом») — явные строки для базовых значений (2170/2250/1900/1950/2000/2100) не нужны.

### Точки интеграции в `DoorConfigurationPricingService`
- Новый `PogonazhSurchargeRuleRepository` с методами `findByFrameTypeIdAndValue`, `findByDoorCasingTypeIdAndValue`, `findByFrameExtensionsTypeIdAndValue` (или один метод с owner-дискриминатором — на усмотрение реализации).
- В ветке `FrameType` (`addComponentIfPresent`): после вычисления `frameHeightOption`/`frameHeightMmOverride` определить эффективное значение высоты (`frameHeightOption.getValue()` либо `frameHeightMmOverride`) и искать правило по `frame_type_id`; применить найденный процент к результату `framePostPrice(...)` тем же способом, что и `applyPercentMultiplier` для leaf (округление после применения), получив `retailPrice`/`dealerPrice`, отличные от `baseRetailPrice`/`baseDealerPrice` (= результат `framePostPrice` без наценки).
- В общей ветке (`DoorCasingType`/`FrameExtensionsType`): после вычисления `lengthOption` искать правило по `door_casing_type_id`/`frame_extensions_type_id` и `lengthOption.getValue()`; применить найденный процент к цене, полученной из `findMostSpecificPrice`, **до** умножения на `quantity` (см. delta door-configuration-api — «Количество наличника и добора»), встроив это в `componentPriceFrom`/аналогичный путь.
- `ComponentPriceDto.baseRetailPrice`/`baseDealerPrice` уже существуют и не меняют форму — контракт ответа расширяется семантически (у большего числа компонентов base может отличаться от итога), а не по составу полей.
- `ResolvedComponent.surcharges: List<LeafPriceSurcharge>` сейчас заполняется только для leaf; для short-читаемости и чтобы не переименовывать существующий record без необходимости, для frame/doorCasing/frameExtensions можно оставить этот список пустым (спецификация не требует его для этих компонентов) — единственное новое требование к структуре ответа уже покрыто `baseRetailPrice`/`baseDealerPrice`.

### Расширение `GET /api/pricing-surcharges`
`PricingSurchargesDto` получает новое поле `pogonazhSurchargeRules: List<PogonazhSurchargeRuleDto>`, каждая запись — владелец (дискриминатор + id, либо три nullable id-поля по образцу структуры таблицы) + `value` + `surchargePercent`. Форма DTO — на усмотрение реализации (task); важно, чтобы фронтенд мог по (тип владельца, id владельца, value) найти нужную строку так же, как сейчас ищет `dimensionSurchargeRules` по (dimensionType.code, value).

### Фронтенд: разбивка по погонажу — отдельная от `computeSurchargeBreakdown`
Существующая `computeSurchargeBreakdown` жёстко специфична для leaf (принимает `leafSelection`, `mirrorFinishTypeId` и т.д.). Для короба/наличника/добора нужна отдельная функция (например, `computePogonazhSurcharge(pricingSurcharges, ownerType, ownerId, selectedValueMm)`), вызываемая до трёх раз — по одному разу для выбранной высоты короба и длины наличника/добора — и рендерящая процент рядом с соответствующим компонентом в разбивке результата (том же месте, где уже показывается `baseRetailPrice`/`baseDealerPrice` для этого компонента, см. `frontend/src/App.tsx` вокруг строки 1832).

## Risks / Trade-offs

- **[Риск]** Частичные уникальные индексы (по одному на FK) легче перепутать в Liquibase (`sql`-changeset) при копировании из существующих миграций с составным `CREATE UNIQUE INDEX`. → В tasks.md явно указать три отдельных `CREATE UNIQUE INDEX ... WHERE <fk> IS NOT NULL`.
- **[Риск]** «Отсутствие строки = база» означает, что для любого нового каталожного значения короба/наличника/добора, добавленного будущей миграцией, по умолчанию наценки не будет, пока кто-то явно не добавит строку `pogonazh_surcharge_rule`. → Это осознанный trade-off (см. «Decisions»); тот же риск существует у любого data-driven справочника наценок в проекте (например, у `mirror_finish_type`/`glazing_type`), и последствие (пропущенная наценка) гораздо безопаснее, чем ошибочный 400 на реальной, уже валидной конфигурации.
- **[Риск]** У короба «НЕО»/«Компланар» два разных каталожных диапазона могут делить одно и то же `value` (например, 2400 мм с диапазонами [2150,2250] и [2300,2300], см. миграцию 0063/0064) — правило `pogonazh_surcharge_rule` матчится по `value`, а не по диапазону, поэтому оба диапазона корректно получают одну и ту же наценку автоматически, без дублирования строк правила. Это не риск, а полезное следствие модели — зафиксировано здесь, чтобы не пытаться (при реализации) сделать матчинг по диапазону.

## Migration Plan

- Новый Liquibase changelog: `createTable pogonazh_surcharge_rule`, три частичных уникальных индекса, CHECK-constraint на «ровно один владелец», затем `INSERT` сид-данных для короба «НЕО»/«Компланар» (2400, 2700, 3000 → 30%), наличника (все 9 door_casing_type, 2400/2700/3000 → 30%), добора (все 10 frame_extensions_type, 2400/2700/2950/3000 → 30%, с точностью до реально существующих в каталоге значений каждого типа — см. миграции 0066/0070/0072/0074) и короба «Фантом» (2050/2150/2200/2250/2300 → 30%).
- Изменение аддитивно (новая таблица, новое поле ответа, компонент может начать иметь `baseRetailPrice != retailPrice` там, где раньше не мог) — откат стандартный для Liquibase (`rollback` на новый changeset), не требует данных-миграций существующих таблиц.
