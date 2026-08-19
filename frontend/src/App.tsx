import { useEffect, useState } from 'react'
import { Alert, Button, Card, Divider, Empty, List, Radio, Space, Spin, Statistic, Typography } from 'antd'
import type { RadioChangeEvent } from 'antd'
import { calculatePrice, fetchDoorConfigurations } from './api/doorConfigurations'
import type {
  ComponentKey,
  ComponentSelectionDto,
  DoorConfigurationDto,
  PricingRequestDto,
  PricingResponseDto,
} from './api/types'
import { OptionGroup } from './components/OptionGroup'
import './App.css'

const COMPONENT_ORDER: ComponentKey[] = ['leaf', 'frame', 'edge', 'doorCasing', 'frameExtensions']

const COMPONENT_LABELS: Record<ComponentKey, string> = {
  leaf: 'Полотно',
  frame: 'Коробка',
  edge: 'Кромка',
  doorCasing: 'Наличник / добор',
  frameExtensions: 'Удлинители',
}

// Коды типов размера из справочника liner_dimension_type (см. db.changelog 0004) — стабильные бизнес-ключи.
const LENGTH_TYPE_CODE = 'DT-001'
const HEIGHT_TYPE_CODE = 'DT-002'
const THICKNESS_TYPE_CODE = 'DT-003'

function emptySelection(): Record<ComponentKey, ComponentSelectionDto> {
  return {
    leaf: {},
    frame: {},
    edge: {},
    doorCasing: {},
    frameExtensions: {},
  }
}

function configurationLabel(configuration: DoorConfigurationDto): string {
  return COMPONENT_ORDER.map((key) => configuration[key]?.type.name)
    .filter((name): name is string => Boolean(name))
    .join(' · ')
}

