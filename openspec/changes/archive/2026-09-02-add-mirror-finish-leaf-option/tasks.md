## 1. Схема и данные (Liquibase)

- [x] 1.1 Новый changeset: создать таблицу `mirror_finish_type` (id, name NOT NULL, surcharge_percent NOT NULL).
- [x] 1.2 Новый changeset: создать таблицу `mirror_finish_option` (id, mirror_finish_type_id FK → mirror_finish_type, leaf_type_id FK → leaf_type) и уникальный индекс по (mirror_finish_type_id, leaf_type_id).
- [x] 1.3 Новый changeset: заполнить `mirror_finish_type` тремя строками — «Глухое плоское полотно с зеркалом с одной стороны» (30), «Глухое плоское полотно с зеркалом с фацетом с одной стороны» (40), «Глухое плоское полотно с зеркалом серое/бронза с одной стороны» (50).
- [x] 1.4 Новый changeset: заполнить `mirror_finish_option` — `CROSS JOIN` всех трёх `mirror_finish_type` на все `leaf_type`, чей `collection.code IN ('LC-003','LC-004','LC-005','LC-006','LC-007','LC-008')` (Элегант, Гармония, Свобода, Геометрия, Сияние, Сибирь).
- [x] 1.5 Подключить все новые changeset'ы в `db.changelog-master.yaml`.
- [x] 1.6 Прогнать миграцию локально и проверить: 3 строки в `mirror_finish_type` с процентами 30/40/50; 93 строки в `mirror_finish_option` (3 × 31 подходящий leaf_type); ни один leaf_type коллекций Вертикаль/Атмосфера/Фантом не затронут. **Подтверждено** через прямой SQL — 3/93/по коллекциям (18+18+24+12+3+18=93).

## 2. Backend — каталог и справочник

- [x] 2.1 Создать `MirrorFinishType.java` (domain) — id, name, surchargePercent, по образцу `DimensionSurchargeRule.java`.
- [x] 2.2 Создать `MirrorFinishOption.java` (domain) — id, mirrorFinishType (ManyToOne), leafType (ManyToOne).
- [x] 2.3 Создать `MirrorFinishTypeRepository` (`findAll()` уже есть у `JpaRepository`) и `MirrorFinishOptionRepository` с методами `findByLeafTypeId(Long)` и `findById(Long)` (уже есть у `JpaRepository`).
- [x] 2.4 `ComponentCatalogDto` — добавить generic-поле `mirrorFinishOptions: List<ReferenceDto>`.
- [x] 2.5 `DoorConfigurationCatalogService.buildLeafComponent` — заполнить `mirrorFinishOptions` через `MirrorFinishOptionRepository.findByLeafTypeId`; id в `ReferenceDto` — id строки `mirror_finish_type` (глобальный; **не** `mirror_finish_option.id` — см. правку дизайна после 4.1, единое пространство id с запросом и `/api/pricing-surcharges`). `MirrorFinishType` не реализует `CatalogType` (нет поля `code`) — `ReferenceDto` строится вручную с `code = null`. `buildComponent`/`buildFrameComponent` — передают пустой список.
- [x] 2.6 Добавлены integration-тесты `DoorConfigurationApiIntegrationTest`: leaf с исполнением зеркала отдаёт его в каталоге (frame того же configuration — пустой список); leaf без исполнений — пустой список. `compileTestJava` проходит; сам прогон не выполнялся.

## 3. Backend — расчёт стоимости

