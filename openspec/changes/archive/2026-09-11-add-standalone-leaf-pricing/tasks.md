## 1. Backend: рефакторинг общего кода расчёта

- [x] 1.1 Добавить `LeafTypeRepository extends JpaRepository<LeafType, Long>` (по образцу существующих репозиториев).
- [x] 1.2 Вынести из `DoorConfigurationPricingService.calculate(...)` подсчёт итоговых сумм и цен фурнитуры в приватный переиспользуемый метод (например, `finalizeResponse(List<ComponentPriceDto> components, PricingRequestDto request)`), возвращающий `PricingResponseDto`; `calculate(...)` использует его без изменения поведения.

## 2. Backend: расчёт стоимости отдельного полотна

- [x] 2.1 Новый публичный метод `DoorConfigurationPricingService.calculateForLeaf(Long leafTypeId, PricingRequestDto request)`: найти `LeafType` по id (404, если не найден), вызвать существующий `addComponentIfPresent(..., "leaf", leafType, leafSelection, null, applyReverseSurcharge)`, где `applyReverseSurcharge` берётся из нового поля `PricingRequestDto.isReverse` (по умолчанию false — у отдельного полотна нет `door_configuration.is_reverse`), собрать ответ через `finalizeResponse(...)`.
- [x] 2.2 Новый контроллер `LeafPricingController`: `POST /api/leaf-types/{id}/price`, тело — `PricingRequestDto` (используются поля `leaf`, `hardware`, `isReverse`), делегирует в `calculateForLeaf`.
- [x] 2.3 Тесты `DoorConfigurationPricingServiceTest` для `calculateForLeaf`: успешный расчёт полотна, полотно без найденной цены, надбавка за нестандартный размер/исполнение зеркала применяется, надбавка за реверс применяется при `isReverse: true` и не применяется по умолчанию, позиции фурнитуры суммируются, несуществующий `leaf_type` — 404, опция чужого компонента — 400.
- [ ] 2.4 Интеграционный тест контроллера (по образцу существующих) на реальных данных каталога.

## 3. Frontend: использование расчёта отдельного полотна

- [x] 3.1 Новая функция API-клиента (`calculateLeafPrice(leafTypeId, request)`) для `POST /api/leaf-types/{id}/price`, переиспользующая существующие типы `PricingRequestDto`/`PricingResponseDto`.
- [x] 3.2 В `App.tsx`: в эффекте автоматического расчёта — если конкретная `door_configuration` (`selectedConfiguration`) ещё не определена, но тип полотна уже определён (есть `resolvedComponent` шага `leaf`), вызывать `calculateLeafPrice` вместо пропуска расчёта; при определении `selectedConfiguration` — переключаться обратно на `calculatePrice` по конфигурации.
- [x] 3.3 Обновить пустое состояние sticky-панели и подсказку о следующем шаге (см. предыдущую доработку) так, чтобы они относились к отсутствию типа полотна, а не отсутствию полной конфигурации.

## 4. Проверка

- [x] 4.1 `./gradlew test --tests "com.example.furniturecalculator.service.DoorConfigurationPricingServiceTest"` — без запуска полного набора тестов.
- [x] 4.2 `npm run build` и `npm run lint` во фронтенде проходят без ошибок.
- [x] 4.3 Ручная проверка в браузере: выбор только полотна (без короба/наличника/добора) показывает цену полотна в sticky-панели; последующий выбор короба и других компонентов переключает расчёт на полную конфигурацию. Подтверждено пользователем.
- [x] 4.4 Прогнать `openspec validate --strict` для изменения перед архивированием.

## 5. Порядок архивации

- [ ] 5.1 Учесть, что delta-спека `door-configurator-ui` в этом изменении модифицирует требование «Автоматический расчёт стоимости по текущему выбору», введённое ещё не заархивированным изменением `redesign-configurator-layout` — перед архивацией `add-standalone-leaf-pricing` необходимо сначала завершить и заархивировать `redesign-configurator-layout`, иначе основная спека не будет содержать модифицируемое требование.
