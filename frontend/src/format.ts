const MONEY_FORMATTER = new Intl.NumberFormat('ru-RU')

// Единый формат денежной суммы во фронтенде (см. change format-money-amounts) — целое число, группы
// разрядов через обычный пробел, без символа валюты.
export function formatMoney(value: number): string {
  return MONEY_FORMATTER.format(value)
}

// Тот же формат, но с символом «₽» — только для тех немногих мест, где сумма является итоговой ценой
// (итоговая цена в сервисе «Эмаль и шпон», итоговая цена конфигурации в корзине заказа, итоговая строка
// в детализации конфигурации, см. правку пользователя); везде остальными суммами используется formatMoney
// без символа валюты.
export function formatMoneyWithCurrency(value: number): string {
  return `${formatMoney(value)} ₽`
}

// Объединяет применимые измерения через « × », пропуская отсутствующие (null/undefined) — тот же принцип,
// что и у объединения размеров полотна (длина × высота × толщина) и у формата колонки «Измерения» в
// backend-выгрузке (см. change add-decorative-element-sandriks).
export function formatDimensions(...values: Array<number | null | undefined>): string {
  return values.filter((value): value is number => value !== null && value !== undefined).join(' × ')
}
