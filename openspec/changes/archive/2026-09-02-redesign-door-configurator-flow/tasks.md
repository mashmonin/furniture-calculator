## 1. Схема и данные (Liquibase)

- [x] 1.1 Новый changeset: `ALTER TABLE door_configuration DROP CONSTRAINT chk_door_configuration_reverse_requires_frame`.
- [x] 1.2 Новый changeset: для каждой существующей уникальной пары (`leaf_type_id`, `edge_type_id`) среди строк `door_configuration` с `is_reverse = true` вставить новую строку без `frame_type_id`/`door_casing_type_id`/`frame_extensions_type_id` (`is_reverse = true`), если такой строки ещё нет — `INSERT ... SELECT DISTINCT ... WHERE NOT EXISTS (...)`, NULL-safe сравнение через `COALESCE(edge_type_id, 0)`, как в `uk_door_configuration_combination`.
- [x] 1.3 Подключить оба changeset'а в `db.changelog-master.yaml`.
- [x] 1.4 Прогнать миграцию локально (`bootRun --server.port=0`, не трогая порт 8080) и проверить: вставка `is_reverse=true` без короба больше не отклоняется; вставка `is_reverse=true` с наличником, но без короба — по-прежнему отклоняется; в `GET /api/door-configurations` появились новые строки «без короба» для реверсивных leaf+edge комбинаций. **Подтверждено**: 132 новые строки вставлены (132 различных пары leaf+edge среди реверсивных), casing-без-короба по-прежнему отклоняется констрейнтом `chk_door_configuration_casing_extensions_require_frame`, дубликат по уникальному индексу корректно отклоняется для уже вставленных пар.

## 2. Backend — эндпоинт процентов надбавок

- [x] 2.1 `DoorConfigurationPricingService` — добавить публичный метод (например, `reverseSurchargePercent()`), вычисляющий процент из уже существующей `REVERSE_SURCHARGE_MULTIPLIER`, не изменяя `calculate()`.
- [x] 2.2 Создать DTO `DimensionSurchargeRuleDto` (тип размера — id/code/name, value, surchargePercent) и `PricingSurchargesDto` (reverseSurchargePercent, список `DimensionSurchargeRuleDto`).
- [x] 2.3 Создать `PricingSurchargesController` (`GET /api/pricing-surcharges`) — читает `PricingSurchargesService`, которая инкапсулирует `DimensionSurchargeRuleRepository.findAll()` и `DoorConfigurationPricingService.reverseSurchargePercent()` (слоистая архитектура controller/service/repository).
- [x] 2.4 Добавлены тесты: `PricingSurchargesServiceTest` (Mockito, пустой справочник → пустой список + процент реверса; непустой → поля смаплены верно) и `PricingSurchargesControllerTest` (MockMvc, реальная БД — эндпоинт отдаёт 200 и известную seed-строку). Оба прошли (`--tests`).

## 3. Frontend — единый поток и реверс первым шагом

- [x] 3.1 `App.tsx` — вынести вычисление `reverseStep`/`resolvedReverse` из `buildCascadeSteps` наружу (`resolveReverseStep`), применить его к полному `configurations` (а не к `collectionFilteredConfigurations`), до вычисления `collectionOptions`.
- [x] 3.2 `App.tsx` — `collectionOptions`/`collectionFilteredConfigurations` строятся из `reverseFilteredConfigurations` (результата шага 3.1), а не из полного каталога.
- [x] 3.3 `App.tsx` — `buildCascadeSteps` упрощён: убраны `CASCADE_BEFORE_REVERSE`/`CASCADE_AFTER_REVERSE` и внутреннее вычисление `reverseStep`, единый `CASCADE_ORDER = [leaf, edge, frame, doorCasing, frameExtensions]`, применяемый к уже дважды отфильтрованным (реверс + коллекция) candidates.
- [x] 3.4 `App.tsx` — `handleReverseChange` теперь сбрасывает `selectedCollectionId`, `cascadeSelection` и `selection` целиком (не только более поздние cascade-шаги, как раньше); `handleCollectionChange`/`handleCascadeStepChange` больше не трогают `reverseSelection` (реверс — не ниже по потоку, сбрасывать нечего).

## 4. Frontend — опции сразу после типа компонента

- [x] 4.1 `App.tsx` — `applyCascadeStep` теперь вычисляет `resolvedComponent` (через `candidates.find(c => c[key]?.type.id === selectedId)?.[key]`) прямо в момент разрешения шага, не дожидаясь `selectedConfiguration`.
- [x] 4.2 `App.tsx` — рендер переписан в единый линейный список шагов: реверс → коллекция → для каждого `key` из `CASCADE_ORDER` — шаг выбора типа и сразу за ним (если `step.resolvedComponent` определён) блок его опций (длина/высота/толщина/цвет, посты короба для frame).
- [x] 4.3 Убрано разделение на визуальные блоки «1. Соберите конфигурацию» / «2. Выберите опции» — единый заголовок «Введите данные двери».
- [x] 4.4 `leafHeightValue` теперь берётся из `resolvedComponent` шага `leaf` в `cascadeSteps` (а не из `selectedConfiguration?.leaf`, которого могло ещё не быть) — условное отображение высоты кромки продолжает работать и доступно раньше, сразу после ввода высоты полотна.

