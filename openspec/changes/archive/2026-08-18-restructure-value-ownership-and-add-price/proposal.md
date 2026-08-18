## Why

Текущая схема допускает только одну запись `liner_dimensions` и одну `colours` на компонент (`leaf.liner_dimensions_id`, `frame.liner_dimensions_id` и т. д. — единственная FK). Это не позволяет описать реальный случай: у одного `frame` (короба) есть длина, ширина и высота одновременно — три независимых размера, а не один. Та же потребность в множественности актуальна для `colours`. Кроме того, в модели пока нет цены: у каждого элемента конфигурации есть фактическая розничная и дилерская цена, а сущности для этого нет.

## What Changes

- **BREAKING**: `liner_dimensions_id` убирается с `leaf`, `frame`, `edge`, `door_casing`, `frame_extensions`, `leaf_mirror`. Вместо этого `liner_dimensions` получает nullable FK-колонки на каждого возможного владельца (`leaf_id`, `frame_id`, `edge_id`, `door_casing_id`, `frame_extensions_id`, `leaf_mirror_id`) с `CHECK`, что заполнена ровно одна. Теперь у одного компонента может быть несколько строк `liner_dimensions` (например, у `frame` — длина, ширина и высота как три отдельные строки), но строка `liner_dimensions` принадлежит ровно одному компоненту (переиспользование между разными компонентами больше не поддерживается).
- **BREAKING**: `colours_id` убирается с `leaf`, `frame`, `edge`, `door_casing`, `frame_extensions` (у `leaf_mirror` этой колонки не было). Симметрично `liner_dimensions`, `colours` получает nullable FK-колонки владельцев (`leaf_id`, `frame_id`, `edge_id`, `door_casing_id`, `frame_extensions_id`) с тем же `CHECK` на ровно одну заполненную.
- Добавляется новая сущность `price` (`id`, `retail_price` decimal, `dealer_price` decimal, обе `NOT NULL`) с nullable FK-колонками владельцев на все семь структурных сущностей (`item_id`, `leaf_id`, `frame_id`, `edge_id`, `leaf_mirror_id`, `door_casing_id`, `frame_extensions_id`) и тем же `CHECK` на ровно одну заполненную. В отличие от `liner_dimensions`/`colours`, ограничение «не более одной строки на владельца» не вводится — у элемента допустимо несколько строк `price` (например, история изменения цены).
- Требования капабилити `item-configuration-schema`, описывающие состав `leaf`, `frame`, `edge`, `door_casing`, `frame_extensions`, `leaf_mirror` («ссылается ровно на одну запись dimensions и ровно на один colour»), переформулируются: «может быть связан с одной или несколькими записями dimensions/colour, каждая из которых принадлежит только ему». Требование «Общие каталоги dimensions и colour» (переиспользуемость) удаляется — оно противоречит новой модели владения.
- Область изменения — только Liquibase-схема: JPA-сущности, репозитории, сервисы, REST-эндпоинты не добавляются (как и в предыдущих изменениях этой капабилити).

## Capabilities

### New Capabilities
(нет)

### Modified Capabilities
- `item-configuration-schema`: связь компонентов с `liner_dimensions`/`colours` меняется с «компонент → одна общая строка» на «строка принадлежит ровно одному компоненту, компонент может иметь несколько строк»; добавляется цена (`price`) как сущность, привязываемая к любой из семи структурных сущностей (включая `item`).

## Impact

- `backend/src/main/resources/db/changelog/` — новый файл changeset (например, `changes/0003-value-ownership-and-price.yaml`), подключаемый из `db.changelog-master.yaml`. Уже применённые `0001-item-schema.yaml` и `0002-item-attribute-columns.yaml` не редактируются.
- Ломающее изменение формы данных: любой будущий прикладной код, ожидающий единственную `liner_dimensions_id`/`colours_id` на компоненте, должен будет работать со списком строк-владений. На момент этого изменения такого кода ещё нет (JPA-слой не создавался).
