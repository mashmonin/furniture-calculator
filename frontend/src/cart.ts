import type {
  HardwarePriceDto,
  HardwarePricingResponseDto,
  HardwareSelectionDto,
  PricingResponseDto,
  SpecificationExportRequestDto,
} from './api/types'

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
  // false — цена в принципе неприменима к этой строке (например, исполнение зеркала/вид остекления под
  // полотном, см. change show-mirror-glazing-in-order-detail-rows) — ценовые ячейки остаются пустыми при
  // отображении, а не «—» (в отличие от «цена не найдена», где dealerPrice/retailPrice тоже null, но
  // priceApplicable=true, см. CartScreen.DetailTable).
  priceApplicable: boolean
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
  // Ключ сервиса, в котором собрана позиция (см. services.ts); у позиций, сохранённых до появления нескольких
  // сервисов, поля нет — они считаются позициями сервиса «Эмаль и шпон» (см. serviceByKey).
  serviceKey?: string
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

// Строки «Фурнитура» детализации позиции корзины по рассчитанной фурнитуре (см. change add-hardware-service) —
// общий код конфигуратора (buildCartItemContent) и сервиса «Фурнитура»: у фурнитуры цена за единицу = сумма
// строки / количество, сумма строки уже учитывает количество (так отдаёт backend).
export function buildHardwareDetailRows(hardware: HardwarePriceDto[]): CartDetailRow[] {
  return hardware.map((item) => ({
    element: 'Фурнитура',
    name: `${item.category.name} — ${item.type.name}`,
    size: null,
    colour: item.colourName,
    quantity: item.quantity,
    dealerPrice: item.dealerPrice / item.quantity,
    retailPrice: item.retailPrice / item.quantity,
    dealerSum: item.dealerPrice,
    retailSum: item.retailPrice,
    priceApplicable: true,
  }))
}

// Позиция корзины с новым списком фурнитуры (см. change add-hardware-service, hardware-service-ui): меняются
// только exportRequest.hardware, фурнитурная часть pricingSnapshot (итоги пересчитываются как «прежний итог −
// прежняя фурнитура + новая фурнитура») и строки «Фурнитура» детализации; полотно и прочие компоненты не
// пересчитываются. priced — результат расчёта ПОЛНОГО нового списка фурнитуры позиции.
export function withHardware(
  item: CartItem,
  selections: HardwareSelectionDto[],
  priced: HardwarePricingResponseDto,
): CartItem {
  const oldRetail = item.pricingSnapshot.hardware.reduce((sum, line) => sum + line.retailPrice, 0)
  const oldDealer = item.pricingSnapshot.hardware.reduce((sum, line) => sum + line.dealerPrice, 0)
  const exportRequest = { ...item.exportRequest }
  if (selections.length > 0) {
    exportRequest.hardware = selections
  } else {
    delete exportRequest.hardware
  }
  return {
    ...item,
    exportRequest,
    pricingSnapshot: {
      ...item.pricingSnapshot,
      totalRetailPrice: item.pricingSnapshot.totalRetailPrice - oldRetail + priced.totalRetailPrice,
      totalDealerPrice: item.pricingSnapshot.totalDealerPrice - oldDealer + priced.totalDealerPrice,
      hardware: priced.hardware,
    },
    detailRows: [...item.detailRows.filter((row) => row.element !== 'Фурнитура'), ...buildHardwareDetailRows(priced.hardware)],
  }
}

// Уникальный яркий цвет на каждый тег атрибута конфигурации (см. правку пользователя, order-cart-ui, «Теги
// атрибутов конфигурации») — отличается и от зелёного SERVICE_TAG_LABEL, и от синего ITEM_STATUS_LABEL, и
// друг от друга; значения — предустановленные яркие цвета antd Tag.
export const ATTRIBUTE_TAG_COLORS: Record<string, string> = {
  'РЕВЕРС': 'red',
  'ОСТЕКЛЕНИЕ': 'cyan',
  'ЧЕТВЕРТЬ': 'orange',
  'ТОЛЩИНА 59': 'gold',
  'ЗЕРКАЛО': 'purple',
  'ДВУСТОРОННЯЯ': 'magenta',
}

// Длительность визуального эффекта «полёта» частицы (добавление конфигурации в корзину, добавление фурнитуры
// к двери) — единственный источник правды: передаётся в CSS через инлайновый `animationDuration`.
export const CART_FLY_DURATION_MS = 650
