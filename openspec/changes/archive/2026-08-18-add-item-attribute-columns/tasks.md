## 1. Атрибуты справочных таблиц

- [x] 1.1 Changeset: добавить в `item_type` колонки `name` (VARCHAR(255), NOT NULL) и `code` (VARCHAR(255), NOT NULL, UNIQUE)
- [x] 1.2 Changeset: добавить в `leaf_type` колонки `name` (VARCHAR(255), NOT NULL) и `code` (VARCHAR(255), NOT NULL, UNIQUE)
- [x] 1.3 Changeset: добавить в `mirror_type` колонки `name` (VARCHAR(255), NOT NULL) и `code` (VARCHAR(255), NOT NULL, UNIQUE)
- [x] 1.4 Changeset: добавить в `leaf_side` колонки `name` (VARCHAR(255), NOT NULL) и `code` (VARCHAR(255), NOT NULL, UNIQUE)
- [x] 1.5 Changeset: добавить в `frame_type` колонки `name` (VARCHAR(255), NOT NULL) и `code` (VARCHAR(255), NOT NULL, UNIQUE)
- [x] 1.6 Changeset: добавить в `liner_dimension_type` колонки `name` (VARCHAR(255), NOT NULL) и `code` (VARCHAR(255), NOT NULL, UNIQUE)
- [x] 1.7 Changeset: добавить в `colour_type` колонки `name` (VARCHAR(255), NOT NULL) и `code` (VARCHAR(255), NOT NULL, UNIQUE)

## 2. Атрибуты liner_dimensions

- [x] 2.1 Changeset: добавить в `liner_dimensions` колонки `value` (DECIMAL, NOT NULL) и `is_standard` (BOOLEAN, NOT NULL)

## 3. Атрибуты colours

- [x] 3.1 Changeset: добавить в `colours` колонки `name` (VARCHAR(255), NOT NULL) и `ral_code` (VARCHAR(255), nullable)

## 4. Подключение и проверка

- [x] 4.1 Создать `backend/src/main/resources/db/changelog/changes/0002-item-attribute-columns.yaml`, содержащий все changeset'ы выше
- [x] 4.2 Подключить `changes/0002-item-attribute-columns.yaml` из `db.changelog-master.yaml` после `changes/0001-item-schema.yaml`
- [x] 4.3 Запустить локальный PostgreSQL (`docker compose up -d`) и выполнить `./backend/gradlew -p backend bootRun` (или `liquibase update`) к базе, где уже применён `0001-item-schema.yaml`, чтобы убедиться, что `addColumn`-changeset'ы применяются без ошибок
- [x] 4.4 Убедиться, что каждый сценарий в `specs/item-configuration-schema/spec.md` выполняется на изменённой схеме (NOT NULL отклоняет отсутствующие name/code/value/is_standard; UNIQUE отклоняет повторный code; colours сохраняется без ral_code)
- [x] 4.5 `openspec validate add-item-attribute-columns --strict`
