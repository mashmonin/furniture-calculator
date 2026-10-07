import type {
  DecorativeElementCategoryDto,
  DecorativeElementPricingRequestDto,
  DecorativeElementPricingResponseDto,
  DoorConfigurationDto,
  FrameGroupPricingRequestDto,
  FrameGroupPricingResponseDto,
  HardwareCategoryDto,
  HardwarePricingRequestDto,
  HardwarePricingResponseDto,
  OrderLineExportRequestDto,
  PricingRequestDto,
  PricingResponseDto,
  PricingSurchargesDto,
  SpecificationExportRequestDto,
} from './types'

// Каталог большой (около 8 000 конфигураций) и общий для всех сервисов (каждый сервис фильтрует его по своему
// прайс-листу, см. services.ts) — запрашивается один раз, повторные вызовы получают тот же Promise. При ошибке
// кэш сбрасывается, чтобы следующий вызов мог повторить запрос.
let doorConfigurationsPromise: Promise<DoorConfigurationDto[]> | null = null

export function fetchDoorConfigurations(): Promise<DoorConfigurationDto[]> {
  if (!doorConfigurationsPromise) {
    doorConfigurationsPromise = loadDoorConfigurations().catch((error: unknown) => {
      doorConfigurationsPromise = null
      throw error
    })
  }
  return doorConfigurationsPromise
}

async function loadDoorConfigurations(): Promise<DoorConfigurationDto[]> {
  const response = await fetch('/api/door-configurations')
  if (!response.ok) {
    throw new Error(`Не удалось загрузить каталог конфигураций (HTTP ${response.status})`)
  }
  return (await response.json()) as DoorConfigurationDto[]
}

export async function fetchHardwareCatalog(): Promise<HardwareCategoryDto[]> {
  const response = await fetch('/api/hardware-catalog')
  if (!response.ok) {
    throw new Error(`Не удалось загрузить каталог фурнитуры (HTTP ${response.status})`)
  }
  return (await response.json()) as HardwareCategoryDto[]
}

// Каталог декоративных элементов (см. change add-decorative-elements-plinth) — тем же принципом, что и
// fetchHardwareCatalog.
export async function fetchDecorativeElementsCatalog(): Promise<DecorativeElementCategoryDto[]> {
  const response = await fetch('/api/decorative-elements-catalog')
  if (!response.ok) {
    throw new Error(`Не удалось загрузить каталог декоративных элементов (HTTP ${response.status})`)
  }
  return (await response.json()) as DecorativeElementCategoryDto[]
}

export async function fetchPricingSurcharges(): Promise<PricingSurchargesDto> {
  const response = await fetch('/api/pricing-surcharges')
  if (!response.ok) {
    throw new Error(`Не удалось загрузить проценты надбавок (HTTP ${response.status})`)
  }
  return (await response.json()) as PricingSurchargesDto
}

// Расчёт этапа «Полотно» (+ кромка, если определена) в отрыве от короба/обрамления/фурнитуры — этап
// всегда независим от остальных двух (см. change add-standalone-leaf-pricing, add-staged-pricing-endpoints,
// frontend-staged-pricing).
export async function calculateLeafPrice(
  leafTypeId: number,
  selection: PricingRequestDto,
): Promise<PricingResponseDto> {
  const response = await fetch(`/api/leaf-types/${leafTypeId}/price`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(selection),
  })
  if (!response.ok) {
    throw new Error(`Не удалось рассчитать стоимость (HTTP ${response.status})`)
  }
  return (await response.json()) as PricingResponseDto
}

// Расчёт этапа «Короб и обрамление» (короб, опционально наличник и добор) независимо от полотна,
// кромки и фурнитуры (см. change add-staged-pricing-endpoints, frontend-staged-pricing).
export async function calculateFrameGroupPrice(
  frameTypeId: number,
  request: FrameGroupPricingRequestDto,
): Promise<FrameGroupPricingResponseDto> {
  const response = await fetch(`/api/frame-types/${frameTypeId}/price`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    throw new Error(`Не удалось рассчитать стоимость (HTTP ${response.status})`)
  }
  return (await response.json()) as FrameGroupPricingResponseDto
}

