## 1. Liquibase: реверсивность frame_type и данные

- [x] 1.1 Changeset: `addColumn: frame_type.is_reverse` (BOOLEAN NOT NULL, defaultValueBoolean: false)
- [x] 1.2 Changeset: `insert` нового `frame_type` «КОМПЛАНАР РЕВЕРС (в проем)» (код `FT-004`, `is_reverse = true`)
- [x] 1.3 Changeset: `sql` — вставить 13 `door_configuration`, связывающих каждую существующую модель полотна коллекций Вертикаль (LT-001…007) и Атмосфера (LT-008…013) с коробом `FT-004` (без наличника/добора/кромки); `leaf_type` не создаётся и не изменяется
- [x] 1.4 Подключить `0026-reverse-leaf-catalog.yaml` в `db.changelog-master.yaml`; поднять `docker compose up -d`, прогнать миграцию, проверить в БД: новый `frame_type` с `is_reverse=true`, `leaf_type` без изменений (45 строк, без новых/переименованных), 13 новых `door_configuration`

## 2. Backend: сущность, расчёт стоимости

- [x] 2.1 В `FrameType` добавить поле `reverse` (boolean, `@Column(name = "is_reverse")`, геттер `isReverse()`); `LeafType` не менять
- [x] 2.2 В `DoorConfigurationPricingService.calculate`: вычислить `reverseFrameSelected = configuration.getFrameType() != null && configuration.getFrameType().isReverse()`; передать `true` только в вызов, строящий leaf-компонент, `false` — во все остальные
- [x] 2.3 В `addComponentIfPresent`/`componentPriceFrom`: при `applyReverseSurcharge = true` прибавлять надбавку к retail/dealer цене компонента через отдельный метод `applyReverseSurcharge` (+10%, округление HALF_UP до целого, с комментарием о временности до внедрения бизнес-правил); короб при этом считается обычным образом (frame_post либо некалькулируем)
- [x] 2.4 Backend-тесты: `DoorConfigurationPricingServiceTest` — цена leaf-компонента с надбавкой при реверсивном коробе (retail и dealer отдельно), надбавка округляется до целого, надбавка не применяется при обычном коробе, надбавка не влияет на цену короба; `DoorConfigurationCatalogServiceTest` не требует изменений (каталог полотна не зависит от реверса)

## 3. Проверка

- [x] 3.1 Backend: `./gradlew test`
- [x] 3.2 Ручная проверка через API (backend перезапущен с новым кодом): каталог полотна коллекций Вертикаль/Атмосфера не содержит новых или переименованных строк (0 совпадений «РЕВЕРС», 13 уникальных leaf_type); 13 новых door_configuration связывают все модели с коробом FT-004 «КОМПЛАНАР РЕВЕРС (в проем)»; расчёт стоимости для конфигурации id=760 (LT-001 + FT-004) дал leaf=32947/20592, что равно цене обычного короба (id=18: 29952/18720) × 1.10 — совпадает точно
