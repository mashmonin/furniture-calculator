import type {
  DoorConfigurationDto,
  FrameGroupPricingRequestDto,
  FrameGroupPricingResponseDto,
  HardwareCategoryDto,
  HardwarePricingRequestDto,
  HardwarePricingResponseDto,
  PricingRequestDto,
  PricingResponseDto,
  PricingSurchargesDto,
} from './types'

export async function fetchDoorConfigurations(): Promise<DoorConfigurationDto[]> {
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
