## 1. Liquibase: диапазон в liner_dimension_option

- [x] 1.1 Новый changeset-файл `0024-liner-dimension-option-range.yaml`: `addColumn: liner_dimension_option.min_value` (DECIMAL, nullable, без constraint'ов) — по образцу `addColumn` для `frame_post.length`
- [x] 1.2 Подключить `0024-liner-dimension-option-range.yaml` в `db.changelog-master.yaml`; поднять `docker compose up -d`, прогнать миграцию, проверить в БД, что столбец добавлен и существующие строки `liner_dimension_option` не затронуты (min_value = NULL)

## 2. Backend: сущность, валидация расчёта стоимости

- [x] 2.1 В `LinerDimensionOption` добавить nullable поле `minValue` (BigDecimal, без `nullable = false`)
- [x] 2.2 В `LinerDimensionOptionDto` добавить поле `minValue` (BigDecimal, nullable); заполнять в `DoorConfigurationCatalogService.toDto(LinerDimensionOption)` из `option.getMinValue()`
- [x] 2.3 В `DoorConfigurationPricingService`: сохранить провалидированную высоту leaf-компонента (`LinerDimensionOption`, уже вычисляется как `heightOption` для leaf при текущем порядке `COMPONENT_ORDER`) и передать её в обработку edge-компонента; добавить проверку — если у edge выбрана liner_dimension_option оси «Высота» (`linerDimensionType.code == 'DT-002'`), убедиться, что значение высоты leaf лежит в диапазоне `[minValue (или -∞, если null), value]` этой опции edge; при несоответствии или отсутствующей высоте leaf — `ResponseStatusException(BAD_REQUEST)`, как в существующей проверке `belongsToLeaf`
- [x] 2.4 Backend-тесты (`DoorConfigurationPricingServiceTest`): высота leaf внутри диапазона edge — расчёт проходит; высота leaf вне диапазона — 400; edge выбрал высоту, leaf высоту не выбрал — 400; edge не выбирал высоту вовсе — новая проверка не применяется (существующее поведение не ломается)

## 3. Frontend: условная фильтрация высоты кромки

- [x] 3.1 Обновить типы каталога (`frontend/src/api/types.ts`) — добавить `minValue: number | null` в `LinerDimensionOptionDto`
- [x] 3.2 В `App.tsx`: для edge-компонента группу «Высота» не рендерить, пока не выбрана высота leaf-компонента (`selection.leaf.heightOptionId` не задан); после выбора — передавать в `OptionGroup` только те `dimensionOptions` edge с кодом `DT-002`, чей диапазон `[minValue ?? -Infinity, value]` покрывает значение выбранной высоты leaf (найденное по `leaf.dimensionOptions`)
- [x] 3.3 В `updateSelection`/обработчике изменения `leaf.heightOptionId`: сбрасывать `selection.edge.heightOptionId` при смене высоты полотна (и скрывать ранее показанный результат расчёта, как и при прочих сбросах в этом шаге)
- [x] 3.4 Frontend: `npm run lint` и `npm run build`

## 4. Проверка

- [x] 4.1 Backend: `./gradlew test`
- [x] 4.2 Ручная проверка через API (`docker compose up -d`, backend перезапущен с новым кодом) с синтетическими тестовыми данными (edge_type с диапазонной liner_dimension_option): убедиться, что запрос расчёта с несовместимой высотой возвращает 400, а с совместимой — 200 и корректную сумму
