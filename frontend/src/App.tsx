import { useEffect, useState } from 'react'
import { Alert, Button, Card, Divider, Empty, List, Space, Spin, Statistic, Switch, Typography } from 'antd'
import { calculatePrice, fetchDoorConfigurations } from './api/doorConfigurations'
import type {
  ComponentCatalogDto,
  ComponentKey,
  ComponentSelectionDto,
  DoorConfigurationDto,
  LinerDimensionOptionDto,
  PricingRequestDto,
  PricingResponseDto,
  ReferenceDto,
} from './api/types'
import { OptionGroup } from './components/OptionGroup'
import './App.css'

const COMPONENT_ORDER: ComponentKey[] = ['leaf', 'frame', 'edge', 'doorCasing', 'frameExtensions']

// Порядок шагов каскадного подбора конфигурации: кромка выбирается сразу
// после полотна, перед коробом/наличником/добором (см. design.md изменения
// reorder-cascade-add-no-casing-option). Наличник/добор по-прежнему требуют
// заданного короба (см. chk_door_configuration_casing_extensions_require_frame),
// поэтому короб идёт раньше них. Между кромкой и коробом встраивается
// переключатель «Реверс» (см. change rework-door-reverse-mechanism) — он не
// тип-компонент, поэтому обрабатывается отдельно от этого списка, но делит
// его на две фазы сужения candidates.
const CASCADE_BEFORE_REVERSE: ComponentKey[] = ['leaf', 'edge']
const CASCADE_AFTER_REVERSE: ComponentKey[] = ['frame', 'doorCasing', 'frameExtensions']
const CASCADE_ORDER: ComponentKey[] = [...CASCADE_BEFORE_REVERSE, ...CASCADE_AFTER_REVERSE]

// Короб без записей frame_post, у которого цена задаётся выбором цвета
// (см. change activate-fantom-frame-type), физически всё равно состоит
// из комплекта стоек — эта строка чисто описательная, в расчёт не входит.
const FRAME_KIT_WITHOUT_POSTS_DESCRIPTION = 'Комплект (2 стойки и верх)'

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

