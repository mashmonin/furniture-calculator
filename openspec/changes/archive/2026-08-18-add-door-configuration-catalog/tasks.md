## 1. Liquibase-миграция: door_configuration

- [x] 1.1 Создать `backend/src/main/resources/db/changelog/changes/0006-door-configuration-catalog.yaml`
- [x] 1.2 `createTable` для `door_configuration`: `id` (PK, autoIncrement), `leaf_type_id` (NOT NULL, FK на `leaf_type`), `frame_type_id`/`edge_type_id`/`door_casing_type_id`/`frame_extensions_type_id` (nullable, FK на соответствующие `*_type`, без `deleteCascade`)
- [x] 1.3 SQL-changeset: CHECK-констрейнт `chk_door_configuration_casing_extensions_require_frame` (door_casing_type_id/frame_extensions_type_id заданы ⇒ frame_type_id задан)
- [x] 1.4 SQL-changeset: уникальный индекс `uk_door_configuration_combination` по `(leaf_type_id, COALESCE(frame_type_id,0), COALESCE(edge_type_id,0), COALESCE(door_casing_type_id,0), COALESCE(frame_extensions_type_id,0))`

## 2. Liquibase-миграция: liner_dimension_option

- [x] 2.1 `createTable` для `liner_dimension_option`: `id` (PK, autoIncrement), `liner_dimension_type_id` (NOT NULL, FK), `value` (DECIMAL, NOT NULL), `is_standard` (BOOLEAN, NOT NULL), пять nullable FK-владельцев (`leaf_type_id`, `frame_type_id`, `edge_type_id`, `door_casing_type_id`, `frame_extensions_type_id`, без `deleteCascade`)
- [x] 2.2 SQL-changeset: CHECK-констрейнт `chk_liner_dimension_option_single_owner` (ровно один владелец)
- [x] 2.3 SQL-changeset: пять partial unique индексов `uk_liner_dimension_option_<owner>` на `(<owner>_id, liner_dimension_type_id, value) WHERE <owner>_id IS NOT NULL`

## 3. Liquibase-миграция: colour_option

- [x] 3.1 `createTable` для `colour_option`: `id` (PK, autoIncrement), `colour_type_id` (NOT NULL, FK), пять nullable FK-владельцев (те же пять, без `deleteCascade`)
- [x] 3.2 SQL-changeset: CHECK-констрейнт `chk_colour_option_single_owner` (ровно один владелец)
- [x] 3.3 SQL-changeset: пять partial unique индексов `uk_colour_option_<owner>` на `(<owner>_id, colour_type_id) WHERE <owner>_id IS NOT NULL`

## 4. Подключение и проверка

- [x] 4.1 Подключить `0006-door-configuration-catalog.yaml` в `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- [x] 4.2 Поднять локальный PostgreSQL (`docker compose up -d`), запустить `./gradlew bootRun` из `backend/`, убедиться что приложение стартует без ошибок и Liquibase применяет все changeset'ы из 0006
- [x] 4.3 Проверить структуру всех трёх таблиц (`\d door_configuration`, `\d liner_dimension_option`, `\d colour_option`) — FK, CHECK-констрейнты и уникальные индексы на месте
- [x] 4.4 Вручную протестировать через SQL по каждой таблице: нарушение single-owner отклоняется, дубликат отклоняется, добор/удлинитель без коробки в door_configuration отклоняется, корректная вставка проходит
- [x] 4.5 Остановить приложение после проверки

## 5. Обновление главного spec

- [ ] 5.1 После реализации — синхронизировать новую спеку `door-configuration-catalog` в `openspec/specs/` (через `/opsx:sync` или `/opsx:archive`)
