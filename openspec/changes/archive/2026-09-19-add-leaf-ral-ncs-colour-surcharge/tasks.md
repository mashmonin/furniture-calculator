## 1. Схема и сид-данные

- [x] 1.1 Миграция `0102-colour-type-surcharge-percent.yaml`: добавить nullable `surcharge_percent DECIMAL` в `colour_type`; backfill всех существующих строк в 0; добавить `NOT NULL` constraint (три шага одного changelog, по образцу 0091). При необходимости — desktop-override для H2, если возникнет синтаксическая несовместимость (проверить на H2 перед коммитом).
- [x] 1.2 Миграция `0103-colour-type-ral-ncs-other.yaml`: вставить строку `colour_type` («Другой цвет из коллекции RAL и NCS», code `CT-029`, `surcharge_percent = 20`); вставить строки `colour_option`, связывающие эту строку с каждым `leaf_type` коллекций с кодами `LC-001`–`LC-007` (динамический `INSERT ... SELECT` через `collection.code IN (...)`, без перечисления конкретных id, по образцу 0101).
- [x] 1.3 Подключить обе миграции в `db.changelog-master.yaml`.
- [x] 1.4 Проверить на H2 (desktop-профиль) и на PostgreSQL (`docker compose up -d`), что миграции применяются без ошибок и данные соответствуют ожиданиям (42+1 строк `colour_type`, новые `colour_option` только для 7 коллекций).

## 2. Backend: модель и резолвинг наценки

- [x] 2.1 Добавить поле `surchargePercent` (`BigDecimal`, `@Column(name = "surcharge_percent", nullable = false)`) в `ColourType.java`.
- [x] 2.2 В `DoorConfigurationPricingService`: в месте, где для leaf-компонента уже резолвится `ColourOption` (`validatedColourOption`), вычислить множитель наценки за цвет (`1 + surchargePercent/100`, либо `ONE`, если цвет не выбран).
- [x] 2.3 Добавить новый параметр (множитель наценки за цвет) в `applySequentialSurcharges` и вставить его применение сразу после шага наценки за высоту и до шага зеркала (порядок: длина → высота → цвет → зеркало → остекление → реверс → погонаж). Обновить оба места вызова (retail/dealer price).
- [x] 2.4 Убедиться, что для компонентов, отличных от leaf, шаг наценки за цвет не влияет на цену (множитель всегда `ONE`, так как только у leaf есть `colourOption`, несущий эту конкретную наценку в этом change).

## 3. Backend: эндпоинт процентов надбавок

- [x] 3.1 Добавить `ColourSurchargeDto(Long id, String name, BigDecimal surchargePercent)` (по образцу `GlazingSurchargeDto`).
- [x] 3.2 Добавить поле `List<ColourSurchargeDto> colourSurcharges` в `PricingSurchargesDto`.
- [x] 3.3 В сервисе, формирующем ответ `GET /api/pricing-surcharges`, заполнить `colourSurcharges` из `colourTypeRepository.findAll()`.

## 4. Backend: тесты

- [x] 4.1 Тест на резолвинг наценки: выбор `colourOption`, ссылающегося на новый `colour_type` (20%), приводит к применению наценки к retail/dealer price; выбор цвета с 0% или отсутствие выбора — не приводит.
- [x] 4.2 Тест на порядок применения наценок: наценка за цвет применяется после высоты и до зеркала/остекления/реверса, с округлением на каждом шаге (расширить существующий тест последовательности наценок).
- [x] 4.3 Тест на `GET /api/pricing-surcharges`: ответ содержит `colourSurcharges` со всеми `colour_type`, включая новую позицию с 20%.
- [x] 4.4 Запускать точечно через `--tests` на конкретный класс, не полный `./gradlew test`.

## 5. Frontend

- [x] 5.1 Добавить `colourSurcharges: ColourSurchargeDto[]` в TS-тип `PricingSurchargesDto` (`frontend/src/api/types.ts`), плюс сам тип `ColourSurchargeDto`.
- [x] 5.2 В `computeSurchargeBreakdown` (`frontend/src/App.tsx`): для leaf-компонента по выбранному `colourOptionId` найти `colourOption.colourType.id` в `leafComponent.colourOptions`, затем найти соответствующую запись в `pricingSurcharges.colourSurcharges`; если процент ненулевой — добавить строку в блок «Надбавки к цене за нестандарт» (по образцу существующих строк за зеркало/остекление).
- [x] 5.3 Убедиться, что при цвете с нулевой наценкой (все существующие цвета) строка не показывается, а при отсутствии загруженных `pricingSurcharges` (ошибка загрузки) — строка не показывается, но остальной блок продолжает работать как раньше.

## 6. Ручная проверка

- [x] 6.1 Через реальный API (отдельный тестовый backend на порту 8099, PostgreSQL с применёнными миграциями 0102/0103; порт 8080 с уже запущенным из IntelliJ backend пользователя не трогали) подтверждено: выбор цвета «Другой цвет из коллекции RAL и NCS» (colourOptionId=696, colourType CT-029) для door_configuration id=5 (Вертикаль) меняет цену полотна с 29952/18720 на 35942/22464 — ровно +20% с округлением HALF_UP, как в design.md. `GET /api/pricing-surcharges` возвращает `colourSurcharges` с этой позицией (id=45, 20%). UI (App.tsx/computeSurchargeBreakdown) не тестировался вручную в браузере — инструмента для этого в текущей среде нет; логика показа строки проверена по коду и типам (tsc/build проходят), а не визуально.
- [x] 6.2 Подтверждено через каталог (`GET /api/door-configurations`): для door_configuration Вертикали colourOptions содержит CT-029, для door_configuration Сибири (id=4741) — не содержит (список цветов Сибири не включает эту позицию).
- [x] 6.3 Подтверждено: выбор обычного цвета (colourOptionId=5, RAL-9003 «Матовый Снежный») для той же конфигурации даёт ту же цену 29952/18720, что и вовсе без выбора цвета — наценка не применяется.
