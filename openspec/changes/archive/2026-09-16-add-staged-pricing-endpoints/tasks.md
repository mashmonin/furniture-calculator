## 1. Новые репозитории и методы существования комбинаций

- [x] 1.1 Создать `FrameTypeRepository extends JpaRepository<FrameType, Long>` (по образцу `LeafTypeRepository`).
- [x] 1.2 Создать `EdgeTypeRepository extends JpaRepository<EdgeType, Long>`.
- [x] 1.3 Создать `DoorCasingTypeRepository extends JpaRepository<DoorCasingType, Long>`.
- [x] 1.4 Создать `FrameExtensionsTypeRepository extends JpaRepository<FrameExtensionsType, Long>`.
- [x] 1.5 В `DoorConfigurationRepository` добавить производные методы существования: `existsByLeafTypeIdAndEdgeTypeId(Long leafTypeId, Long edgeTypeId)`, `existsByFrameTypeIdAndDoorCasingTypeId(Long frameTypeId, Long doorCasingTypeId)`, `existsByFrameTypeIdAndFrameExtensionsTypeId(Long frameTypeId, Long frameExtensionsTypeId)`.

## 2. Кромка в `POST /api/leaf-types/{id}/price`

- [x] 2.1 Добавить поле `Long edgeTypeId` в `PricingRequestDto` (топ-уровень, рядом с `isReverse`), обновить все конструкторы-перегрузки, чтобы дефолтили его в `null`.
- [x] 2.2 В `DoorConfigurationPricingService.calculateForLeaf` добавить резолв `edgeTypeId` → `EdgeType` (через `EdgeTypeRepository`, 404 если не найден) и проверку `doorConfigurationRepository.existsByLeafTypeIdAndEdgeTypeId(leafTypeId, edgeTypeId)` (400 если не найдено — не различать «не существует» и «недопустим для этого полотна», как у mirror_finish/glazing).
- [x] 2.3 Если `edgeTypeId` задан — вызвать существующий `addComponentIfPresent(components, "edge", edgeType, request.edge(), leafHeightValue, false)` (после расчёта leaf, используя уже вычисленный `leafHeightValue`); если не задан — не добавлять запись о кромке (текущее поведение).
- [x] 2.4 Обновить комментарии у `PricingRequestDto.edgeTypeId`/`calculateForLeaf`, аналогично существующим комментариям про `isReverse`/`mirrorFinishTypeId`/`glazingTypeId`.

## 3. Новый эндпоинт короба и обрамления

- [x] 3.1 Создать `FrameGroupPricingRequestDto(ComponentSelectionDto frame, Long doorCasingTypeId, ComponentSelectionDto doorCasing, Long frameExtensionsTypeId, ComponentSelectionDto frameExtensions, BigDecimal leafHeightValue)`.
- [x] 3.2 Создать `FrameGroupPricingResponseDto(BigDecimal totalRetailPrice, BigDecimal totalDealerPrice, List<ComponentPriceDto> components)`.
- [x] 3.3 В `DoorConfigurationPricingService` добавить метод `calculateForFrameGroup(Long frameTypeId, FrameGroupPricingRequestDto request)`: резолв `frameTypeId` → `FrameType` (404 при отсутствии); резолв `doorCasingTypeId`/`frameExtensionsTypeId` (если заданы) через новые репозитории + проверка `existsByFrameTypeIdAndDoorCasingTypeId`/`existsByFrameTypeIdAndFrameExtensionsTypeId` (400 при недопустимой комбинации); переиспользовать `addComponentIfPresent` для frame/doorCasing/frameExtensions с `leafHeightValue` из запроса (может быть `null`) и `applyReverseSurcharge = false`; собрать `FrameGroupPricingResponseDto` из итогов (без фурнитуры).
- [x] 3.4 Создать `FrameGroupPricingController` с `@RequestMapping("/api/frame-types")`, `@PostMapping("/{id}/price")`, вызывающий `calculateForFrameGroup`.

## 4. Новый эндпоинт фурнитуры

- [x] 4.1 Создать `HardwarePricingRequestDto(List<HardwareSelectionDto> hardware)`.
- [x] 4.2 Создать `HardwarePricingResponseDto(BigDecimal totalRetailPrice, BigDecimal totalDealerPrice, List<HardwarePriceDto> hardware)`.
- [x] 4.3 В `DoorConfigurationPricingService` добавить метод `calculateHardware(HardwarePricingRequestDto request)`, переиспользующий существующий `priceHardwareSelections` (сделать его пригодным для вызова из нового метода — расширить видимость при необходимости) и суммирующий итоги в `HardwarePricingResponseDto`; пустой/отсутствующий список — 200 с нулевой суммой.
- [x] 4.4 Создать `HardwarePricingController` с `@RequestMapping("/api/hardware")`, `@PostMapping("/price")`, вызывающий `calculateHardware`.

## 5. Проверка

- [x] 5.1 Точечные backend-тесты на новую логику (валидация 404/400 для frameTypeId/edgeTypeId/doorCasingTypeId/frameExtensionsTypeId, успешный расчёт каждого нового эндпоинта, учёт `leafHeightValue` в диапазонных проверках короба/наличника/добора, обратная совместимость `POST /api/leaf-types/{id}/price` без `edgeTypeId`). Запускать точечно (`./gradlew test --tests "<ИмяКласса>"`), не весь набор тестов.
- [x] 5.2 `./gradlew compileJava compileTestJava` — без ошибок компиляции.
