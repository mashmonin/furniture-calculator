## Why

Изменение `add-item-schema` намеренно создало только структурный каркас конфигурации item (первичные и внешние ключи, без описательных столбцов) — исходная Chen-диаграмма не содержала атрибутов. Справочные таблицы (`item_type`, `leaf_type`, `mirror_type`, `leaf_side`, `frame_type`, `liner_dimension_type`, `colour_type`) сейчас пустые (только `id`), и их нечем заполнять: нет столбца, в котором хранилось бы человекочитаемое название или код значения. Аналогично `liner_dimensions` не хранит само числовое значение размера, а `colours` не хранит ничего, что описывало бы цвет. Без этих столбцов справочники и таблицы общих значений нельзя наполнить реальными данными, и бизнес-логика калькулятора не может на них опереться.

## What Changes

- Добавить `name` (varchar, `NOT NULL`) и `code` (varchar, `NOT NULL`, `UNIQUE`) в каждую из семи справочных таблиц: `item_type`, `leaf_type`, `mirror_type`, `leaf_side`, `frame_type`, `liner_dimension_type`, `colour_type`.
- Добавить в `liner_dimensions` столбцы `value` (decimal, `NOT NULL`) — числовое значение размера — и `is_standard` (boolean, `NOT NULL`) — признак того, что это стандартный (каталожный), а не произвольный размер. Отдельный столбец с типом размера не добавляется: тип уже выражен существующим `liner_dimension_type_id`.
- Добавить в `colours` столбцы `name` (varchar, `NOT NULL`) — название цвета — и `ral_code` (varchar, нулевой допустим) — каталожный код цвета (например, RAL).
- Новые столбцы добавляются только к таблицам, уже созданным в `add-item-schema`; изменение не добавляет и не меняет FK, `item`, `leaf`, `frame`, `edge`, `door_casing`, `frame_extensions` и `leaf_mirror` новых столбцов не получают — их тип и описательные данные уже полностью выражены существующими `*_type_id`, `liner_dimensions_id` и `colours_id` FK.
- Область изменения — только Liquibase-схема: JPA-сущности, репозитории, сервисы и REST-эндпоинты этим изменением не добавляются (как и в `add-item-schema`). Рекомендация по устройству прикладного слоя (общий базовый класс/репозиторий для однотипных справочников) фиксируется в `design.md` как ориентир для последующего прикладного изменения, но не реализуется здесь.

## Capabilities

### New Capabilities
(нет)

### Modified Capabilities
- `item-configuration-schema`: справочные, размерные и цветовые сущности получают описательные столбцы (name/code у справочников, value/is_standard у liner_dimensions, name/ral_code у colours), необходимые для наполнения их реальными данными.

## Impact

- `backend/src/main/resources/db/changelog/` — новый файл changeset (`changes/0002-item-attribute-columns.yaml` или аналогичное имя) и обновление `db.changelog-master.yaml` для его подключения. Уже применённый `changes/0001-item-schema.yaml` не редактируется.
- Прикладной backend-код (сущности/репозитории/контроллеры) и frontend-код этим изменением не затрагиваются.
