## 1. Backend: детализация заказа (Excel)

- [x] 1.1 В `SpecificationExportService.buildDetailRows` (`backend/src/main/java/com/example/furniturecalculator/service/SpecificationExportService.java`) сразу после строки «Полотно» и до строки «Кромка» добавить строку «Исполнение зеркала», если у `request.leaf().mirrorFinishTypeId()` есть значение, и/или строку «Вид остекления», если есть `request.leaf().glazingTypeId()` — наименование брать из `components.leaf().selectedOptions()` (то же поле, что уже использует `leafSectionRows` одиночной выгрузки), не резолвя его заново
- [x] 1.2 Определить, какое из значений `selectedOptions()` относится к зеркалу, а какое — к остеклению (см. design.md, Risks) — по `request.leaf()` id, а не по позиции в списке, чтобы не зависеть от порядка — реализовано: `leafSelection.mirrorFinishTypeId() != null` выбирает заголовок «Исполнение зеркала», иначе «Вид остекления»; `selectedOptions()` содержит не более одного значения (структурная взаимоисключаемость), поэтому `get(0)` безопасен
- [x] 1.3 Новая строка — `DetailRow(element, name, null, null, 1, false, null, null, null, null)` (`size`/`colour` = null, `priced` = false) — тем же способом, что и у компонента без найденной цены; отдельный флаг «цена неприменима» не вводить (см. design.md, Decisions) — реализовано как `leafOptionRow(element, name)`
- [x] 1.4 Проверить, что `OrderExportService.writeDetailTotalsRow` корректно пропускает новую строку при суммировании «Итого» — изменений в этом методе не требуется (уже работает через `priced()`), только проверка тестом — подтверждено чтением кода в design.md; тестом — см. 4.1

## 2. Frontend: детализация заказа (Excel) — тесты не нужны, но нужна проверка на сервере

- [x] 2.1 Прогнать `SpecificationExportServiceTest`/`OrderExportServiceTest` вручную после 1.1–1.3, чтобы убедиться, что существующие тесты без зеркала/остекления не сломаны (новые строки не должны появляться, если опции не выбраны) — все существующие тесты зелёные, без изменений

## 3. Frontend: детализация позиции корзины (экран)

- [x] 3.1 В `ConfiguratorScreen.buildCartItemContent` (`frontend/src/ConfiguratorScreen.tsx`), в месте построения `detailRows` (сразу после `detailRow('Полотно', ...)`, до строки «Кромка»), добавить строку «Исполнение зеркала» — если `mirrorFinishTypeId` определён, с наименованием выбранной опции из `mirrorFinishStep.options` — и/или строку «Вид остекления» — если `glazingTypeId` определён, с наименованием из `glazingStep.options`
- [x] 3.2 Новая строка — `{ element, name, size: null, colour: null, quantity: 1, retailPrice: null, dealerPrice: null, retailSum: null, dealerSum: null }`, тем же способом, что и у `detailRow(...)` для компонента без цены — не вводить новых полей в `CartDetailRow` (`frontend/src/cart.ts`) — реализовано через существующий `detailRow(element, name, null, null, 1, undefined)`, `CartDetailRow` не менялся

## 4. Проверка

- [x] 4.1 Backend: прогнать `--tests` на `SpecificationExportServiceTest` и `OrderExportServiceTest` (не полный `gradlew test`, см. project-память) — добавить/обновить тест на позицию с зеркалом и позицию с остеклением в заказе, проверить положение строки, пустые размер/цвет, прочерк в ценах, и что строка не включается в сумму «Итого» сверх нуля — добавлены 3 теста в `OrderExportServiceTest`, все 10 тестов зелёные
- [x] 4.2 Frontend: `tsc -b` и `oxlint` чисто; ручная проверка в конфигураторе (`npm run dev`) — добавить в корзину позицию с зеркалом (например, ФАНТОМ 01 с исполнением) и позицию с остеклением, развернуть «Детализация конфигурации», убедиться, что строка идёт сразу под «Полотно» — `tsc -b`, `oxlint` и `npm run build` чисто; визуальная проверка в браузере не выполнена — в этой сессии нет инструмента браузера (как и в change mirror-boolean-for-blind-leaf)
- [ ] 4.3 Скачать Excel-выгрузку заказа с такими позициями и визуально сверить, что строка «Исполнение зеркала»/«Вид остекления» идёт сразу под «Полотно» и не ломает строку «Итого» под детализацией — не выполнено (требует ручной проверки файла человеком); backend-поведение покрыто модульными тестами (см. 4.1), которые проверяют ровно это через структуру .xlsx