## 5. Frontend — разбивка надбавок в процентах

- [x] 5.1 `api/types.ts` — добавить типы `DimensionSurchargeRuleDto`/`PricingSurchargesDto`, соответствующие backend DTO.
- [x] 5.2 `api/doorConfigurations.ts` — добавить клиентский метод получения `GET /api/pricing-surcharges`.
- [x] 5.3 `App.tsx` — проценты надбавок загружаются отдельным `useEffect` параллельно с каталогом; ошибка загрузки перехватывается молча (не блокирует каскад/расчёт, только отключает разбивку).
- [x] 5.4 `App.tsx` — `computeSurchargeBreakdown` вычисляет применимые надбавки (длина/DT-001, высота/DT-002, реверс) и рендерится как `List` (bordered, по аналогии со списком компонентов) рядом с итоговой ценой; при отсутствии надбавок блок не показывается. (Изначально отрисовано `Tag`-облаком — заменено на `List` по просьбе пользователя после ручной проверки.)

## 6. Проверка

- [ ] 6.1 Точечный прогон затронутых backend-тестов. Пропущено по просьбе пользователя в этой сессии («не запускай backend тесты») — точечный прогон `PricingSurchargesServiceTest`/`PricingSurchargesControllerTest` уже был выполнен и прошёл ранее, при реализации 2.4 (до этой просьбы); повторный/более широкий прогон (включая `DoorConfigurationApiIntegrationTest` после миграции 1.x) не выполнялся. Прогнать перед архивированием, когда пользователь разрешит.
- [x] 6.2 `npm run build` и `npm run lint` — без ошибок. **Подтверждено**.
- [x] 6.3 Ручная проверка в браузере пользователем — найдены и исправлены 2 регрессии единого потока (см. ниже); пользователь подтвердил, что всё работает («всё работает, можно архивировать»).
  - [x] 6.3.1 **Баг**: при выборе/смене типа компонента (`handleCascadeStepChange`) сбрасывались опции ВСЕХ компонентов (`setSelection(emptySelection())`), включая уже заполненные для более ранних шагов — например, ввод опций полотна стирался при выборе кромки. Исправлено: сбрасываются только опции изменённого шага и всех последующих по каскаду, более ранние сохраняются.
  - [x] 6.3.2 **Баг**: результат расчёта не скрывался при изменении длины/толщины/цвета (кроме высоты полотна) — оставался показанным и неактуальным после правки опций. Исправлено: `updateSelection` теперь всегда сбрасывает `pricingResult`/`pricingError`, а не только при изменении высоты полотна.
- [x] 6.4 `openspec validate redesign-door-configurator-flow --strict` — проходит без ошибок. **Подтверждено**.

## 7. Базовая цена у каждого компонента разбивки

- [x] 7.1 `ComponentPriceDto` — добавить поля `baseRetailPrice`/`baseDealerPrice` (аддитивное расширение контракта ответа `POST /api/door-configurations/{id}/price` на уровне объекта компонента; контракт запроса и итоговые суммы не меняются).
- [x] 7.2 `DoorConfigurationPricingService` — `componentPriceFrom` заполняет `baseRetailPrice`/`baseDealerPrice` из `ConfigurationPrice.retailPrice/dealerPrice` до `applySequentialSurcharges` (точное значение, не приближение делением); `framePostPrice` — базовая цена равна итоговой (короб надбавок не имеет). *(Первая итерация добавляла агрегированные `totalRetailPriceBeforeSurcharges`/`totalDealerPriceBeforeSurcharges` на `PricingResponseDto` через промежуточный `ComponentPricing` — отклонено пользователем: базовая цена должна относиться к каждому компоненту, а не быть только суммой; `ComponentPricing` удалён, поля перенесены в `ComponentPriceDto`.)*
- [x] 7.3 `api/types.ts` — добавить поля `baseRetailPrice`/`baseDealerPrice` в `ComponentPriceDto` (убраны ранее добавленные `totalRetailPriceBeforeSurcharges`/`totalDealerPriceBeforeSurcharges` из `PricingResponseDto`).
- [x] 7.4 `App.tsx` — в списке компонентов результата расчёта для каждого элемента, чья базовая цена отличается от итоговой, показать базовую цену вторичным (менее заметным, мельче) текстом под ценой этого элемента; для остальных компонентов — не показывать (совпадает с итоговой).
- [x] 7.5 `./gradlew compileJava compileTestJava` — без ошибок (backend-тесты не запускались по просьбе пользователя). `npm run build`/`lint` — без ошибок.
- [x] 7.6 Ручная проверка в браузере: для компонента с применённой надбавкой (leaf при нестандартном размере/реверсе) базовая цена видна под его итоговой ценой и меньше её; у остальных компонентов базовая цена не показывается. **Подтверждено пользователем**.
