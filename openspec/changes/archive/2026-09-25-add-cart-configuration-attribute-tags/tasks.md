## 1. Модель данных корзины

- [x] 1.1 `frontend/src/cart.ts`: новое поле `attributeTags: string[]` в `CartItemContent` (наследуется `CartItem`) — список уже применимых, готовых к отображению тегов конфигурации.

## 2. Вычисление тегов в конфигураторе

- [x] 2.1 `frontend/src/ConfiguratorScreen.tsx`, `buildCartItemContent()`: вычислить `attributeTags` из уже читаемых в этой функции/доступных в замыкании значений — `resolvedReverse` → «РЕВЕРС»; `resolvedHasQuarter` → «ЧЕТВЕРТЬ»; `resolvedPanelType === 'GLAZED'` → «ОСТЕКЛЕНИЕ»; `resolvedPanelType === 'MIRRORED'` → «ЗЕРКАЛО»; `leafThicknessValue === THICKNESS_REQUIRING_QUARTER_MM` → «ТОЛЩИНА 59»; `selection.leaf.doubleSidedPainting === true` → «ДВУСТОРОННЯЯ». Порядок — в этой последовательности, неприменимые атрибуты пропускаются (не пустая строка). Включить `attributeTags` в возвращаемый `CartItemContent`.

## 3. Отображение в корзине

- [x] 3.1 `frontend/src/CartScreen.tsx`: в столбце «Конфигурация», в существующем `<Space>` с тегами `SERVICE_TAG_LABEL`/`ITEM_STATUS_LABEL`, после них отрендерить `item.attributeTags` (каждый — `Tag`, цвет на усмотрение реализации, например нейтральный/default, чтобы не путать с зелёным сервисным и синим тегом вида изделия). Отсутствующее поле (позиции, добавленные до этого изменения) трактовать как пустой список — `item.attributeTags ?? []`.

## 4. Проверка и финализация

- [x] 4.1 `npm run lint` и `npm run build` (включая `tsc -b`) — чисто.
- [ ] 4.2 Ручная/визуальная проверка не выполнена — в этой среде нет headless-браузера/playwright (та же ограниченность, что и в предыдущих changes этой сессии). Вместо этого проверено статически: `tsc -b --noEmit` и `npm run build` проходят чисто, код теговой логики (`attributeTags` в `ConfiguratorScreen.tsx`/рендер в `CartScreen.tsx`) соответствует условиям из спеки (шесть атрибутов, взаимоисключение «ОСТЕКЛЕНИЕ»/«ЗЕРКАЛО», пропуск неприменимых).
- [x] 4.3 `openspec validate "add-cart-configuration-attribute-tags" --strict` — проходит.

## 5. Уникальные яркие цвета тегов и ширина столбца (по правке пользователя)

- [x] 5.1 `frontend/src/CartScreen.tsx`: новая карта `ATTRIBUTE_TAG_COLORS` — по одному уникальному яркому предустановленному цвету antd `Tag` на каждый из шести тегов атрибутов («РЕВЕРС» → red, «ОСТЕКЛЕНИЕ» → cyan, «ЧЕТВЕРТЬ» → orange, «ТОЛЩИНА 59» → gold, «ЗЕРКАЛО» → purple, «ДВУСТОРОННЯЯ» → magenta) — ни один не совпадает с зелёным (`SERVICE_TAG_LABEL`) или синим (`ITEM_STATUS_LABEL`). Применена как `color={ATTRIBUTE_TAG_COLORS[tag] ?? 'default'}` при рендере `item.attributeTags`.
- [x] 5.2 Столбец «Конфигурация» таблицы позиций (`frontend/src/CartScreen.tsx`) получил `width: 140`, затем по дополнительным правкам пользователя увеличен до `width: 200`, затем до `width: 300`.
- [x] 5.3 Обновлена delta-спека `order-cart-ui`: «Теги атрибутов конфигурации» дополнено требованием уникального цвета каждого тега + сценарий; добавлен MODIFIED-блок «Таблица позиций корзины» с шириной столбца (со всеми уже существующими сценариями, без потери) + новый сценарий про фиксированную ширину; ширина обновлена 140 → 200 → 300px по дополнительным правкам пользователя. `openspec validate --strict` — проходит.
- [x] 5.4 `npm run lint` и `npm run build` — чисто.
