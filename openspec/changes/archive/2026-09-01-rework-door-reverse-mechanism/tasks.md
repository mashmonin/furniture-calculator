## 1. Схема и данные (Liquibase)

- [x] 1.1 Новый changeset: `ADD COLUMN door_configuration.is_reverse BOOLEAN NOT NULL DEFAULT false`.
- [x] 1.2 Новый changeset: CHECK-констрейнт «is_reverse = true ⇒ frame_type_id IS NOT NULL» на `door_configuration` (по аналогии с существующим `chk_door_configuration_casing_extensions_require_frame`).
- [x] 1.3 Новый changeset: пересобрать уникальный индекс `uk_door_configuration_combination`, добавив `is_reverse` в список колонок (DROP INDEX + CREATE UNIQUE INDEX с тем же COALESCE-паттерном для остальных колонок).
- [x] 1.4 Новый changeset: скопировать все `door_configuration` с `frame_type_id = FT-004` в новые строки с `frame_type_id = FT-002` и `is_reverse = true`, сохранив leaf_type_id/edge_type_id/door_casing_type_id/frame_extensions_type_id как есть — одним `INSERT ... SELECT` с join по кодам `FT-004`/`FT-002` (без ручного перечисления коллекций).
- [x] 1.5 Новый changeset: `DELETE FROM door_configuration WHERE frame_type_id = (SELECT id FROM frame_type WHERE code = 'FT-004')`.
- [x] 1.6 Новый changeset: `DELETE FROM frame_post WHERE frame_type_id = (SELECT id FROM frame_type WHERE code = 'FT-004')`.
- [x] 1.7 Новый changeset: `DELETE FROM frame_type WHERE code = 'FT-004'`.
- [x] 1.8 Новый changeset: `ALTER TABLE frame_type DROP COLUMN is_reverse`.
- [x] 1.9 Подключить все changeset'ы из 1.1–1.8 в `db.changelog-master.yaml` по порядку (одним или несколькими файлами, но в указанной последовательности — колонка/констрейнт/индекс до переноса данных, перенос данных до удаления FT-004).
- [x] 1.10 Прогнать `docker compose up -d` + `./gradlew bootRun` (или тестовый liquibase update) локально — миграции применяются без ошибок на текущей БД с уже накопленными данными.
- [x] 1.11 Проверить через SQL/API: количество `door_configuration` с `is_reverse = true` после миграции равно количеству, которое раньше было с `frame_type_id = FT-004`; для каждой такой строки `frame_type_id` теперь указывает на FT-002; наличники DCT-005/007/009 по-прежнему существуют и используются этими строками.

## 2. Backend

- [x] 2.1 `FrameType.java` — убрать поле `reverse`/аннотацию `@Column(name = "is_reverse")`.
- [x] 2.2 `DoorConfiguration.java` — добавить `@Column(name = "is_reverse", nullable = false) private boolean reverse;` (геттер `isReverse()` через Lombok, как у `FrameType` сейчас).
- [x] 2.3 `DoorConfigurationPricingService.calculate()` — заменить `configuration.getFrameType().isReverse()` на `configuration.isReverse()`; убедиться, что `reverseFrameSelected`-переменная (или её замена) по-прежнему передаётся только для leaf-компонента, а для остальных — `false`, как раньше.
- [x] 2.4 `DoorConfigurationDto` — добавить поле `boolean reverse`.
- [x] 2.5 `DoorConfigurationCatalogService.toDto()` — прокинуть `configuration.isReverse()` в новое поле DTO.
- [x] 2.6 Обновить/добавить unit-тесты `DoorConfigurationPricingServiceTest`: надбавка применяется при `configuration.isReverse() == true` независимо от `frameType`; не применяется при `false`; регрессия — существующие тесты про надбавку (`надбавка_за_реверс_*`) переписаны через `TestEntities.doorConfiguration(...)` с новым флагом вместо `TestEntities.frameTypeReverse(...)` (или обновить `TestEntities`, если у неё есть фабрика для реверсивного `frameType`, — завести аналогичную для `doorConfiguration` с `is_reverse = true`).
- [x] 2.7 Проверить (и поправить при необходимости) любые другие места в backend, ссылающиеся на `FrameType.isReverse()`/`reverse` (полнотекстовый поиск по репозиторию) — их не должно остаться.

## 3. Frontend

- [x] 3.1 `frontend/src/api/types.ts` — добавить `reverse: boolean` в `DoorConfigurationDto`.
- [x] 3.2 `App.tsx` — вычислить доступность и текущее значение переключателя «Реверс» для текущего частичного выбора (полотно + кромка): если среди `candidates` есть и `reverse=false`, и `reverse=true` — показать переключатель (по умолчанию `false`); если только одно значение — использовать его без отображения переключателя.
- [x] 3.3 `App.tsx` — сузить `candidates` перед шагами `frame`/`doorCasing`/`frameExtensions` по текущему разрешённому значению `reverse` (аналогично тому, как уже сужаются `candidates` после каждого шага `CASCADE_ORDER`).
- [x] 3.4 Отрисовать переключатель (antd `Switch`) с лейблом «Реверс» между блоками шагов «Кромка» и «Короб».
- [x] 3.5 При изменении переключателя — сбросить выбор `frame`/`doorCasing`/`frameExtensions` и опции размеров/цвета/результат расчёта, как это уже происходит при смене любого более раннего шага каскада.
- [x] 3.6 При изменении полотна/кромки — пересчитать доступность/значение переключателя заново (может измениться набор допустимых `reverse`-значений).

## 4. Проверка

