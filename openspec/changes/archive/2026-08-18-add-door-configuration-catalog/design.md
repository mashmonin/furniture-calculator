## Context

См. proposal.md — Why. Технический контекст: схема БД управляется Liquibase (`backend/src/main/resources/db/changelog/`). Существующая схема `item-configuration-schema` (0001–0005) моделирует экземпляры компонентов (`leaf`, `frame`, `edge`, ...), где `liner_dimensions`/`colours`/`price` принадлежат ровно одному экземпляру через single-owner FK + CHECK-констрейнт (0003-value-ownership-and-price.yaml). Этот паттерн переиспользуется здесь, но с другим набором владельцев: не экземпляры компонентов, а сами справочники типов (`leaf_type`, `frame_type`, `edge_type`, `door_casing_type`, `frame_extensions_type`).

## Goals / Non-Goals

**Goals:**
- Хранить, какие сочетания типов компонентов согласованы с фабрикой (`door_configuration`).
- Хранить, какие размеры и цвета допустимы для каждого типа компонента (`liner_dimension_option`, `colour_option`), независимо от того, участвует ли этот тип в одной или нескольких `door_configuration`.
- Переиспользовать существующие справочники (`leaf_type`, `frame_type`, ..., `liner_dimension_type`, `colour_type`) без дублирования.

**Non-Goals:**
- Хранение фактических экземпляров/заказов — уже покрыто `item-configuration-schema`, не меняется.
- Проверка совместимости конкретных троек размеров (длина+высота+толщина) — по решению пользователя размеры независимы друг от друга.
- Ценообразование и логика расчёта.
- Java-слой приложения — в проекте пока нет кода, изменение затрагивает только схему БД.
- Привязка `door_configuration` к `item_type`: сейчас в системе только один `item_type` (МЕЖКОМНАТНЫЕ ДВЕРИ), явная связь добавит колонку без текущей пользы; при появлении второго item_type это можно добавить отдельным изменением.

## Decisions

**`door_configuration` — отдельная таблица с одним обязательным (`leaf_type_id`) и четырьмя опциональными FK, а не M:N-таблицы между парами типов.**
Почему: конфигурация — это цельная связка всех пяти типов сразу (подтверждено пользователем), а не независимые попарные связи. Одна строка = одна согласованная фабрикой комбинация, что проще читать и проверять на дубликаты, чем реконструировать комбинацию из нескольких M:N-связей.

**Зависимость "casing/extensions требует frame" — через CHECK, а не через отдельную таблицу "frame-конфигураций".**
`ALTER TABLE door_configuration ADD CONSTRAINT chk_door_configuration_casing_extensions_require_frame CHECK ((door_casing_type_id IS NULL OR frame_type_id IS NOT NULL) AND (frame_extensions_type_id IS NULL OR frame_type_id IS NOT NULL))`. Отражает существующий структурный факт (`door_casing`/`frame_extensions` в item-configuration-schema принадлежат `frame`, не `item`), не изобретая новую сущность.

**Уникальность полной комбинации типов — через выражение с COALESCE, а не обычный UNIQUE.**
В PostgreSQL обычный UNIQUE не считает две строки дубликатами, если хотя бы один столбец в обеих строках NULL (NULL ≠ NULL). Чтобы «дверь только с leaf_type X, без остального» нельзя было вставить дважды, уникальный индекс строится по выражениям:
```sql
CREATE UNIQUE INDEX uk_door_configuration_combination ON door_configuration (
  leaf_type_id,
  COALESCE(frame_type_id, 0),
  COALESCE(edge_type_id, 0),
  COALESCE(door_casing_type_id, 0),
  COALESCE(frame_extensions_type_id, 0)
);
```
0 как sentinel безопасен: id — `BIGINT autoIncrement`, реальные значения начинаются с 1.

**`liner_dimension_option` и `colour_option` — две отдельные таблицы, а не одна общая "type_option" с полиморфным полем "что именно опция" (размер или цвет).**
Разные наборы атрибутов (`value`+`is_standard` у размера, только ссылка на `colour_type` у цвета) и разная семантика: полиморфная таблица потребовала бы nullable-колонок обоих видов и усложнила бы CHECK-констрейнты без реальной экономии (структура и так уже переиспользует общий паттерн владения).

**Владение `liner_dimension_option`/`colour_option` — 5 nullable FK-колонок + CHECK "ровно один", как и в `door_configuration`/0003, а не 5 отдельных таблиц на каждого владельца.**
Тот же аргумент, что и в существующем паттерне: одна таблица проще в сопровождении, чем 5 почти идентичных.

**Партиционированные (partial) уникальные индексы для `liner_dimension_option` (owner, liner_dimension_type_id, value) и `colour_option` (owner, colour_type_id) — по одному индексу на владельца.**
Тот же COALESCE-подход, что и для `door_configuration`, применённый отдельно на каждую из 5 owner-колонок:
```sql
CREATE UNIQUE INDEX uk_liner_dimension_option_leaf_type ON liner_dimension_option (leaf_type_id, liner_dimension_type_id, value) WHERE leaf_type_id IS NOT NULL;
-- аналогично для frame_type_id, edge_type_id, door_casing_type_id, frame_extensions_type_id
```
Partial-индекс здесь проще, чем единый COALESCE-индекс по всем пяти owner-колонкам сразу, потому что дублирующиеся `(liner_dimension_type_id, value)` у одного и того же владельца — единственный сценарий, который нужно ловить; сравнивать между разными владельцами не нужно.

**FK на `*_type`-таблицы без `deleteCascade`.**
Тип не должен исчезать вместе со своими опциями/конфигурациями при случайном удалении — как и для существующих справочников (требование "Защита справочников от удаления" в item-configuration-schema); удаление типа, на который ссылается `door_configuration`/`liner_dimension_option`/`colour_option`, будет отклонено СУБД по умолчанию (FK без `ON DELETE CASCADE`).

## Risks / Trade-offs

- **[Риск]** Пять nullable FK-колонок повторяются в трёх новых таблицах (`door_configuration`, `liner_dimension_option`, `colour_option`) → **Митигация**: паттерн уже принят и обкатан в проекте (0003), локализован в миграциях, кода приложения пока нет.
- **[Риск]** COALESCE-индексы менее очевидны для читателя миграции, чем обычный UNIQUE → **Митигация**: комментарии в самой миграции и описание здесь, в design.md.
- **[Риск]** Без привязки `door_configuration` к `item_type` при появлении второго item_type в будущем придётся добавлять колонку отдельной миграцией → **Митигация**: осознанный компромисс (см. Non-Goals), сейчас в системе один item_type и добавлять неиспользуемую колонку преждевременно.
