import { List, Space, Typography } from 'antd'
import type { ComponentKey, ComponentPriceDto, DecorativeElementPriceDto, HardwarePriceDto } from './api/types'
import { formatDimensions, formatMoney } from './format'

export interface SurchargeBreakdownItem {
  label: string
  percent: number
}

const COMPONENT_LABELS: Record<ComponentKey, string> = {
  leaf: 'Полотно',
  frame: 'Коробка',
  edge: 'Кромка',
  doorCasing: 'Наличник',
  frameExtensions: 'Добор',
}

interface ComponentBreakdownListProps {
  components: ComponentPriceDto[]
  hardware: HardwarePriceDto[]
  decorativeElements: DecorativeElementPriceDto[]
  quantities?: Partial<Record<ComponentKey, number>>
  surchargesByComponent?: Partial<Record<ComponentKey, SurchargeBreakdownItem[]>>
}

// Разбивка результата расчёта по компонентам — общая для экрана конфигуратора (см.
// door-configurator-ui, «Отображение базовой цены рядом с ценой компонента», «Разбивка надбавок к цене
// полотна в процентах») и раскрываемой детализации позиции корзины (см. change add-order-cart-screen,
// order-cart-ui, «Разворачиваемая детализация позиции корзины») — выделена сюда, чтобы не дублировать
// разметку между ConfiguratorScreen и CartScreen. quantities/surchargesByComponent опциональны: у позиции
// корзины (замороженный `pricingSnapshot`) surcharges не хранятся отдельно — только базовая/итоговая цена.
export function ComponentBreakdownList({
  components,
  hardware,
  decorativeElements,
  quantities,
  surchargesByComponent,
}: ComponentBreakdownListProps) {
  return (
    <>
      <List
        style={{ marginTop: 16 }}
        size="small"
        bordered
        dataSource={components}
        renderItem={(item) => {
          const hasSurcharge =
            item.priced && item.baseRetailPrice !== item.retailPrice
          const key = item.component as ComponentKey
          const quantity = quantities?.[key]
          const componentSurcharges = surchargesByComponent?.[key] ?? []
          return (
            <List.Item>
              <Space direction="vertical" size={0}>
                <span>
                  {COMPONENT_LABELS[key] ?? item.component}:{' '}
                  {item.priced ? formatMoney(item.retailPrice!) : 'цена не найдена'}
                  {item.priced && quantity !== undefined && ` × ${quantity} шт.`}
                </span>
                {componentSurcharges.map((surcharge) => (
                  <Typography.Text key={surcharge.label} type="secondary" style={{ fontSize: 12 }}>
                    {surcharge.label}: +{surcharge.percent}%
                  </Typography.Text>
                ))}
                {hasSurcharge && (
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    Без надбавок: {formatMoney(item.baseRetailPrice!)}
                  </Typography.Text>
                )}
              </Space>
            </List.Item>
          )
        }}
      />
      {decorativeElements.length > 0 && (
        <List
          style={{ marginTop: 16 }}
          size="small"
          bordered
          header={<Typography.Text type="secondary">Декоративные элементы</Typography.Text>}
          dataSource={decorativeElements}
          renderItem={(item) => (
            <List.Item>
              {item.type.name} ({formatDimensions(item.lengthMm, item.widthMm, item.thicknessMm)} мм) × {item.quantity} шт.:{' '}
              {formatMoney(item.retailPrice)}
            </List.Item>
          )}
        />
      )}
      {hardware.length > 0 && (
        <List
          style={{ marginTop: 16 }}
          size="small"
          bordered
          header={<Typography.Text type="secondary">Фурнитура</Typography.Text>}
          dataSource={hardware}
          renderItem={(item) => (
            <List.Item>
              {item.category.name} — {item.type.name} ({item.colourName}) × {item.quantity} шт.:{' '}
              {formatMoney(item.retailPrice)}
            </List.Item>
          )}
        />
      )}
    </>
  )
}