// Диапазон высоты кромки [minValue, value] должен покрывать высоту уже выбранного полотна (см. design.md).
function edgeHeightOptions(component: ComponentCatalogDto, leafHeightValue: number | undefined): LinerDimensionOptionDto[] {
  if (leafHeightValue === undefined) {
    return []
  }
  return component.dimensionOptions.filter(
    (option) =>
      option.dimensionType.code === HEIGHT_TYPE_CODE &&
      (option.minValue === null || option.minValue <= leafHeightValue) &&
      leafHeightValue <= option.value,
  )
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

interface ReverseStep {
  visible: boolean
  value: boolean
}

// Сужает candidates по одному шагу CASCADE_ORDER; возвращает undefined в steps-массиве
// вызывающей стороны, если шаг ещё не разрешён (кандидат не сужен дальше).
function applyCascadeStep(
  key: ComponentKey,
  candidates: DoorConfigurationDto[],
  manualSelection: Partial<Record<ComponentKey, number>>,
): { step?: CascadeStep; nextCandidates?: DoorConfigurationDto[] } {
  const availableTypes = uniqueById(
    candidates.map((configuration) => configuration[key]?.type).filter((type): type is ReferenceDto => Boolean(type)),
  )
  if (availableTypes.length === 0) {
    // Ни у одного кандидата нет этого компонента — выбирать нечего, шаг пропускается.
    return { nextCandidates: candidates }
  }
  const hasNoneOption = candidates.some((configuration) => !configuration[key])

  const manual = manualSelection[key]
  const manualValid =
    manual !== undefined && ((manual === NONE_OPTION_ID && hasNoneOption) || availableTypes.some((type) => type.id === manual))
  const selectedId = manualValid ? manual : undefined

  const step: CascadeStep = { key, availableTypes, hasNoneOption, selectedId }
  if (selectedId === undefined) {
    return { step }
  }
  const nextCandidates =
    selectedId === NONE_OPTION_ID
      ? candidates.filter((configuration) => !configuration[key])
      : candidates.filter((configuration) => configuration[key]?.type.id === selectedId)
  return { step, nextCandidates }
}

function buildCascadeSteps(
  configurations: DoorConfigurationDto[],
  manualSelection: Partial<Record<ComponentKey, number>>,
  reverseSelection: boolean | undefined,
): { steps: CascadeStep[]; reverseStep?: ReverseStep; selectedConfiguration?: DoorConfigurationDto } {
  const steps: CascadeStep[] = []
  let candidates = configurations

  for (const key of CASCADE_BEFORE_REVERSE) {
    const { step, nextCandidates } = applyCascadeStep(key, candidates, manualSelection)
    if (step) {
      steps.push(step)
    }
    if (nextCandidates === undefined) {
      return { steps }
    }
    candidates = nextCandidates
  }

  const reverseValues = new Set(candidates.map((configuration) => configuration.reverse))
  let reverseStep: ReverseStep | undefined
  let resolvedReverse: boolean
  if (reverseValues.size > 1) {
    resolvedReverse = reverseSelection ?? false
    reverseStep = { visible: true, value: resolvedReverse }
  } else {
    resolvedReverse = candidates.length > 0 ? candidates[0].reverse : false
  }
  candidates = candidates.filter((configuration) => configuration.reverse === resolvedReverse)

  for (const key of CASCADE_AFTER_REVERSE) {
    const { step, nextCandidates } = applyCascadeStep(key, candidates, manualSelection)
    if (step) {
      steps.push(step)
    }
    if (nextCandidates === undefined) {
      return { steps, reverseStep }
    }
    candidates = nextCandidates
  }

  return { steps, reverseStep, selectedConfiguration: candidates.length === 1 ? candidates[0] : undefined }
}

function App() {
  const [configurations, setConfigurations] = useState<DoorConfigurationDto[]>([])
  const [catalogLoading, setCatalogLoading] = useState(true)
  const [catalogError, setCatalogError] = useState<string | null>(null)

  const [selectedCollectionId, setSelectedCollectionId] = useState<number | undefined>(undefined)
  const [cascadeSelection, setCascadeSelection] = useState<Partial<Record<ComponentKey, number>>>({})
  const [reverseSelection, setReverseSelection] = useState<boolean | undefined>(undefined)
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

  const {
    steps: cascadeSteps,
    reverseStep,
    selectedConfiguration,
  } = buildCascadeSteps(collectionFilteredConfigurations, cascadeSelection, reverseSelection)

  const leafHeightValue = selectedConfiguration?.leaf.dimensionOptions.find(
    (option) => option.id === selection.leaf.heightOptionId,
  )?.value

  function handleCollectionChange(id: number | undefined) {
    setSelectedCollectionId(id)
    setCascadeSelection({})
    setReverseSelection(undefined)
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
    if (CASCADE_BEFORE_REVERSE.includes(key)) {
      setReverseSelection(undefined)
    }
    setSelection(emptySelection())
    setPricingResult(null)
    setPricingError(null)
  }

  function handleReverseChange(value: boolean) {
    setReverseSelection(value)
    setCascadeSelection((prev) => {
      const next: Partial<Record<ComponentKey, number>> = {}
      for (const k of CASCADE_BEFORE_REVERSE) {
        if (prev[k] !== undefined) {
          next[k] = prev[k]
        }
      }
      return next
    })
    setSelection(emptySelection())
    setPricingResult(null)
    setPricingError(null)
  }

  function updateSelection(key: ComponentKey, patch: Partial<ComponentSelectionDto>) {
    const isLeafHeightChange = key === 'leaf' && 'heightOptionId' in patch
    setSelection((prev) => {
      const next = { ...prev, [key]: { ...prev[key], ...patch } }
      if (isLeafHeightChange) {
        next.edge = { ...next.edge, heightOptionId: undefined }
      }
      return next
    })
    if (isLeafHeightChange) {
      setPricingResult(null)
      setPricingError(null)
    }
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
          {cascadeSteps
            .filter((step) => CASCADE_BEFORE_REVERSE.includes(step.key))
            .map((step) => (
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
          {reverseStep?.visible && (
            <Space align="center">
              <Typography.Text>Реверс</Typography.Text>
              <Switch checked={reverseStep.value} onChange={handleReverseChange} />
            </Space>
          )}
          {cascadeSteps
            .filter((step) => CASCADE_AFTER_REVERSE.includes(step.key))
            .map((step) => (
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
                    {key === 'frame' && component.posts.length > 0 && (
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
                    {key === 'frame' && component.posts.length === 0 && component.colourOptions.length > 0 && (
                      <List
                        size="small"
                        header={<Typography.Text type="secondary">Состав короба</Typography.Text>}
                        bordered
                        dataSource={[FRAME_KIT_WITHOUT_POSTS_DESCRIPTION]}
                        renderItem={(item) => <List.Item>{item}</List.Item>}
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
                      options={(key === 'edge'
                        ? edgeHeightOptions(component, leafHeightValue)
                        : component.dimensionOptions.filter((option) => option.dimensionType.code === HEIGHT_TYPE_CODE)
                      ).map((option) => ({ id: option.id, label: String(option.value) }))}
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