// Расчёт этапа «Фурнитура» независимо от door_configuration и её компонентов (см. change
// add-staged-pricing-endpoints, frontend-staged-pricing).
export async function calculateHardwarePrice(
  request: HardwarePricingRequestDto,
): Promise<HardwarePricingResponseDto> {
  const response = await fetch('/api/hardware/price', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    throw new Error(`Не удалось рассчитать стоимость (HTTP ${response.status})`)
  }
  return (await response.json()) as HardwarePricingResponseDto
}

// Расчёт этапа «Декоративные элементы» независимо от door_configuration и её компонентов (см. change
// add-decorative-elements-plinth) — тем же принципом, что и calculateHardwarePrice.
export async function calculateDecorativeElementsPrice(
  request: DecorativeElementPricingRequestDto,
): Promise<DecorativeElementPricingResponseDto> {
  const response = await fetch('/api/decorative-elements/price', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    throw new Error(`Не удалось рассчитать стоимость (HTTP ${response.status})`)
  }
  return (await response.json()) as DecorativeElementPricingResponseDto
}

const DEFAULT_SPECIFICATION_FILENAME = 'specification.xlsx'

// Имя файла — из Content-Disposition ответа, если он его содержит, иначе запасное имя по умолчанию
// (см. design.md изменения add-specification-export, «Скачивание на фронте»). Backend кодирует кириллицу
// по RFC 5987 (filename*=UTF-8''%D0%97...), а не как обычный filename="..." — иначе Tomcat падает при
// записи заголовка (см. UnmappableCharacterException, ISO-8859-1 не вмещает кириллицу).
function filenameFromContentDisposition(header: string | null): string {
  const extendedMatch = header?.match(/filename\*=UTF-8''([^;]+)/i)
  if (extendedMatch) {
    try {
      return decodeURIComponent(extendedMatch[1])
    } catch {
      // Битый процент-энкодинг — падаем на обычный filename ниже.
    }
  }
  const match = header?.match(/filename="?([^";]+)"?/)
  return match ? match[1] : DEFAULT_SPECIFICATION_FILENAME
}

// Выгрузка .xlsx-спецификации по собранной конфигурации (см. change add-specification-export) — backend
// пересчитывает цены заново теми же правилами, что и три этапных эндпоинта, не доверяя уже показанным на
// экране числам.
export async function exportSpecification(
  request: SpecificationExportRequestDto,
): Promise<{ blob: Blob; filename: string }> {
  const response = await fetch('/api/specification/export', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    throw new Error(`Не удалось сформировать спецификацию (HTTP ${response.status})`)
  }
  const blob = await response.blob()
  const filename = filenameFromContentDisposition(response.headers.get('Content-Disposition'))
  return { blob, filename }
}

// Выгрузка коммерческого предложения (PDF) по всем позициям корзины (см. change add-commercial-offer-pdf-export,
// commercial-offer-export) — те же позиции, что и в exportOrder, на выходе PDF вместо .xlsx.
// orderNote — текст поля «Заказ» для шапки КП; пустой (после trim) не передаётся.
export async function exportOffer(
  lines: OrderLineExportRequestDto[],
  orderNote?: string,
): Promise<{ blob: Blob; filename: string }> {
  const note = orderNote?.trim()
  const query = note ? `?orderNote=${encodeURIComponent(note)}` : ''
  const response = await fetch(`/api/specification/export-offer${query}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(lines),
  })
  if (!response.ok) {
    throw new Error(`Не удалось сформировать коммерческое предложение (HTTP ${response.status})`)
  }
  const blob = await response.blob()
  const filename = filenameFromContentDisposition(response.headers.get('Content-Disposition'))
  return { blob, filename }
}

// Выгрузка всего заказа (корзины) одним .xlsx (см. change add-order-cart-screen, order-export-api) — тот же
// принцип, что и exportSpecification, но список позиций вместо одной конфигурации.
export async function exportOrder(lines: OrderLineExportRequestDto[]): Promise<{ blob: Blob; filename: string }> {
  const response = await fetch('/api/specification/export-order', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(lines),
  })
  if (!response.ok) {
    throw new Error(`Не удалось сформировать файл заказа (HTTP ${response.status})`)
  }
  const blob = await response.blob()
  const filename = filenameFromContentDisposition(response.headers.get('Content-Disposition'))
  return { blob, filename }
}
