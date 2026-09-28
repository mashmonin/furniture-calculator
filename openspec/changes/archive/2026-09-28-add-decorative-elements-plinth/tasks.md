## 1. Схема данных (Liquibase)

- [x] 1.1 Новый changeset (следующий свободный номер после `0106`): создать таблицы `decorative_element_category` (id, name, code unique), `decorative_element_type` (id, decorative_element_category_id FK, name, code unique, short_name, length_mm, retail_price, dealer_price — все, кроме short_name, NOT NULL), `decorative_element_option` (id, decorative_element_type_id FK, leaf_type_id FK, unique(decorative_element_type_id, leaf_type_id)). `0107-decorative-elements-plinth.yaml`.
- [x] 1.2 Данные: вставить категорию «Плинтус» (`DEC-001`), два типа — «Плинтус Модо» (`DET-001`) и «Плинтус Модо облегченный» (`DET-002`) (length_mm 2400, retail_price 2611, dealer_price 1492 у обоих).
- [x] 1.3 Данные: вставить `decorative_element_option` для обоих типов плинтуса × все leaf_type, чья collection отлична от `LC-009` (Фантом).
- [x] 1.4 Подключить новый changeset в `db.changelog-master.yaml`; desktop-override не требуется (только `createTable`/`INSERT`, без expression-индексов, без `COALESCE`).

## 2. Backend — каталог и расчёт декоративных элементов

- [x] 2.1 JPA-сущности `DecorativeElementCategory`, `DecorativeElementType`, `DecorativeElementOption` (пакет `domain`), по образцу `HardwareCategory`/`HardwareType`/`HardwareOption`.
- [x] 2.2 Репозитории `DecorativeElementCategoryRepository`, `DecorativeElementTypeRepository`, `DecorativeElementOptionRepository`.
- [x] 2.3 DTO: `DecorativeElementCategoryDto`, `DecorativeElementTypeDto` (ReferenceDto, lengthMm, retailPrice, dealerPrice), `DecorativeElementSelectionDto` (id, quantity?), `DecorativeElementPriceDto`, `DecorativeElementPricingRequestDto`, `DecorativeElementPricingResponseDto` — по образцу одноимённых Hardware*Dto.
- [x] 2.4 `DecorativeElementCatalogService` + `DecorativeElementCatalogController` (`GET /api/decorative-elements-catalog`) — по образцу `HardwareCatalogService`/`HardwareCatalogController`.
- [x] 2.5 В `DoorConfigurationPricingService`: метод расчёта списка позиций декоративных элементов (id типа + количество, ошибка 400 на несуществующий тип и на неположительное количество, по умолчанию количество 1) — по образцу существующего метода для фурнитуры; используется и эндпоинтом `POST /api/decorative-elements/price`, и основным расчётом по `door_configuration`, и расчётом отдельного полотна.
- [x] 2.6 `DecorativeElementPricingController` (`POST /api/decorative-elements/price`) — по образцу `HardwarePricingController`.
- [x] 2.7 Расширить запрос/ответ расчёта стоимости по `door_configuration` (см. специфику `door-configuration-api`, «Учёт декоративных элементов в расчёте стоимости изделия») и расчёта отдельного полотна (`leaf-standalone-pricing`) — необязательный список позиций декоративных элементов, суммируется отдельным слагаемым, без надбавок, без проверки допустимости для коллекции. `PricingRequestDto`/`PricingResponseDto` расширены (с обратно-совместимыми legacy-конструкторами для существующих тестов).
- [x] 2.8 Расчёт короба/наличника/добора (`FrameGroupPricingRequestDto`) не имеет поля декоративных элементов вовсе — лишнее поле в JSON игнорируется десериализацией по умолчанию; тест на этот сценарий — см. 6.2.
- [x] 2.9 Каталог конфигураций (`GET /api/door-configurations`): добавлено полю leaf-компонента поле `decorativeElements` (`List<ReferenceDto>`, по `decorative_element_option`) — пусто для остальных типов компонента и для leaf_type коллекции «Фантом» (нет строк `decorative_element_option`).

