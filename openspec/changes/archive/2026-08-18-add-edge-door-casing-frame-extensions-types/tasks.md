## 1. Новые справочные таблицы

- [x] 1.1 Changeset: создать `edge_type` (`id` identity PK, `name` VARCHAR(255) NOT NULL, `code` VARCHAR(255) NOT NULL UNIQUE)
- [x] 1.2 Changeset: создать `door_casing_type` (`id` identity PK, `name` VARCHAR(255) NOT NULL, `code` VARCHAR(255) NOT NULL UNIQUE)
- [x] 1.3 Changeset: создать `frame_extensions_type` (`id` identity PK, `name` VARCHAR(255) NOT NULL, `code` VARCHAR(255) NOT NULL UNIQUE)

## 2. FK-колонки на компонентах

- [x] 2.1 Changeset: добавить в `edge` колонку `edge_type_id` (BIGINT NOT NULL, FK на `edge_type(id)`, ON DELETE RESTRICT)
- [x] 2.2 Changeset: добавить в `door_casing` колонку `door_casing_type_id` (BIGINT NOT NULL, FK на `door_casing_type(id)`, ON DELETE RESTRICT)
- [x] 2.3 Changeset: добавить в `frame_extensions` колонку `frame_extensions_type_id` (BIGINT NOT NULL, FK на `frame_extensions_type(id)`, ON DELETE RESTRICT)

## 3. Подключение и проверка

- [x] 3.1 Создать `backend/src/main/resources/db/changelog/changes/0005-edge-door-casing-frame-extensions-types.yaml`, содержащий все changeset'ы выше в указанном порядке (справочники — раньше FK-колонок, ссылающихся на них)
- [x] 3.2 Подключить `changes/0005-edge-door-casing-frame-extensions-types.yaml` из `db.changelog-master.yaml` после `changes/0004-item-configuration-reference-data.yaml`
- [x] 3.3 Запустить локальный PostgreSQL (`docker compose up -d`) и выполнить `./backend/gradlew -p backend bootRun` к базе, где уже применены `0001`–`0004`, чтобы убедиться, что changeset'ы применяются без ошибок (обратить внимание: `edge`, `door_casing`, `frame_extensions` сейчас пусты — `NOT NULL addColumn` не будет заблокирован существующими строками)
- [x] 3.4 Убедиться, что каждый сценарий в `specs/item-configuration-schema/spec.md` выполняется на изменённой схеме: edge/door_casing/frame_extensions требуют существующий *_type; используемый edge_type/door_casing_type/frame_extensions_type нельзя удалить; edge_type/door_casing_type/frame_extensions_type требуют name и уникальный code
- [x] 3.5 `openspec validate add-edge-door-casing-frame-extensions-types --strict`