## 5. Уточнение по правке пользователя после первой реализации

- [x] 5.1 Вернуть заголовок «Исполнение зеркала»/«Вид остекления» в столбец «Элемент» (был ошибочно убран в первой реализации)
- [x] 5.2 Убрать лишние прочерки «—» у этой строки: добавить `priceApplicable` в `DetailRow` (backend) и `CartDetailRow` (frontend) — `false` только у строки исполнения зеркала/вида остекления, `true` у всех существующих строк; «Размеры»/«Цвет»/ценовые столбцы теперь пустые, а не «—», для этой конкретной строки — `OrderExportService.writeDetailRow` и `CartScreen.DetailTable` (`money`/рендер «Размеры»/«Цвет») обновлены соответственно
- [x] 5.3 Обновить тесты (`OrderExportServiceTest`) и delta-спеки (`order-export-api`, `order-cart-ui`) под новое поведение
- [x] 5.4 Перепроверить: `--tests` на `OrderExportServiceTest`/`SpecificationExportServiceTest` (10/10 зелёные), `tsc -b`, `oxlint`, `npm run build` — все чисто

## 6. Найденный баг: столбец «Наименование» дублировал категорию

- [x] 6.1 Первопричина: `components.leaf().selectedOptions()` (`DoorConfigurationPricingService.leafSelectedOptions`/`OptionSurcharge.selectedLabel`) уже возвращал строку вида «Исполнение зеркала: <значение>» — предназначенную для одноколоночного формата одиночной выгрузки (`leafSectionRows`/`labelRow`), но 1.1/3.1 переиспользовали её как есть для отдельного столбца «Наименование» двухколоночного формата — итог: «Элемент» = «Исполнение зеркала», «Наименование» = «Исполнение зеркала: <значение>» (дублирование, см. правку пользователя — «B2»)
- [x] 6.2 `resolveMirrorFinishMultiplier`/`resolveGlazingMultiplier` (`DoorConfigurationPricingService.java`) — убрать префикс-категорию из `OptionSurcharge.selectedLabel`, теперь голое наименование (`option.getMirrorFinishType().getName()`/`getGlazingType().getName()`)
- [x] 6.3 `SpecificationExportService.leafSectionRows` — переложить формирование префикса на месте вызова (`"Исполнение зеркала: " + ...`/`"Вид остекления: " + ...`, выбор по `leafSelection.mirrorFinishTypeId()`/`glazingTypeId()` — тем же способом, что и в `buildDetailRows`), чтобы поведение одиночной выгрузки не изменилось
- [x] 6.4 `buildDetailRows` — без изменений (уже брал `selectedOptions().get(0)` как «Наименование»; теперь это корректно голое значение без переделок)
- [x] 6.5 Обновить `SpecificationExportServiceTest` — тест `выбранные_опции_полотна_выгружаются_строками_между_полотном_и_кромкой` разделён на два теста (исполнение зеркала / вид остекления по отдельности, с явным `mirrorFinishTypeId`/`glazingTypeId` в запросе, как того теперь требует правильный выбор префикса) — фикстуры `selectedOptions` используют голые имена
- [x] 6.6 Перепроверить: `--tests` на `OrderExportServiceTest`/`SpecificationExportServiceTest`/`DoorConfigurationPricingServiceTest` (все зелёные) и широкий прогон `DoorConfigurationApiIntegrationTest`/`DoorConfigurationCatalogServiceTest` (248 тестов, 2 предсуществующих несвязанных падения — см. `insertGlazingType` без `surcharge_percent`, не относится к этой правке); `tsc -b`, `oxlint`, `npm run build` — чисто