## 3. Backend — выгрузка спецификации и заказа

- [x] 3.1 Расширить запрос `POST /api/specification/export` списком позиций декоративных элементов; валидация — 400 на несуществующий decorative_element_type (через `resolveSpecificationComponents` → `priceDecorativeElementSelections`).
- [x] 3.2 Новый раздел файла «Декоративные элементы» между «Наличники и доборы» и «Фурнитура» — по одной строке на позицию (наименование категории+типа, длина в мм в столбце размеров, количество, цена/сумма розница и дилер; базовая цена = итоговой, без наценок).
- [x] 3.3 Раздел «Итоговая сумма» — включает декоративные элементы в сумму.
- [x] 3.4 `POST /api/specification/export-order`: тело позиции (`OrderLineExportRequestDto.specification`) автоматически наследует новое поле — оно просто оборачивает `SpecificationExportRequestDto`; блок детализации листа заказа (`OrderExportService`) полностью общий (`DetailRow`/`resolveConfigurationBreakdown`), новых строк без изменений в самом `OrderExportService`.

## 4. Frontend — конфигуратор

- [x] 4.1 Типы: `DecorativeElementCategoryDto`/`DecorativeElementTypeDto` в `src/api/types.ts`; поле `decorativeElements` у `ComponentCatalogDto` (leaf).
- [x] 4.2 Загрузка каталога декоративных элементов при открытии страницы (`GET /api/decorative-elements-catalog`), наряду с каталогом конфигураций и каталогом фурнитуры; ошибка загрузки не блокирует остальной функционал.
- [x] 4.3 Блок «Декоративные элементы»: компонент-позиция (категория → тип, количество, удаление), переиспользующий паттерн блока фурнитуры (`OptionGroup`-подобный каскад), без уровня цвета; поля в один ряд («Категория», «Тип», «Количество», «Удалить»); сокращение выбранного значения до первого слова с «…», как у фурнитуры.
- [x] 4.4 Запрет повторного выбора одного и того же типа в двух позициях одновременно (фильтрация списка типов по уже выбранным в других позициях) — по образцу фурнитуры.
- [x] 4.5 Доступность блока построена по `leaf.decorativeElements` из каталога конфигураций (не по хардкоду кода коллекции) — для коллекции «Фантом» и до выбора полотна список пуст, блок показывает поясняющее сообщение и не даёт добавить позицию.
- [x] 4.6 Новый раздел аккордеона «Декоративные элементы» между «Короб и обрамление» и «Фурнитура» (`ConfiguratorScreen.tsx`), эксклюзивный, как и остальные три раздела.
- [x] 4.7 Четвёртый независимый debounced-этап автоматического расчёта: `POST /api/decorative-elements/price` с текущим списком завершённых позиций, как только определён тип полотна, у него есть хотя бы один допустимый decorative_element_type и есть хотя бы одна завершённая позиция; не блокирует и не блокируется остальными этапами.
- [x] 4.8 Объединение результата в sticky-панели: итоговая сумма включает этап «Декоративные элементы»; отдельная разбивка по декоративным элементам в `ComponentBreakdownList`, наряду с разбивкой по фурнитуре.
- [x] 4.9 Кнопка «Очистить» дополнительно сбрасывает все добавленные позиции декоративных элементов и результат/ошибку этапа. Type-check (`tsc -b`) и `npm run lint` — чисто.

## 5. Frontend — корзина

