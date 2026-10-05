## 1. Миграция

- [x] 1.1 Создать `backend/src/main/resources/db/changelog/changes/0127-emal-layt-price-update-05-10-2026.yaml` с changeSet'ом `UPDATE price_list SET name = 'hausdoors_emal_layt_tsfo_05_10_2026' WHERE code = 'PL-002'` и rollback на `hausdoors_emal_layt_tsfo_07_09_2026`
- [x] 1.2 Добавить в тот же файл changeSet `UPDATE configuration_price SET retail_price = 18761` для MONO MN 01 (`LT-046`), высота 2000, ширины 400/600/700/800 (условие `retail_price = 17761`), rollback — возврат 17761; строки адресовать через коды и значения опций
- [x] 1.3 Подключить файл в конец `db.changelog-master.yaml` (`relativeToChangelogFile: true`); ранее применённые changeset'ы не менять

## 2. Тесты

- [x] 2.1 `EmalLaytCatalogIntegrationTest`: розница MONO MN 01 при высоте 2000 и ширинах 400–800 — 18 761 (вместо 17 761), название прайс-листа — `hausdoors_emal_layt_tsfo_05_10_2026`; цены остальных размеров и моделей не меняются
- [x] 2.2 `EmalLaytHardwareCatalogIntegrationTest`: название прайс-листа — `hausdoors_emal_layt_tsfo_05_10_2026`
- [ ] 2.3 Запустить только эти два класса: `./gradlew test --tests '*EmalLaytCatalogIntegrationTest' --tests '*EmalLaytHardwareCatalogIntegrationTest'`

## 3. Проверка

- [ ] 3.1 Убедиться, что изменено ровно 4 строки `configuration_price` и коллекции/фурнитура `PL-002` по-прежнему ссылаются на прайс-лист
- [x] 3.2 `openspec validate update-price-list-20261006`
