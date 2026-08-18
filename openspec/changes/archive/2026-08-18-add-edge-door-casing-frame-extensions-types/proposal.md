## Why

`item`, `leaf` и `frame` уже классифицируются через собственные справочники (`item_type`, `leaf_type`, `frame_type`), но `edge`, `door_casing` и `frame_extensions` такой классификации не имеют — у них нет атрибута, который бы описывал их разновидность (например, тип кромки или тип добора). Без этого их нельзя различать в бизнес-логике калькулятора так же, как уже различаются leaf и frame.

## What Changes

- Добавить три новые справочные таблицы: `edge_type`, `door_casing_type`, `frame_extensions_type` — по образцу уже существующих семи справочников (`id` identity PK, `name` VARCHAR NOT NULL, `code` VARCHAR NOT NULL UNIQUE).
- Добавить FK-колонки `edge_type_id` (на `edge`), `door_casing_type_id` (на `door_casing`), `frame_extensions_type_id` (на `frame_extensions`) — все `NOT NULL`, `ON DELETE RESTRICT`. Форма соответствует уже принятому в проекте паттерну для type-колонок (`item.item_type_id`, `leaf.leaf_type_id`, `frame.frame_type_id`, `leaf_mirror.mirror_type_id`/`leaf_side_id`) — обязательная ссылка, защищённая от удаления используемого типа.
- Требования `item-configuration-schema`, описывающие состав `edge`, `door_casing`, `frame_extensions`, дополняются обязательным типом. Требования «Защита справочников от удаления» и «Атрибуты справочных значений» расширяются тремя новыми справочниками.
- Область изменения — только Liquibase-схема: JPA-сущности, репозитории, сервисы, REST-эндпоинты не добавляются (как и во всех предыдущих изменениях этой капабилити). Начальные данные для новых справочников не добавляются — таблицы создаются пустыми, заполняются отдельно (как и с уже существующими семью справочниками).

## Capabilities

### New Capabilities
(нет)

### Modified Capabilities
- `item-configuration-schema`: `edge`, `door_casing`, `frame_extensions` получают обязательный тип через новые справочники `edge_type`, `door_casing_type`, `frame_extensions_type`; требования о защите справочников от удаления и об атрибутах справочных значений расширяются на эти три новые таблицы.

## Impact

- `backend/src/main/resources/db/changelog/` — новый файл changeset (например, `changes/0005-edge-door-casing-frame-extensions-types.yaml`), подключаемый из `db.changelog-master.yaml` после `changes/0004-item-configuration-reference-data.yaml`. Уже применённые предыдущие changeset'ы не редактируются.
- Прикладной backend-код и frontend-код этим изменением не затрагиваются.