- [x] 5.1 Структура позиции корзины (`CartItem`/`CartItemContent` в `cart.ts`) уже опаково хранит `exportRequest: SpecificationExportRequestDto` и `pricingSnapshot: PricingResponseDto` — оба типа уже расширены полем декоративных элементов (см. 4.1/2.7), `cart.ts` не нужно менять.
- [x] 5.2 Разворачиваемая детализация: строки «Декоративные элементы» добавляются в `detailRows` в `ConfiguratorScreen.buildCartItemContent` (см. 4.8/сопутствующая правка) между «Добор» и «Фурнитура»; `CartScreen.DetailTable` рендерит `detailRows` полностью универсально (по `element`/`size`/`colour` из данных) — изменений в `CartScreen.tsx`/`cart.ts` не требуется.
- [x] 5.3 «Посмотреть»: `App.tsx`/`CartScreen.tsx` передают `item.exportRequest` как есть в `loadRequest` — восстановление `decorativeElementLines` из `request.decorativeElements` уже реализовано в эффекте реконструкции конфигуратора (см. 4.х).
- [x] 5.4 Живая синхронизация уже покрыта: `decorativeElementLines`/`decorativeElementsPricingResult` добавлены в зависимости эффекта синхронизации (см. изменение зависимостей выше).
- [x] 5.5 `POST /api/specification/export`/`export-order`: `exportRequest.decorativeElements` заполняется в `buildCartItemContent` (см. 4.х) и передаётся как есть — `OrderLineExportRequestDto.specification` оборачивает тот же `SpecificationExportRequestDto`, изменений в `CartScreen.tsx` не требуется.

## 6. Проверка

- [x] 6.1 Миграция `0107` применяется без ошибок (проверено запуском backend). SQL-проверка по коллекциям: `LC-009` (Фантом, 1 leaf_type) — 0 строк `decorative_element_option`; остальные 8 коллекций (44 leaf_type суммарно) — по 2 строки на каждый (2 типа плинтуса), итог 88 строк. Соответствует ожиданиям.
- [x] 6.2 Backend-тесты (новые + затронутые точечно, не полный `gradlew test`): `DecorativeElementCatalogServiceTest`, `DecorativeElementCatalogControllerTest` (новые), тесты в `DoorConfigurationPricingServiceTest` (итог/умолчательное количество/несколько позиций/несуществующий тип 400/неположительное количество 400/без надбавок за реверс — и для `calculate()`, и для `calculateForLeaf()`, и для standalone `calculateDecorativeElements()`), тест в `DoorConfigurationCatalogServiceTest` (`leaf.decorativeElements`), тесты в `SpecificationExportServiceTest` (порядок разделов, содержимое строки). Игнорирование в `frame-group-standalone-pricing` не тестируется отдельно — гарантировано отсутствием поля в `FrameGroupPricingRequestDto` (см. 2.8). Прогон: 241 тест, 0 новых падений (2 предсуществующих несвязанных — `insertGlazingType`).
- [x] 6.3 Frontend: `tsc -b` и `npm run lint` — чисто; `npm run build` — production-сборка успешна. Полный стек проверен через curl/SQL (backend поднят + `vite` dev-сервер с проксированием `/api`, каталог декоративных элементов и каталог конфигураций отдаются корректно через тот же путь, что использует React-приложение). **Визуальную проверку в браузере (клики по аккордеону, реальный рендер блока, живое взаимодействие) выполнить не удалось — в этой среде нет доступного инструмента браузера/скриншотов.** Логика (доступность блока, порядок разделов, расчёт, отражение в корзине/выгрузке) проверена по коду и через API, но конечный визуальный результат в браузере не подтверждён; рекомендую пользователю самостоятельно открыть конфигуратор и пройти сценарий вручную перед архивированием.

## 7. OpenSpec

- [x] 7.1 `openspec validate add-decorative-elements-plinth --strict` перед архивированием — успешно.

## 8. Правка после первой реализации

- [x] 8.1 По просьбе пользователя: наименование декоративного элемента в колонке «Наименование» (детализация корзины, разделы выгрузки .xlsx) и в разбивке результата расчёта показывает только название типа (`type.name`), без наименования категории — в отличие от фурнитуры, где сохраняется формат «категория — тип». Исправлено в `ConfiguratorScreen.tsx` (детализация корзины), `ComponentBreakdownList.tsx` (разбивка результата), `SpecificationExportService.java` (оба места — раздел одиночной спецификации и плоская детализация для заказа); обновлены тест и спеки (`door-configuration-export`, `door-configurator-ui`). Точечные тесты и `tsc`/`lint` — чисто.