- [x] 3.1 `ComponentSelectionDto` — добавить опциональное поле `mirrorFinishTypeId` (Long; id `mirror_finish_type`, не `mirror_finish_option` — см. правку дизайна). Позиционные конструкторы в тестах (28 мест) обновлены тем же скриптом с балансировкой скобок, что и для `quantity`.
- [x] 3.2 `DoorConfigurationPricingService.resolveMirrorFinishMultiplier` — валидация: `mirrorFinishTypeId` указан для компонента, отличного от leaf, — 400; для этой пары (mirrorFinishTypeId, leafTypeId) нет строки `mirror_finish_option` (id не существует вовсе ИЛИ существует, но не для этого полотна — одна и та же проверка, см. design.md) — 400.
- [x] 3.3 То же — резолвинг: `mirrorFinishOptionRepository.findByMirrorFinishTypeIdAndLeafTypeId(mirrorFinishTypeId, leafTypeId)` → множитель `1 + surchargePercent/100`; если не указан — множитель 1 (шаг пропускается через `applyPercentMultiplier`'s no-op при множителе 1).
- [x] 3.4 `applySequentialSurcharges` — добавлен параметр `mirrorFinishMultiplier`, применяется строго между шагом высоты и шагом реверса, с тем же округлением `HALF_UP`, что и у остальных шагов.
- [x] 3.5 Добавлены unit-тесты `DoorConfigurationPricingServiceTest` (5 штук): наценка за зеркало между высотой и реверсом (retail 1011→2024, dealer 911→1824; обратный порядок дал бы 2023/1823 — числовой пример подтверждает порядок шагов); зеркало не выбрано — цена как раньше; зеркало для edge — 400; несуществующий id — 400; id чужого leaf_type — 400. `compileTestJava` проходит; сам прогон не выполнялся.

## 4. Backend — эндпоинт процентов надбавок

- [x] 4.1 `PricingSurchargesDto` — добавлено поле `mirrorFinishSurcharges: List<MirrorFinishSurchargeDto>` (id — mirror_finish_type.id, name, surchargePercent). *(По ходу реализации обнаружена и исправлена проблема дизайна: изначально `ComponentSelectionDto`/каталог ссылались на `mirror_finish_option.id` — leaf-специфичный, несовместимый по пространству id с этим глобальным списком. Переделано на `mirror_finish_type.id` везде — см. правки в группах 2–3.)*
- [x] 4.2 `PricingSurchargesService` — заполнить новое поле через `MirrorFinishTypeRepository.findAll()`.
- [x] 4.3 Обновлены тесты `PricingSurchargesServiceTest` (пустые справочники → пустые списки; непустой справочник наценок — поля верны; непустой справочник исполнений зеркала — поля верны) и `PricingSurchargesControllerTest` (эндпоинт отдаёт известную seed-строку 30%). `compileTestJava` проходит; сам прогон не выполнялся.

## 5. Frontend — псевдо-шаг выбора исполнения зеркала

- [x] 5.1 `api/types.ts` — `ComponentCatalogDto.mirrorFinishOptions: ReferenceDto[]`; `ComponentSelectionDto.mirrorFinishTypeId?: number`; `PricingSurchargesDto` — новое поле `mirrorFinishSurcharges` со списком исполнений зеркала.
- [x] 5.2 `App.tsx` — `resolveMirrorFinishStep(configurations)` по аналогии с `resolveReverseStep`: `visible` — есть ли хотя бы одно исполнение среди `leaf.mirrorFinishOptions`; `options` — их объединение. *(Финальная правка по просьбе пользователя, после двух промежуточных версий — см. design.md: вместо одного `OptionGroup` с псевдо-вариантом «Без зеркала» — переключатель `Switch` «Нужно зеркало» (независимый от «Реверс», в одном ряду с ним) плюс отдельный `OptionGroup` с конкретными исполнениями, показываемый только когда переключатель включён.)*
- [x] 5.3 `App.tsx` — `filterByMirrorFinish(configurations, mirrorFinishEnabled, mirrorFinishTypeId)`: выключенный переключатель не сужает; включённый без выбранного исполнения сужает до leaf_type с непустым `mirrorFinishOptions`; включённый с выбранным исполнением сужает до этого исполнения. Вычисляется над `reverseFilteredConfigurations`; `collectionOptions`/`collectionFilteredConfigurations` строятся из результата обоих сужений.
- [x] 5.4 `App.tsx` — `Switch` «Нужно зеркало» в той же строке (`<Space>`), что и переключатель «Реверс» — оба независимы, отображаются одновременно с момента загрузки каталога; отдельный `OptionGroup` со списком конкретных исполнений появляется ниже, только когда переключатель включён.
- [x] 5.5 `App.tsx` — изменение переключателя «Нужно зеркало» (в любую сторону) и изменение выбранного конкретного исполнения — оба сбрасывают весь последующий выбор (коллекция и далее), как и изменение реверса. Реверс и «Нужно зеркало» — независимые пред-коллекционные псевдо-шаги (peers): изменение одного не сбрасывает другой.
- [x] 5.6 `App.tsx` — при расчёте передавать `mirrorFinishTypeId` в `selection.leaf`, если переключатель включён и выбрано конкретное исполнение. *(Правка дизайна по ходу реализации: выбор зеркала хранится в отдельных top-level state `mirrorFinishEnabled`/`mirrorFinishTypeId`, а не внутри `selection.leaf` — иначе смена конкретной модели полотна в каскаде (`handleCascadeStepChange('leaf', …)`, который целиком очищает `selection.leaf`) стирала бы уже сделанный выбор зеркала. В `selection.leaf` значение подмешивается только в момент сборки запроса в `handleCalculate`.)*

## 6. Frontend — разбивка надбавок

- [x] 6.1 `App.tsx` — загрузка `GET /api/pricing-surcharges` теперь включает список исполнений зеркала (уже загружается тем же запросом, что и раньше — доп. кода на загрузку не потребовалось, только чтение нового поля `mirrorFinishSurcharges`).
- [x] 6.2 `App.tsx` — `computeSurchargeBreakdown` — добавлена строка «За исполнение зеркала» (поиск в `pricingSurcharges.mirrorFinishSurcharges` по top-level state `mirrorFinishTypeId`, см. правку в 5.6), позиционирована между строкой высоты и строкой реверса, если исполнение зеркала выбрано.

## 7. Проверка

- [ ] 7.1 Точечный прогон новых/изменённых backend-тестов. Пропущено по умолчанию, пока пользователь явно не попросит.
- [x] 7.2 `npm run build` и `npm run lint` — без ошибок. Подтверждено.
- [x] 7.3 Проверка через живой backend (перезапущен с новым кодом) — вместо браузерного клика: `GET /api/pricing-surcharges` отдаёт все 3 mirror_finish_type (30/40/50%); `GET /api/door-configurations` — 6555 конфигураций с непустым `leaf.mirrorFinishOptions` ровно для 6 названных коллекций (Элегант, Гармония, Свобода, Геометрия, Сияние, Сибирь), 0 — для Вертикаль/Атмосфера (Фантом вовсе не имеет leaf-конфигураций), ни у одного frame — не пусто только у leaf; `POST /{id}/price` для реверсивной конфигурации коллекции Элегант — числовое подтверждение порядка «высота → зеркало → реверс» с промежуточным округлением: без зеркала 38932→42825 ₽ (retail), 22245→24470 ₽ (dealer); с исполнением 30% 38932→50612→55673 ₽ (retail), 22245→28919→31811 ₽ (dealer) — совпадает с формулой из спецификации; зеркало на компоненте `edge` и несуществующий `mirrorFinishTypeId` — оба отклонены 400. Фронтенд собирается (`npm run build`) и линтуется (`npm run lint`) без ошибок; логика UI-контрола повторяет уже проверенный в проде паттерн `resolveReverseStep`.
- [x] 7.4 `openspec validate add-mirror-finish-leaf-option --strict` — проходит без ошибок. Подтверждено.
