## 1. Liquibase-миграция: configuration_price

- [x] 1.1 Создать `backend/src/main/resources/db/changelog/changes/0008-configuration-price.yaml`
- [x] 1.2 `createTable` для `configuration_price`: `id` (PK, autoIncrement), `retail_price` (DECIMAL, NOT NULL), `dealer_price` (DECIMAL, NOT NULL), пять nullable FK-владельцев (`leaf_type_id`, `frame_type_id`, `edge_type_id`, `door_casing_type_id`, `frame_extensions_type_id`, без `deleteCascade`), три nullable FK на `liner_dimension_option` (`length_option_id`, `height_option_id`, `thickness_option_id`, без `deleteCascade`), один nullable FK на `colour_option` (`colour_option_id`, без `deleteCascade`)
- [x] 1.3 SQL-changeset: CHECK-констрейнт `chk_configuration_price_single_owner` (ровно один владелец среди пяти типов)
- [x] 1.4 SQL-changeset: уникальный индекс `uk_configuration_price_combination` по `COALESCE`-выражениям всех девяти nullable-колонок (5 владельцев + length/height/thickness + colour)

## 2. Подключение и проверка

- [x] 2.1 Подключить `0008-configuration-price.yaml` в `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- [x] 2.2 Поднять локальный PostgreSQL (`docker compose up -d`), запустить `./gradlew bootRun` из `backend/`, убедиться что приложение стартует без ошибок и Liquibase применяет все changeset'ы из 0008
- [x] 2.3 Проверить структуру таблицы (`\d configuration_price`) — FK, CHECK-констрейнт и уникальный индекс на месте
- [x] 2.4 Вручную протестировать через SQL: вставка без владельца отклоняется, вставка с двумя владельцами отклоняется, вставка без retail_price/dealer_price отклоняется, вставка только с частью размеров (например, только length_option) проходит, дубликат той же комбинации отклоняется, корректная вставка с length/height/thickness/colour проходит
- [x] 2.5 Остановить приложение после проверки

## 3. Обновление главного spec

- [ ] 3.1 После реализации — синхронизировать delta-спеку с `openspec/specs/door-configuration-catalog/spec.md` (через `/opsx:sync` или `/opsx:archive`)
