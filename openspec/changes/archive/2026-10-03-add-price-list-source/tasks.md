## 1. База данных

- [x] 1.1 Создать Liquibase changeset: таблица `price_list` (`id`, `code` уникальный, `name`), начальная запись `PL-001` = `hausdoors_emal_i_shpon_rf_07_09_2026`
- [x] 1.2 В том же или следующем changeset'е: колонки `collection.price_list_id` и `hardware_type.price_list_id` (сначала nullable), заполнение `PL-001` для всех существующих строк, затем `NOT NULL` и внешние ключи; без триггеров и процедурной логики
- [x] 1.3 Подключить changeset в `db.changelog-master.yaml`; задать rollback
- [x] 1.4 Убедиться, что миграция проходит на H2 (десктоп) без override; при несовместимости — override по паттерну `changes-desktop-overrides/`

## 2. Backend (API)

- [x] 2.1 Сущность `PriceList`, поле `priceList` в `LeafCollection` и `HardwareType`
- [x] 2.2 DTO прайс-листа (`id`, `code`, `name`); поле в `ComponentCatalogDto` (только leaf) и в `HardwareTypeDto`
- [x] 2.3 Заполнение полей в `DoorConfigurationCatalogService` и в сервисе каталога фурнитуры
- [x] 2.4 Тесты: `DoorConfigurationApiIntegrationTest` (прайс-лист у leaf, null у остальных), `HardwareCatalogControllerTest` (прайс-лист у типа)

## 3. Frontend

- [x] 3.1 Тип прайс-листа и поля в `api/types.ts`
- [x] 3.2 Заменить `PRICE_LIST_LABEL` в `App.tsx`: подпись «Прайс-лист: » + `priceList.name` из загруженного каталога; не показывать до загрузки/при пустом каталоге
- [x] 3.3 Обновить комментарий с прежним названием файла в `CartScreen.tsx`; корзину и экспорт не менять
- [x] 3.4 `npm run lint` и `npm run build` проходят

## 4. Проверка

- [x] 4.1 Прогнать `DoorConfigurationApiIntegrationTest`, `HardwareCatalogControllerTest`, `FurnitureCalculatorApplicationTests` (`--tests`) на PostgreSQL
- [x] 4.2 Прогнать `DesktopProfileTest` и `DesktopLauncherBackendStartupTest` на H2
- [x] 4.3 Проверить в запущенном приложении, что в шапке «Эмаль и шпон» отображается новое название файла
