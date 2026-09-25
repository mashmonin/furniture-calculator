import type { PricingResponseDto, SpecificationExportRequestDto } from './api/types'

// Одна строка детализации позиции корзины по компонентам (см. order-cart-ui, «Разворачиваемая детализация
// позиции корзины») — столбцы «Элемент»/«Наименование»/«Размеры»/«Цвет»/«Кол-во»/«Цена дилер»/«Цена
// клиенту»/«Сумма дилер»/«Сумма клиенту», построены на фронте в момент добавления в корзину (см.
// ConfiguratorScreen.buildCartDetailRows) из тех же catalog/selection данных, что уже использует сам
// конфигуратор — без нового backend-эндпоинта. dealerPrice/retailPrice — цена за одну единицу, dealerSum/
// retailSum — с учётом собственного количества строки (например, количества наличника/добора/фурнитуры;
// для большинства строк, где количество=1, совпадает с ценой за единицу).
export interface CartDetailRow {
  element: string
  name: string
  size: string | null
  colour: string | null
  quantity: number
  dealerPrice: number | null
  retailPrice: number | null
  dealerSum: number | null
  retailSum: number | null
}

// Содержимое позиции корзины, которое описывает саму конфигурацию (в отличие от id/addedAt/quantity —
// метаданных самой позиции корзины, не зависящих от того, что выбрано в конфигураторе). Один и тот же набор
// полей нужен и при первом добавлении («Добавить в корзину»), и при живой синхронизации уже открытой из
// корзины позиции (см. change add-order-cart-screen, order-cart-ui, «Живая синхронизация конфигурации,
// открытой из корзины» — ConfiguratorScreen.buildCartItemContent строит это же содержимое в обоих случаях).
export interface CartItemContent {
  displayName: string
  dimensionsLabel: string
  exportRequest: SpecificationExportRequestDto
  pricingSnapshot: PricingResponseDto
  detailRows: CartDetailRow[]
  // Теги атрибутов конфигурации для столбца «Конфигурация» на экране корзины (см. order-cart-ui, «Теги
  // атрибутов конфигурации») — только применимые к этой позиции («РЕВЕРС», «ОСТЕКЛЕНИЕ», «ЧЕТВЕРТЬ»,
  // «ТОЛЩИНА 59», «ЗЕРКАЛО», «ДВУСТОРОННЯЯ»), уже готовые строки, а не булевы флаги — построены на фронте в
  // момент добавления/синхронизации тем же принципом, что и detailRows.
  attributeTags: string[]
}

// Одна позиция корзины заказа (см. change add-order-cart-screen, order-cart-ui, «Хранение корзины в
// localStorage»). exportRequest — то же тело, что сегодня уходит в POST /api/specification/export,
// переиспользуется при выгрузке всего заказа (см. order-export-api) и при открытии позиции обратно в
// конфигураторе (см. «Кнопка «Посмотреть» открывает конфигурацию в конфигураторе»). pricingSnapshot — уже
// посчитанный результат расчёта (PricingResponseDto — тот же тип, что и pricingResult в ConfiguratorScreen),
// используемый для цены за единицу; detailRows — построчная детализация по компонентам для раскрываемой
// таблицы. И то и другое обновляется живой синхронизацией, пока позиция открыта в конфигураторе (см.
// CartItemContent выше), а не только фиксируется один раз в момент первого добавления (см. design.md,
// «Модель данных корзины и хранение», с учётом правки про живую синхронизацию).
export interface CartItem extends CartItemContent {
  id: string
  addedAt: string
  quantity: number
}

const CART_STORAGE_KEY = 'door-configurator:cart'

// Повреждённые/нечитаемые данные (см. «Повреждённые данные не ломают приложение») — корзина считается
// пустой, а не бросает исключение; недоступность localStorage (приватный режим, отключённое хранилище)
// обрабатывается тем же try/catch.
export function loadCart(): CartItem[] {
  try {
    const raw = localStorage.getItem(CART_STORAGE_KEY)
    if (!raw) {
      return []
    }
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as CartItem[]) : []
  } catch {
    return []
  }
}

export function saveCart(items: CartItem[]): boolean {
  try {
    localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items))
    return true
  } catch {
    return false
  }
}

// Суммарное количество изделий по всем позициям — для счётчика в шапке (см. order-cart-ui, «Ссылка
// «Корзина» в шапке приложения») и сводки на экране корзины (см. «Сводка и итог по заказу»), не число самих
// позиций.
export function totalCartQuantity(items: CartItem[]): number {
  return items.reduce((sum, item) => sum + item.quantity, 0)
}
