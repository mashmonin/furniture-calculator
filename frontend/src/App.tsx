import { useEffect, useState } from 'react'
import { Alert, Button, Card, Divider, Empty, List, Space, Spin, Statistic, Typography } from 'antd'
import { calculatePrice, fetchDoorConfigurations } from './api/doorConfigurations'
import type {
  ComponentKey,
  ComponentSelectionDto,
  DoorConfigurationDto,
  PricingRequestDto,
  PricingResponseDto,
  ReferenceDto,
} from './api/types'
import { OptionGroup } from './components/OptionGroup'
import './App.css'

const COMPONENT_ORDER: ComponentKey[] = ['leaf', 'frame', 'edge', 'doorCasing', 'frameExtensions']

// Порядок шагов каскадного подбора конфигурации: наличник/добор требуют
// заданного короба (см. chk_door_configuration_casing_extensions_require_frame),
// поэтому короб идёт раньше них; кромка — после наличника (см. design.md).
const CASCADE_ORDER: ComponentKey[] = ['leaf', 'frame', 'doorCasing', 'edge', 'frameExtensions']

const COMPONENT_LABELS: Record<ComponentKey, string> = {
  leaf: 'Полотно',
  frame: 'Коробка',
  edge: 'Кромка',
  doorCasing: 'Наличник',
  frameExtensions: 'Добор',
}

const COLLECTION_LABEL = 'Коллекция'

// Синтетический id варианта «без этого компонента» — реальные id из БД начинаются с 1.
const NONE_OPTION_ID = 0

const NONE_OPTION_LABELS: Record<ComponentKey, string> = {
  leaf: 'Без полотна',
  frame: 'Без короба',
  edge: 'Без кромки',
  doorCasing: 'Без наличника',
  frameExtensions: 'Без добора',
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

interface CascadeStep {
  key: ComponentKey
  availableTypes: ReferenceDto[]
  hasNoneOption: boolean
  selectedId?: number
}

function uniqueById(types: ReferenceDto[]): ReferenceDto[] {
  const seen = new Set<number>()
  const result: ReferenceDto[] = []
  for (const type of types) {
    if (!seen.has(type.id)) {
      seen.add(type.id)
      result.push(type)
    }
  }
  return result
}

function buildCascadeSteps(
  configurations: DoorConfigurationDto[],
  manualSelection: Partial<Record<ComponentKey, number>>,
): { steps: CascadeStep[]; selectedConfiguration?: DoorConfigurationDto } {
  const steps: CascadeStep[] = []
  let candidates = configurations

  for (const key of CASCADE_ORDER) {
    const availableTypes = uniqueById(
      candidates.map((configuration) => configuration[key]?.type).filter((type): type is ReferenceDto => Boolean(type)),
    )
    if (availableTypes.length === 0) {
      // Ни у одного кандидата нет этого компонента — выбирать нечего, шаг пропускается.
      continue
    }
    const hasNoneOption = candidates.some((configuration) => !configuration[key])

    const manual = manualSelection[key]
    const manualValid =
      manual !== undefined && ((manual === NONE_OPTION_ID && hasNoneOption) || availableTypes.some((type) => type.id === manual))
    const selectedId = manualValid ? manual : undefined

    steps.push({ key, availableTypes, hasNoneOption, selectedId })

    if (selectedId === undefined) {
      return { steps }
    }
    candidates =
      selectedId === NONE_OPTION_ID
        ? candidates.filter((configuration) => !configuration[key])
        : candidates.filter((configuration) => configuration[key]?.type.id === selectedId)
  }

  return { steps, selectedConfiguration: candidates.length === 1 ? candidates[0] : undefined }
}

function App() {
  const [configurations, setConfigurations] = useState<DoorConfigurationDto[]>([])
  const [catalogLoading, setCatalogLoading] = useState(true)
  const [catalogError, setCatalogError] = useState<string | null>(null)

  const [selectedCollectionId, setSelectedCollectionId] = useState<number | undefined>(undefined)
  const [cascadeSelection, setCascadeSelection] = useState<Partial<Record<ComponentKey, number>>>({})
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

  const collectionOptions = uniqueById(
    configurations.map((configuration) => configuration.leaf.collection).filter((type): type is ReferenceDto => Boolean(type)),
  )
  const collectionFilteredConfigurations =
    selectedCollectionId === undefined
      ? []
      : configurations.filter((configuration) => configuration.leaf.collection?.id === selectedCollectionId)

  const { steps: cascadeSteps, selectedConfiguration } = buildCascadeSteps(collectionFilteredConfigurations, cascadeSelection)

  function handleCollectionChange(id: number | undefined) {
    setSelectedCollectionId(id)
    setCascadeSelection({})
    setSelection(emptySelection())
    setPricingResult(null)
    setPricingError(null)
  }

  function handleCascadeStepChange(key: ComponentKey, id: number | undefined) {
    setCascadeSelection((prev) => {
      const next: Partial<Record<ComponentKey, number>> = {}
      for (const k of CASCADE_ORDER) {
        if (k === key) {
          break
        }
        if (prev[k] !== undefined) {
          next[k] = prev[k]
        }
      }
      if (id !== undefined) {
        next[key] = id
      }
      return next
    })
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

      <Typography.Title level={4}>1. Соберите конфигурацию</Typography.Title>
      {catalogLoading && <Spin />}
      {catalogError && <Alert type="error" message={catalogError} showIcon />}
      {!catalogLoading && !catalogError && configurations.length === 0 && (
        <Empty description="Нет доступных конфигураций" />
      )}
      {!catalogLoading && !catalogError && configurations.length > 0 && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <OptionGroup
            label={COLLECTION_LABEL}
            options={collectionOptions.map((type) => ({ id: type.id, label: type.name }))}
            selectedId={selectedCollectionId}
            onChange={handleCollectionChange}
          />
          {cascadeSteps.map((step) => (
            <OptionGroup
              key={step.key}
              label={COMPONENT_LABELS[step.key]}
              options={[
                ...step.availableTypes.map((type) => ({ id: type.id, label: type.name })),
                ...(step.hasNoneOption ? [{ id: NONE_OPTION_ID, label: NONE_OPTION_LABELS[step.key] }] : []),
              ]}
              selectedId={step.selectedId}
              onChange={(id) => handleCascadeStepChange(step.key, id)}
            />
          ))}
        </Space>
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
                    {component.posts.length > 0 && (
                      <List
                        size="small"
                        header={<Typography.Text type="secondary">Состав короба</Typography.Text>}
                        bordered
                        dataSource={component.posts}
                        renderItem={(post) => (
                          <List.Item>
                            {post.postType.name} × {post.quantity}
                            {post.length !== null ? `, длина ${post.length}` : ''} — {post.retailPrice} ₽ / {post.dealerPrice} ₽
                            (дилер)
                          </List.Item>
                        )}
                      />
                    )}
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
