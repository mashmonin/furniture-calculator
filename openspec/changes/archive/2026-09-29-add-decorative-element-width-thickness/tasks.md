## 1. Схема и данные

- [x] 1.1 Создать `backend/src/main/resources/db/changelog/changes/0109-decorative-element-width-thickness.yaml`: changeset `addColumn` — `width_mm` и `thickness_mm` (DECIMAL, nullable) в `decorative_element_type`
- [x] 1.2 В том же файле — changeset обновления значений width_mm/thickness_mm для `DET-003`…`DET-007` (см. specs/decorative-element-catalog/spec.md)
- [x] 1.3 Подключить `0109-decorative-element-width-thickness.yaml` в `db.changelog-master.yaml`

## 2. Backend

- [x] 2.1 Добавить поля `widthMm` и `thicknessMm` (nullable `BigDecimal`) в `DecorativeElementType` (домен) — без изменений DTO каталога и контроллера, эти поля наружу не отдаются
- [x] 2.2 Добавить `widthMm`/`thicknessMm` в `DecorativeElementPriceDto` и прокинуть их из `DecorativeElementType` в `DoorConfigurationPricingService`
- [x] 2.3 В `SpecificationExportService` заменить `plainNumber(item.lengthMm())` на `formatDimensions(item.lengthMm(), item.widthMm(), item.thicknessMm())` в обоих местах, где строится строка декоративного элемента (детализация под строкой заказа и раздел «Декоративные элементы» спецификации)
- [x] 2.4 В `formatDimensions` (общий хелпер для всех размеров выгрузки — полотно, короб, наличник, декоративные элементы) заменить разделитель `*` на ` × `

## 3. Проверка

- [x] 3.1 Запустить backend локально и убедиться, что миграция применяется без ошибок; выборкой из БД (или через `DecorativeElementTypeRepository`/лог) проверить, что у `DET-003`…`DET-007` width_mm/thickness_mm заполнены значениями из спеки, а у `DET-001`/`DET-002` — NULL
- [x] 3.2 Обновить существующие тесты `SpecificationExportServiceTest`, использующие конструктор `DecorativeElementPriceDto`, под новую сигнатуру; добавить тест, проверяющий, что для типа с шириной и толщиной колонка размеров выгрузки содержит `90 × 90 × 30`
- [x] 3.3 Обновить существующие assertions на разделитель `*` (строка полотна `900*2400*44` и т.п.) на ` × ` по всему `SpecificationExportServiceTest`
- [x] 3.4 Прогнать `./gradlew test --tests SpecificationExportServiceTest --tests DoorConfigurationPricingServiceTest --tests OrderExportServiceTest`