- [x] 4.1 `./gradlew test` — все тесты проходят (BUILD SUCCESSFUL; заняло необычно долго — 17 минут вместо обычных ~5 — из-за существующей N+1-неэффективности `DoorConfigurationCatalogService.colourOptionsFor` на возросшем объёме door_configuration, не связанной с этим change).
- [x] 4.2 Через прямые вызовы REST API (или в браузере) проверить: коллекция с выделенным реверс-наличником (например Элегант) — переключатель показывает оба режима, включение сужает короб до КОМПЛАНАР и наличник до реверс-варианта (Аура-реверс), расчёт стоимости даёт надбавку +10% к полотну. **Проверено через API**: для Элегант при is_reverse=true доступны наличники DCT-005 (Авеню-реверс), None, DCT-007 (Аура-реверс); frame всегда FT-002.
- [x] 4.3 Аналогично проверить коллекцию без выделенного реверс-наличника (Сияние или Сибирь) — переключатель тоже работает, наличник в обоих режимах отсутствует, но конфигурации не путаются между собой (ровно одна строка door_configuration определяется после выбора реверса). **Проверено через API**: Сияние (3 строки) и Сибирь (18 строк) с is_reverse=true — во всех наличник NULL, без дублей.
- [x] 4.4 ~~Проверить коллекцию без реверса вообще (Гармония)~~ — уточнение по факту: Гармония, вопреки исходному предположению в proposal/design, тоже имеет реверс-конфигурации (унаследованы от Элегант через клонирование коллекции в старой миграции 0037, не связано с этим change). Коллекции без реверса вообще среди активных нет — пункт снят.
- [x] 4.5 Проверить, что короб НЕО как и раньше недоступен при включённом реверсе (не появляется в списке короба, если для этой пары полотно+кромка включён реверс). **Проверено через API**: 0 строк door_configuration с frame_type=FT-003 и is_reverse=true.
- [x] 4.6 `npm run build` и `npm run lint` — без ошибок.

## 5. Спецификации

- [x] 5.1 `openspec validate rework-door-reverse-mechanism --strict` — проходит без ошибок перед архивированием.

## 6. Наличник Эво для реверс-комплектаций (доп. запрос пользователя)

- [x] 6.1 Новый changeset: для каждой строки `door_configuration` с `is_reverse = true` и `door_casing_type_id IS NULL` добавить парную строку с тем же leaf_type/frame_type/edge_type/frame_extensions_type, `door_casing_type_id` = DCT-001 «Эво» и `is_reverse = true` (переиспользуется существующий наличник, без нового door_casing_type).
- [x] 6.2 Подключить changeset в `db.changelog-master.yaml`.
- [x] 6.3 Прогнать миграцию локально и проверить через SQL/API: наличник Эво теперь доступен для всех реверс-конфигураций (все 8 коллекций, включая Гармонию), количество новых строк равно количеству реверс-комбинаций leaf+edge (132 на момент проверки). **Подтверждено**: 132 новые строки, Эво доступен во всех 8 коллекциях.
- [x] 6.4 `openspec validate rework-door-reverse-mechanism --strict` — повторно, после добавления этого раздела.

## 7. Реверс-исполнение для короба ФАНТОМ (доп. запрос пользователя)

- [x] 7.1 Новый changeset: для каждой строки `door_configuration` с `frame_type_id = FT-001` и `is_reverse = false` добавить парную строку с тем же leaf_type/edge_type/frame_extensions_type (door_casing_type всегда NULL у ФАНТОМ), `frame_type_id = FT-001`, `is_reverse = true`. Цветовые опции короба (`colour_option`/`configuration_price` на FT-001) переиспользуются как есть — они привязаны к frame_type, а не к конкретной door_configuration, поэтому новых данных не требуют.
- [x] 7.2 Подключить changeset в `db.changelog-master.yaml`.
- [x] 7.3 Прогнать миграцию локально и проверить через SQL/API: 132 новые строки с `frame_type_id = FT-001, is_reverse = true`; для них по-прежнему доступны все 4 цвета; расчёт стоимости даёт надбавку за реверс к полотну и корректную цену короба по цвету. **Подтверждено**: leaf 32947 = 29952×1.10 (надбавка), frame 16850/9903 (цена Хром матовый, без изменений).
- [x] 7.4 Проверить через API, что при включённом переключателе «Реверс» шаг короба теперь показывает и КОМПЛАНАР, и ФАНТОМ (там, где для этой пары полотно+кромка есть обе реверс-конфигурации) — без правок фронтенда, за счёт уже общего механизма каскада. **Подтверждено**: все 132 реверс-комбинации предлагают оба короба (FT-001 и FT-002).
- [x] 7.5 `openspec validate rework-door-reverse-mechanism --strict` — повторно.

## 8. Доборы КОМПЛАНАР для реверс-конфигураций (доп. запрос пользователя)

- [x] 8.1 Новый changeset: для каждой строки `door_configuration` с `frame_type_id = FT-002`, `is_reverse = true` и `frame_extensions_type_id IS NULL` добавить 6 парных строк (по одной на каждый добор FET-008–FET-013), с тем же leaf_type/edge_type/door_casing_type, `is_reverse = true` — одним `INSERT ... SELECT ... CROSS JOIN frame_extensions_type`, без ручного перечисления коллекций/наличников (459 базовых строк × 6 доборов = 2754 новые строки).
- [x] 8.2 Подключить changeset в `db.changelog-master.yaml`.
- [x] 8.3 Прогнать миграцию локально и проверить через SQL/API: 2754 новые строки; доборы доступны при реверсе для всех наличников (Эво, Авеню-реверс, Аура-реверс, Ария-реверс, без наличника), как и в обычном режиме. **Подтверждено**: ровно 2754 строки, все 6 доборов × 5 вариантов наличника (включая None).
- [x] 8.4 `openspec validate rework-door-reverse-mechanism --strict` — повторно.
