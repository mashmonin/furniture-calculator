## 1. Данные каталога

- [x] 1.1 Создать Liquibase changeset `0127-emal-layt-price-list-update-06-10-2026.yaml` и подключить его в `db.changelog-master.yaml` после `0126`
- [x] 1.2 `0127-1`: переименовать `PL-002` в `hausdoors_emal_layt_tsfo_06_10_2026` (rollback — прежнее имя `hausdoors_emal_layt_tsfo_07_09_2026`)
- [x] 1.3 `0127-2`: обновить `configuration_price` для `LT-046`, ширин 400/600/700/800 и высоты 2000: дилерская и розничная = 1000000 (rollback — 10764 / 17761); только переносимый SQL (PostgreSQL и H2)

## 2. Тесты

- [x] 2.1 Обновить `EmalLaytCatalogIntegrationTest`: название прайс-листа, ожидаемые цены MONO MN 01 (ширина ≤800, высота 2000) и места с 17761/10764
- [x] 2.2 Обновить `EmalLaytHardwareCatalogIntegrationTest`: константа `PRICE_LIST_NAME`

## 3. Проверка

- [x] 3.1 Убедиться, что значение 1000000 помещается в тип столбцов цен и что сохранённые заказы/корзина не зависят от старой цены
- [x] 3.2 Запустить `EmalLaytCatalogIntegrationTest` и `EmalLaytHardwareCatalogIntegrationTest` через `--tests` (не полный `gradlew test`) и убедиться, что миграция применяется
- [ ] 3.3 Сверить с исходным файлом прайса, что 1 000 000 — не опечатка
