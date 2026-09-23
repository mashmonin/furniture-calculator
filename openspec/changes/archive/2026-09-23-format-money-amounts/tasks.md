## 1. Общая функция форматирования

- [x] 1.1 Новый модуль `frontend/src/format.ts` с функцией `formatMoney(value: number): string` на основе `new Intl.NumberFormat('ru-RU').format(value)` — целое число, группы разрядов через обычный пробел, без символа валюты (см. design.md, Decisions).

## 2. Применение в `ConfiguratorScreen.tsx`

- [x] 2.1 Итоговая розничная и дилерская цена (`Statistic title="Розничная цена" value={...} suffix="₽"` и аналогично дилерская) — заменить на `formatter={(value) => formatMoney(Number(value))}` без `suffix`.
- [x] 2.2 Цена и базовая цена компонента в разбивке результата — не применимо к этому файлу: такие строки есть только в `ComponentBreakdownList.tsx` (см. группу 3), в `ConfiguratorScreen.tsx` их нет.
- [x] 2.3 Строка стойки короба (`{post.retailPrice} ₽ / {post.dealerPrice} ₽ (дилер)`) — заменить на `formatMoney(...)`.

## 3. Применение в `ComponentBreakdownList.tsx`

- [x] 3.1 Цена компонента (`` `${item.retailPrice} ₽ / ${item.dealerPrice} ₽ (дилер)` ``) — заменить на `formatMoney(...)`. По ходу правки обнаружена и исправлена та же строка ещё и в списке фурнитуры (одноимённый паттерн, не выделенный отдельной задачей).
- [x] 3.2 Базовая (приглушённая) цена компонента (`` `Без надбавок: ${item.baseRetailPrice} ₽ / ${item.baseDealerPrice} ₽ (дилер)` ``) — заменить на `formatMoney(...)`.

## 4. Применение в `CartScreen.tsx`

- [x] 4.1 Хелпер отображения цены за единицу/детализации (сейчас `` return value !== null ? `${value} ₽` : '—' ``) — использовать `formatMoney(value)` вместо ручной подстановки, сохранив обработку `null` → «—».
- [x] 4.2 Столбец «Цена за ед.» таблицы позиций (`` `${item.pricingSnapshot.totalRetailPrice} ₽` ``) — заменить на `formatMoney(...)`.
- [x] 4.3 Столбец «Сумма» таблицы позиций (`` `${item.pricingSnapshot.totalRetailPrice * item.quantity} ₽` ``) — заменить на `formatMoney(...)`.
- [x] 4.4 Итог по заказу (`{orderTotal} ₽`) — заменить на `formatMoney(orderTotal)`.

## 5. Проверка полноты и финализация

- [x] 5.1 `grep -rn "₽" frontend/src` — после правок не должно остаться мест, где «₽» приклеен к динамическому значению суммы (литеральные «₽» в статичных подписях типа «Прайс-лист...» не относятся к этому изменению и не трогаются, если такие есть). Проверено: совпадений не осталось вовсе (статичных подписей с «₽» в проекте и не было).
- [x] 5.2 Обновлены delta-спеки `door-configurator-ui` и `order-cart-ui` (уже созданы в этом change) — без дополнительных правок, реализация не отклонилась от них.
- [x] 5.3 `npm run lint` и `npm run build` (включая `tsc -b`) — чисто. По ходу правки в `ComponentBreakdownList.tsx` обнаружено: `retailPrice`/`dealerPrice`/`baseRetailPrice`/`baseDealerPrice` в `ComponentPriceDto` типизированы как `number | null` (TS не может связать их ненулевость с булевым `item.priced`/`hasSurcharge` без дискриминированного объединения) — `formatMoney` намеренно принимает только `number` (см. design.md), поэтому в этих трёх местах, уже находящихся внутри проверки `item.priced`/`hasSurcharge`, добавлено non-null утверждение (`!`) — то же допущение, что и раньше делал неявно шаблонный литерал `${item.retailPrice} ₽`.
- [x] 5.4 `openspec validate "format-money-amounts" --strict` — проходит.

## 6. Символ «₽» возвращён для трёх итоговых сумм (по правке пользователя)

- [x] 6.1 Новая функция `formatMoneyWithCurrency(value: number): string` в `frontend/src/format.ts` — тот же `formatMoney`, с добавленным через пробел «₽»; `formatMoney` без изменений (по-прежнему без символа валюты, используется везде, кроме трёх мест ниже).
- [x] 6.2 `ConfiguratorScreen.tsx`: итоговая розничная и дилерская цена в sticky-панели результата (`Statistic title="Розничная цена"`/`"Дилерская цена"`) — `formatter` переключён с `formatMoney` на `formatMoneyWithCurrency`.
- [x] 6.3 `CartScreen.tsx`: столбец «Цена за ед.» таблицы позиций (итоговая цена самой конфигурации) — переключён на `formatMoneyWithCurrency`; столбец «Сумма» и итог по заказу остались на `formatMoney` (без символа) — не входят в перечисленные пользователем места.
- [x] 6.4 `CartScreen.tsx`, `DetailTable`: итоговая строка «Итого» (`dealerTotal`/`retailTotal`) — переключена на `formatMoneyWithCurrency`; отдельные строки детализации (цена/сумма дилер и клиенту по каждому компоненту, через хелпер `money()`) остались на `formatMoney` (без символа).
- [x] 6.5 Обновлены delta-спеки `door-configurator-ui` и `order-cart-ui` — требования «Формат отображения денежных сумм...» переписаны: базовое правило (без «₽») остаётся общим, добавлено явное исключение для трёх сумм (итоговая цена в sticky-панели конфигуратора; «Цена за ед.» и «Итого» детализации в корзине) с отдельными сценариями на каждую. `openspec validate "format-money-amounts" --strict` — проходит.
- [x] 6.6 `npm run lint` и `npm run build` — чисто.

## 7. Символ «₽» добавлен и в столбец «Сумма» таблицы позиций корзины (по правке пользователя)

- [x] 7.1 `CartScreen.tsx`: столбец «Сумма» таблицы позиций — переключён с `formatMoney` на `formatMoneyWithCurrency`. Итог по заказу не тронут (пользователь не называл его) — остаётся на `formatMoney` без символа.
- [x] 7.2 Delta-спека `order-cart-ui`: требование «Формат отображения денежных сумм в интерфейсе корзины» переписано — символ «₽» теперь у трёх мест (было два): «Цена за ед.», «Сумма» и итоговая строка детализации; сценарий «Сумма строки таблицы показана без символа валюты» заменён на «...показана с символом валюты». `openspec validate "format-money-amounts" --strict` — проходит.
- [x] 7.3 `npm run lint` и `npm run build` — чисто.
