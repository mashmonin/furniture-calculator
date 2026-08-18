## 1. Справочные таблицы (без зависимостей)

- [x] 1.1 Changeset: создать `item_type` (только `id` identity PK)
- [x] 1.2 Changeset: создать `leaf_type` (только `id` identity PK)
- [x] 1.3 Changeset: создать `mirror_type` (только `id` identity PK)
- [x] 1.4 Changeset: создать `leaf_side` (только `id` identity PK)
- [x] 1.5 Changeset: создать `frame_type` (только `id` identity PK)
- [x] 1.6 Changeset: создать `liner_dimension_type` (только `id` identity PK)
- [x] 1.7 Changeset: создать `colour_type` (только `id` identity PK)

## 2. Таблицы общих значений

- [x] 2.1 Changeset: создать `liner_dimensions` с FK `liner_dimension_type_id` (`NOT NULL`, `ON DELETE RESTRICT`)
- [x] 2.2 Changeset: создать `colours` с FK `colour_type_id` (`NOT NULL`, `ON DELETE RESTRICT`)

## 3. Item

- [x] 3.1 Changeset: создать `item` с FK `item_type_id` (`NOT NULL`, `ON DELETE RESTRICT`)

## 4. Компоненты item

- [x] 4.1 Changeset: создать `leaf` с FK `item_id` (`NOT NULL`, `ON DELETE CASCADE`), `leaf_type_id` (`NOT NULL`, `ON DELETE RESTRICT`), `liner_dimensions_id` (`NOT NULL`, `ON DELETE RESTRICT`), `colours_id` (`NOT NULL`, `ON DELETE RESTRICT`)
- [x] 4.2 Changeset: создать `frame` с FK `item_id` (`NOT NULL`, `ON DELETE CASCADE`), `frame_type_id` (`NOT NULL`, `ON DELETE RESTRICT`), `liner_dimensions_id` (`NOT NULL`, `ON DELETE RESTRICT`), `colours_id` (`NOT NULL`, `ON DELETE RESTRICT`)
- [x] 4.3 Changeset: создать `edge` с FK `item_id` (`NOT NULL`, `ON DELETE CASCADE`), `liner_dimensions_id` (`NOT NULL`, `ON DELETE RESTRICT`), `colours_id` (`NOT NULL`, `ON DELETE RESTRICT`)

## 5. Leaf mirror

- [x] 5.1 Changeset: создать `leaf_mirror` с FK `leaf_id` (`NOT NULL`, `ON DELETE CASCADE`), `mirror_type_id` (`NOT NULL`, `ON DELETE RESTRICT`), `leaf_side_id` (`NOT NULL`, `ON DELETE RESTRICT`), `liner_dimensions_id` (`NOT NULL`, `ON DELETE RESTRICT`)

## 6. Компоненты frame

- [x] 6.1 Changeset: создать `door_casing` с FK `frame_id` (`NOT NULL`, `ON DELETE CASCADE`), `liner_dimensions_id` (`NOT NULL`, `ON DELETE RESTRICT`), `colours_id` (`NOT NULL`, `ON DELETE RESTRICT`)
- [x] 6.2 Changeset: создать `frame_extensions` с FK `frame_id` (`NOT NULL`, `ON DELETE CASCADE`), `liner_dimensions_id` (`NOT NULL`, `ON DELETE RESTRICT`), `colours_id` (`NOT NULL`, `ON DELETE RESTRICT`)

## 7. Подключение и проверка

- [x] 7.1 Создать `backend/src/main/resources/db/changelog/changes/0001-item-schema.yaml`, содержащий все changeset'ы выше, в указанном порядке зависимостей
- [x] 7.2 Подключить `changes/0001-item-schema.yaml` из `db.changelog-master.yaml`
- [x] 7.3 Запустить локальный PostgreSQL (`docker compose up -d`) и выполнить `./backend/gradlew -p backend bootRun` (или `liquibase update`), чтобы убедиться, что все changeset'ы применяются без ошибок к пустой базе данных
- [x] 7.4 Убедиться, что каждый сценарий в `specs/item-configuration-schema/spec.md` выполняется на созданной схеме (FK с `NOT NULL` отклоняют отсутствующие ссылки; `RESTRICT` предотвращает удаление используемых справочных/значимых строк; `CASCADE` удаляет компоненты при удалении их контейнера)
- [x] 7.5 `openspec validate add-item-schema --strict`
