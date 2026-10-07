import type { LeafPanelType } from './api/types'

// Сервисы приложения (пункты левого меню) — по принципу «один сервис = один прайс-лист» (см. change
// add-emal-layt-service). Таблицы сервисов в БД нет: сервис задаётся здесь кодом прайс-листа, а конфигуратор
// показывает только конфигурации, у которых leaf.priceList.code совпадает с priceListCode сервиса.
export interface ServiceDef {
  key: string
  label: string
  priceListCode: string
  // Текст тега сервиса в корзине (экран «Корзина заказа»).
  cartTag: string
  // Толщина полотна (мм), которая выбирается автоматически после выбора модели, если толщина ещё не выбрана;
  // у сервиса без значения толщина по умолчанию не выбирается.
  defaultThicknessMm?: number
  // Тип полотна, который в сервисе всегда выбран: группа «Тип полотна» отображается с единственной выбранной
  // кнопкой, которую нельзя снять; у сервиса без значения поведение группы определяется каталогом.
  fixedPanelType?: LeafPanelType
  // Допускается ли переключатель «Двустороннее» (двусторонняя покраска с надбавкой); false — скрыт.
  // У сервиса без значения переключатель показывается, как раньше (когда у полотна есть цвета).
  allowDoubleSided?: boolean
  // Вид сервиса: 'configurator' (по умолчанию) — конфигуратор двери; 'hardware' — экран подбора фурнитуры для
  // дверей из корзины (см. change add-hardware-service), у него нет конфигуратора и позиций корзины своего сервиса.
  kind?: 'configurator' | 'hardware'
}

export const SERVICES: ServiceDef[] = [
  { key: 'emal-i-shpon', label: 'Эмаль и шпон', priceListCode: 'PL-001', cartTag: 'ЭМАЛЬ И ШПОН' },
  { key: 'emal-layt', label: 'Эмаль лайт', priceListCode: 'PL-002', cartTag: 'ЭМАЛЬ ЛАЙТ', defaultThicknessMm: 44, fixedPanelType: 'BLIND', allowDoubleSided: false },
  { key: 'furnitura', label: 'Фурнитура', priceListCode: 'PL-003', cartTag: 'ФУРНИТУРА', kind: 'hardware' },
]

// Сервисы с конфигуратором двери (все, кроме экрана «Фурнитура»).
export const CONFIGURATOR_SERVICES: ServiceDef[] = SERVICES.filter((service) => service.kind !== 'hardware')

export const DEFAULT_SERVICE: ServiceDef = SERVICES[0]

// Позиции корзины, сохранённые до появления нескольких сервисов, не содержат serviceKey — это «Эмаль и шпон».
export function serviceByKey(key: string | undefined): ServiceDef {
  return SERVICES.find((service) => service.key === key) ?? DEFAULT_SERVICE
}
