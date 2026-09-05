import type { UpdateCheckDto } from './types'

export async function fetchUpdateCheck(): Promise<UpdateCheckDto> {
  const response = await fetch('/api/update-check')
  if (!response.ok) {
    throw new Error(`Не удалось проверить наличие обновлений (HTTP ${response.status})`)
  }
  return (await response.json()) as UpdateCheckDto
}