function App() {
  const [configurations, setConfigurations] = useState<DoorConfigurationDto[]>([])
  const [catalogLoading, setCatalogLoading] = useState(true)
  const [catalogError, setCatalogError] = useState<string | null>(null)

  const [selectedConfigurationId, setSelectedConfigurationId] = useState<number>()
  const [selection, setSelection] = useState(emptySelection)

  const [pricingResult, setPricingResult] = useState<PricingResponseDto | null>(null)
  const [pricingLoading, setPricingLoading] = useState(false)
  const [pricingError, setPricingError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setCatalogLoading(true)
    setCatalogError(null)
    fetchDoorConfigurations()
      .then((data) => {
        if (!cancelled) {
          setConfigurations(data)
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setCatalogError(error instanceof Error ? error.message : 'Не удалось загрузить каталог конфигураций')
        }
      })
      .finally(() => {
        if (!cancelled) {
          setCatalogLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  const selectedConfiguration = configurations.find((c) => c.id === selectedConfigurationId)

  function handleSelectConfiguration(event: RadioChangeEvent) {
    setSelectedConfigurationId(event.target.value as number)
    setSelection(emptySelection())
    setPricingResult(null)
    setPricingError(null)
  }

  function updateSelection(key: ComponentKey, patch: Partial<ComponentSelectionDto>) {
    setSelection((prev) => ({
      ...prev,
      [key]: { ...prev[key], ...patch },
    }))
  }

  async function handleCalculate() {
    if (!selectedConfiguration) {
      return
    }
    setPricingLoading(true)
    setPricingError(null)
    try {
      const request: PricingRequestDto = {}
      for (const key of COMPONENT_ORDER) {
        if (selectedConfiguration[key]) {
          request[key] = selection[key]
        }
      }
      const result = await calculatePrice(selectedConfiguration.id, request)
      setPricingResult(result)
    } catch (error) {
      setPricingError(error instanceof Error ? error.message : 'Не удалось рассчитать стоимость')
    } finally {
      setPricingLoading(false)
    }
  }

  return (
    <div className="page">
      <Typography.Title level={2}>Конфигуратор межкомнатных дверей</Typography.Title>

      <Typography.Title level={4}>1. Выберите конфигурацию</Typography.Title>
      {catalogLoading && <Spin />}
      {catalogError && <Alert type="error" message={catalogError} showIcon />}
      {!catalogLoading && !catalogError && configurations.length === 0 && (
        <Empty description="Нет доступных конфигураций" />
      )}
      {!catalogLoading && !catalogError && configurations.length > 0 && (
        <Radio.Group value={selectedConfigurationId} onChange={handleSelectConfiguration}>
          <Space wrap>
            {configurations.map((configuration) => (
              <Radio.Button key={configuration.id} value={configuration.id}>
                {configurationLabel(configuration)}
              </Radio.Button>
            ))}
          </Space>
        </Radio.Group>
      )}

      {selectedConfiguration && (
        <>
          <Divider />
          <Typography.Title level={4}>2. Выберите опции</Typography.Title>
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            {COMPONENT_ORDER.map((key) => {
              const component = selectedConfiguration[key]
              if (!component) {
                return null
              }
              return (
                <Card key={key} size="small" title={`${COMPONENT_LABELS[key]}: ${component.type.name}`}>
                  <Space direction="vertical" size="middle">
                    <OptionGroup
                      label="Длина"
                      options={component.dimensionOptions
                        .filter((option) => option.dimensionType.code === LENGTH_TYPE_CODE)
                        .map((option) => ({ id: option.id, label: String(option.value) }))}
                      selectedId={selection[key].lengthOptionId}
                      onChange={(id) => updateSelection(key, { lengthOptionId: id })}
                    />
                    <OptionGroup
                      label="Высота"
                      options={component.dimensionOptions
                        .filter((option) => option.dimensionType.code === HEIGHT_TYPE_CODE)
                        .map((option) => ({ id: option.id, label: String(option.value) }))}
                      selectedId={selection[key].heightOptionId}
                      onChange={(id) => updateSelection(key, { heightOptionId: id })}
                    />
                    <OptionGroup
                      label="Толщина"
                      options={component.dimensionOptions
                        .filter((option) => option.dimensionType.code === THICKNESS_TYPE_CODE)
                        .map((option) => ({ id: option.id, label: String(option.value) }))}
                      selectedId={selection[key].thicknessOptionId}
                      onChange={(id) => updateSelection(key, { thicknessOptionId: id })}
                    />
                    <OptionGroup
                      label="Цвет"
                      options={component.colourOptions.map((option) => ({
                        id: option.id,
                        label: option.colourType.name,
                      }))}
                      selectedId={selection[key].colourOptionId}
                      onChange={(id) => updateSelection(key, { colourOptionId: id })}
                    />
                  </Space>
                </Card>
              )
            })}
          </Space>

          <Divider />
          <Typography.Title level={4}>3. Расчёт стоимости</Typography.Title>
          <Button type="primary" loading={pricingLoading} onClick={handleCalculate}>
            Рассчитать стоимость
          </Button>

          {pricingError && (
            <Alert style={{ marginTop: 16 }} type="error" message={pricingError} showIcon />
          )}

          {pricingResult && (
            <div style={{ marginTop: 16 }}>
              <Space size="large">
                <Statistic title="Розничная цена" value={pricingResult.totalRetailPrice} suffix="₽" />
                <Statistic title="Дилерская цена" value={pricingResult.totalDealerPrice} suffix="₽" />
              </Space>
              <List
                style={{ marginTop: 16 }}
                bordered
                dataSource={pricingResult.components}
                renderItem={(item) => (
                  <List.Item>
                    {COMPONENT_LABELS[item.component as ComponentKey] ?? item.component}:{' '}
                    {item.priced
                      ? `${item.retailPrice} ₽ / ${item.dealerPrice} ₽ (дилер)`
                      : 'цена не найдена'}
                  </List.Item>
                )}
              />
            </div>
          )}
        </>
      )}
    </div>
  )
}

export default App
