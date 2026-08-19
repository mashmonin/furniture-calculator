import type { DoorConfigurationDto, PricingRequestDto, PricingResponseDto } from './types'

export async function fetchDoorConfigurations(): Promise<DoorConfigurationDto[]> {
  const response = await fetch('/api/door-configurations')
  if (!response.ok) {
    throw new Error(`Не удалось загрузить каталог конфигураций (HTTP ${response.status})`)
  }
  return (await response.json()) as DoorConfigurationDto[]
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
