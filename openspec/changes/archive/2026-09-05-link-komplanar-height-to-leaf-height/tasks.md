## 1. Backend: обобщить код на набор типов короба

- [x] 1.1 В `DoorConfigurationPricingService.java` заменить `private static final String NEO_FRAME_TYPE_CODE = "FT-003";` на `private static final Set<String> HEIGHT_RANGE_FRAME_TYPE_CODES = Set.of("FT-002", "FT-003");` (добавить импорт `java.util.Set`, если отсутствует).
- [x] 1.2 Обновить оба использования (проверка обязательности высоты и вызов `validateHeightWithinLeafRange` в ветке `FrameType`) — заменить `NEO_FRAME_TYPE_CODE.equals(frameType.getCode())` на `HEIGHT_RANGE_FRAME_TYPE_CODES.contains(frameType.getCode())`.
- [x] 1.3 Убедиться, что `validateHeightWithinLeafRange` не требует изменений (уже общая).

## 2. Liquibase-миграция для короба «Компланар»

- [x] 2.1 `0064-komplanar-frame-height-configurations.yaml`: вставить 5 строк `liner_dimension_option` для `frame_type_id` = id `FT-002`, `liner_dimension_type_id` = id `DT-002` (высота), `is_standard = true`:
  - value=2170, min_value=1900, max_value=2100
  - value=2400, min_value=2150, max_value=2250
  - value=2400, min_value=2300, max_value=2300
  - value=2700, min_value=2350, max_value=2550
  - value=3000, min_value=2600, max_value=2850
  подключить в `db.changelog-master.yaml`.

## 3. Тесты backend

- [x] 3.1 `DoorConfigurationPricingServiceTest`: высота полотна внутри диапазона короба «Компланар» → расчёт выполняется (например, диапазон 2600–2850 → 3000 мм — специфичный для «Компланар», недоступный у «НЕО»).
- [x] 3.2 Высота полотна вне диапазона короба «Компланар» → 400.
- [x] 3.3 Высота короба «Компланар» выбрана без высоты полотна → 400.
- [x] 3.4 Короб «Компланар» без выбранной высоты вообще (heightOptionId=null) → 400.
- [x] 3.5 Высота полотна в разрыве между диапазонами короба «Компланар» (2900–2950) → 400.
- [x] 3.6 Высота полотна ровно 2300 выбирает опцию value=2400 с диапазоном [2300, 2300] короба «Компланар».
- [x] 3.7 Правило действует для реверсивной door_configuration (is_reverse = true) с frame_type «Компланар» — тот же набор проверок, что и для обычной.
- [x] 3.8 Регрессия: существующие тесты для короба «НЕО» (FT-003) по-прежнему проходят без изменений после замены `NEO_FRAME_TYPE_CODE` на `HEIGHT_RANGE_FRAME_TYPE_CODES`.

## 4. Frontend: обобщить код на набор типов короба

- [x] 4.1 В `frontend/src/App.tsx` заменить `const NEO_FRAME_TYPE_CODE = 'FT-003'` на `const HEIGHT_RANGE_FRAME_TYPE_CODES = ['FT-002', 'FT-003']`.
- [x] 4.2 Переименовать `frameNeoHeightOptions` → `frameHeightRangeOptions`, `neoFrameCoversHeight` → `frameCoversHeight` (сигнатуры и логика не меняются; заодно переименован внутренний хелпер `neoFrameHeightOptionCoversLeafHeight` → `frameHeightRangeOptionCoversLeafHeight`).
- [x] 4.3 Обновить использование в `buildCascadeSteps` (фильтрация кандидатов шага `frame`) — `configuration.frame?.type.code` проверяется через `HEIGHT_RANGE_FRAME_TYPE_CODES.includes(...)`, вызов `frameCoversHeight`.
- [x] 4.4 Обновить использование в `updateSelection`/сбросе высоты при смене высоты полотна — проверка типа короба через `HEIGHT_RANGE_FRAME_TYPE_CODES.includes(frameType?.code)`.
- [x] 4.5 Обновить рендер группы «Высота» — ветка для `step.key === 'frame' && HEIGHT_RANGE_FRAME_TYPE_CODES.includes(component.type.code)`, использующая `frameHeightRangeOptions`.

## 5. Проверка

- [x] 5.1 `./gradlew test --tests "*DoorConfigurationPricingServiceTest*"` (из `backend/`).
- [x] 5.2 `npm run build` (из `frontend/`) — проверка типов и сборки.
- [x] 5.3 Ручная/API-проверка: конфигурация с коробом «Компланар» — успех внутри диапазона (включая 2600–2850 → 3000 мм), 400 вне диапазона, 400 без высоты, недоступность в разрыве 2900–2950; отдельно то же для реверсивной конфигурации.
- [x] 5.4 Ручная проверка в браузере: короб «Компланар» скрыт/показан в каскаде и в группе «Высота» согласно выбранной высоте полотна, аналогично уже проверенному поведению «НЕО».
