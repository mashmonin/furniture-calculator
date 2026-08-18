## 1. Убрать старые FK владения на компонентах

- [x] 1.1 Changeset: dropColumn `liner_dimensions_id` с `leaf`, `frame`, `edge`, `door_casing`, `frame_extensions`, `leaf_mirror`
- [x] 1.2 Changeset: dropColumn `colours_id` с `leaf`, `frame`, `edge`, `door_casing`, `frame_extensions`

## 2. Владельческие колонки на liner_dimensions

- [x] 2.1 Changeset: addColumn на `liner_dimensions` — `leaf_id`, `frame_id`, `edge_id`, `door_casing_id`, `frame_extensions_id`, `leaf_mirror_id` (все BIGINT, nullable, FK на соответствующие таблицы)
- [x] 2.2 Changeset (sql): CHECK-ограничение на `liner_dimensions` — ровно одна из владельческих колонок заполнена

## 3. Владельческие колонки на colours

- [x] 3.1 Changeset: addColumn на `colours` — `leaf_id`, `frame_id`, `edge_id`, `door_casing_id`, `frame_extensions_id` (все BIGINT, nullable, FK на соответствующие таблицы)
- [x] 3.2 Changeset (sql): CHECK-ограничение на `colours` — ровно одна из владельческих колонок заполнена

## 4. Сущность price

- [x] 4.1 Changeset: createTable `price` — `id` (BIGINT identity PK), `retail_price` (DECIMAL NOT NULL), `dealer_price` (DECIMAL NOT NULL), владельческие колонки `item_id`, `leaf_id`, `frame_id`, `edge_id`, `leaf_mirror_id`, `door_casing_id`, `frame_extensions_id` (все BIGINT, nullable, FK на соответствующие таблицы)
- [x] 4.2 Changeset (sql): CHECK-ограничение на `price` — ровно одна из владельческих колонок заполнена

## 5. Подключение и проверка

- [x] 5.1 Создать `backend/src/main/resources/db/changelog/changes/0003-value-ownership-and-price.yaml`, содержащий все changeset'ы выше в указанном порядке
- [x] 5.2 Подключить `changes/0003-value-ownership-and-price.yaml` из `db.changelog-master.yaml` после `changes/0002-item-attribute-columns.yaml`
- [x] 5.3 Запустить локальный PostgreSQL (`docker compose up -d`) и выполнить `./backend/gradlew -p backend bootRun` к базе, где уже применены `0001`/`0002`, чтобы убедиться, что changeset'ы применяются без ошибок
- [x] 5.4 Убедиться, что каждый сценарий в `specs/item-configuration-schema/spec.md` выполняется на изменённой схеме: frame может иметь несколько liner_dimensions одновременно; вставка liner_dimensions/colours/price без владельца или с двумя владельцами отклоняется; price допускает несколько строк на один элемент
- [x] 5.5 `openspec validate restructure-value-ownership-and-add-price --strict`
