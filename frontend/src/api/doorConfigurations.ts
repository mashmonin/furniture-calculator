import type {
  DoorConfigurationDto,
  HardwareCategoryDto,
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

export async function calculatePrice(
  configurationId: number,
  selection: PricingRequestDto,
): Promise<PricingResponseDto> {
  const response = await fetch(`/api/door-configurations/${configurationId}/price`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(selection),
  })
  if (!response.ok) {
    throw new Error(`Не удалось рассчитать стоимость (HTTP ${response.status})`)
  }
  return (await response.json()) as PricingResponseDto
}
