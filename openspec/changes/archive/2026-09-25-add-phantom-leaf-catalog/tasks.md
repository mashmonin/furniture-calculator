## 1. Liquibase changeset

- [x] 1.1 Создать `backend/src/main/resources/db/changelog/changes/0106-phantom-leaf-catalog.yaml` (следующий свободный номер после `0105`).
- [x] 1.2 Changeset: вставить `liner_dimension_option` для `LT-045` — длина (`DT-001`) 400/600/700/800 мм, высота (`DT-002`) 2000/2100 мм, `is_standard = true` (по образцу `0048-sibir-leaf-catalog.yaml`, changeset `0048-2`/`0048-3`).
- [x] 1.3 Changeset: вставить `door_configuration` для `LT-045` × frame_type «ФАНТОМ» (`FT-001`) — ровно 6 строк по требованию «Сочетания реверса, четверти и кромки для ФАНТОМ 01»: `is_reverse=false/has_quarter=false` (без кромки и с `ET-001`), `is_reverse=false/has_quarter=true` (без кромки и с `ET-002`), `is_reverse=true/has_quarter=true` (без кромки и с `ET-002`); без `door_casing_type_id`/`frame_extensions_type_id`.
- [x] 1.4 Changeset: вставить `mirror_finish_option` для `LT-045` × все существующие `mirror_finish_type` (по образцу `0060-4-insert-mirror-finish-options`, ограничив `WHERE` на `LT-045` вместо списка коллекций).
- [x] 1.5 Changeset: обновить `leaf_type.panel_type = 'MIRRORED'` для `LT-045` (следствие появления `mirror_finish_option`, см. `0089-2-backfill-leaf-type-panel-type`).
- [x] 1.6 Changeset: вставить `configuration_price` для `LT-045` — `retail_price = 10728`, `dealer_price = 18255`, без ссылок на длину/высоту/толщину/цвет (по образцу `0048-6`).
- [x] 1.7 Подключить `changes/0106-phantom-leaf-catalog.yaml` в `backend/src/main/resources/db/changelog/db.changelog-master.yaml` (`include` после `0105`).
- [x] 1.8 Проверить, нужен ли desktop-override (H2): changeset не создаёт индексов/constraint'ов, только `INSERT`/`UPDATE` — по прецеденту (`0048`, `0104` не имеют override в `changes-desktop-overrides/`) override не требуется; подтвердить сборкой desktop-профиля при необходимости.
- [x] 1.9 Changeset (`0106-7`): удалить 30 ошибочно заведённых для `LT-045` строк `door_configuration` с коробом, отличным от «ФАНТОМ» (`FT-001`) — побочный эффект бага в предсуществующем `0105-9-restore-neo-frame-for-59mm-thickness-models-without-quarter` (обнаружено при проверке 2.3, подтверждено пользователем).
- [x] 1.10 Changeset (`0106-8`, новый — `0106-1..7` уже применены к локальной БД, задним числом не редактируются): вставить `liner_dimension_option` для `LT-045` — толщина (`DT-003`) 44 мм, `is_standard = true` (базовая толщина по умолчанию; 59 мм уже была заведена ранее в `0104-leaf-thickness-59mm.yaml`, уточнено пользователем).
- [x] 2.4 Применить и вручную проверить новый changeset (толщина 44/59 мм в каталоге ФАНТОМ 01). Проверено SQL-запросом: для `LT-045`/`DT-003` теперь ровно два значения — 44 и 59 мм, оба `is_standard = true`. Точечные тесты повторно прогнаны: те же 200/202 (2 предсуществующих несвязанных падения), регрессий нет.

## 2. Проверка

- [x] 2.1 Поднять PostgreSQL (`docker compose up -d`) и применить миграции (`./gradlew bootRun` из `backend/` либо `./gradlew update`), убедиться, что changeset `0106` применяется без ошибок.
- [x] 2.2 Точечно прогнать тесты каталога конфигураций (не полный `gradlew test` — см. память): `./gradlew test --tests "*.DoorConfigurationCatalogServiceTest"`, `./gradlew test --tests "*.DoorConfigurationApiIntegrationTest"`, `./gradlew test --tests "*.DoorConfigurationPricingServiceTest"`. 200/202 успешно; 2 падения (`каталог_возвращает_виды_остекления_только_для_полотна`, `повторная_пара_glazing_type_и_leaf_type_отклоняется`) — предсуществующий баг тестового хелпера `insertGlazingType` (не передаёт обязательный `surcharge_percent`, введённый changeset `0091`, задолго до этого change), к серии «Фантом» не относится, воспроизводится независимо от `0106`.
- [x] 2.3 Вручную проверить через API/frontend-конфигуратор, что для полотна ФАНТОМ 01 доступны: размеры 400/600/700/800×2000/2100, короб «ФАНТОМ» (обычный и реверс), кромка (без/прямая/с четвертью — согласованно с атрибутом «Четверть»), исполнения зеркала, цена; цветовые опции отсутствуют. Проверено через `GET /api/door-configurations`: ровно 6 конфигураций для `LT-045`, все с `frame=FT-001`, комбинации `is_reverse`/`has_quarter`/кромки совпадают со спекой; `panelType=MIRRORED`, все 3 исполнения зеркала присутствуют; `colourOptions` пуст; цена 10728/18255.

## 3. OpenSpec

- [x] 3.1 `openspec validate --change add-phantom-leaf-catalog --strict` перед архивированием.
