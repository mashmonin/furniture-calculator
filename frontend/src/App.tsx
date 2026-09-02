import { Fragment, useEffect, useState } from 'react'
import { Alert, Button, Card, Divider, Empty, InputNumber, List, Space, Spin, Statistic, Switch, Typography } from 'antd'
import { calculatePrice, fetchDoorConfigurations, fetchPricingSurcharges } from './api/doorConfigurations'
import type {
  ComponentCatalogDto,
  ComponentKey,
  ComponentSelectionDto,
  DoorConfigurationDto,
  LinerDimensionOptionDto,
  PricingRequestDto,
  PricingResponseDto,
  PricingSurchargesDto,
  ReferenceDto,
} from './api/types'
import { OptionGroup } from './components/OptionGroup'
import './App.css'

const COMPONENT_ORDER: ComponentKey[] = ['leaf', 'frame', 'edge', 'doorCasing', 'frameExtensions']

// Единый поток «Введите данные двери»: реверс → коллекция → полотно → кромка → короб → наличник → добор
// (см. change redesign-door-configurator-flow). Реверс и коллекция вычисляются отдельно от этого списка —
// каждый сужает candidates до того, как начинается перебор CASCADE_ORDER, точно так же, как раньше только
// коллекция вычислялась отдельно от каскада типов компонентов.
const CASCADE_ORDER: ComponentKey[] = ['leaf', 'edge', 'frame', 'doorCasing', 'frameExtensions']

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
const MIRROR_FINISH_NEEDED_LABEL = 'Нужно зеркало'
const MIRROR_FINISH_LABEL = 'Исполнение с зеркалом'

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
  // Каталожные данные (длина/высота/толщина/цвет) уже определённого типа компонента — доступны сразу после
  // выбора типа, не дожидаясь, пока разрешатся типы всех остальных компонентов (см. design.md).
  resolvedComponent?: ComponentCatalogDto
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

// Реверс — первый шаг всего потока, оценивается по полному каталогу (см. design.md, «Переключатель «Реверс»
// в каскаде»): показывается, только если среди ВСЕХ door_configuration есть оба значения is_reverse.
function resolveReverseStep(
  configurations: DoorConfigurationDto[],
  reverseSelection: boolean | undefined,
): { reverseStep?: ReverseStep; resolvedReverse: boolean } {
  const reverseValues = new Set(configurations.map((configuration) => configuration.reverse))
  if (reverseValues.size > 1) {
    const resolvedReverse = reverseSelection ?? false
    return { reverseStep: { visible: true, value: resolvedReverse }, resolvedReverse }
  }
  return { resolvedReverse: configurations.length > 0 ? configurations[0].reverse : false }
}

interface MirrorFinishStep {
  visible: boolean
  options: ReferenceDto[]
}

// Исполнение зеркала — пред-коллекционный псевдо-шаг, симметричный resolveReverseStep, но независимый от него:
// оценивается уже по configurations, суженным реверсом (см. design.md). Переключатель «Нужно зеркало»
// выключен по умолчанию (аналог «без зеркала»); включение показывает варианты исполнения зеркала.
function resolveMirrorFinishStep(configurations: DoorConfigurationDto[]): MirrorFinishStep {
  const options = uniqueById(configurations.flatMap((configuration) => configuration.leaf.mirrorFinishOptions))
  return { visible: options.length > 0, options }
}

function filterByMirrorFinish(
  configurations: DoorConfigurationDto[],
  mirrorFinishEnabled: boolean,
  mirrorFinishTypeId: number | undefined,
): DoorConfigurationDto[] {
  if (!mirrorFinishEnabled) {
    return configurations
  }
  if (mirrorFinishTypeId === undefined) {
    return configurations.filter((configuration) => configuration.leaf.mirrorFinishOptions.length > 0)
  }
  return configurations.filter((configuration) =>
    configuration.leaf.mirrorFinishOptions.some((option) => option.id === mirrorFinishTypeId),
  )
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

  const resolvedComponent =
    selectedId !== undefined && selectedId !== NONE_OPTION_ID
      ? (candidates.find((configuration) => configuration[key]?.type.id === selectedId)?.[key] ?? undefined)
      : undefined

  const step: CascadeStep = { key, availableTypes, hasNoneOption, selectedId, resolvedComponent }
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
): { steps: CascadeStep[]; selectedConfiguration?: DoorConfigurationDto } {
  const steps: CascadeStep[] = []
  let candidates = configurations

  for (const key of CASCADE_ORDER) {
    const { step, nextCandidates } = applyCascadeStep(key, candidates, manualSelection)
    if (step) {
      steps.push(step)
    }
    if (nextCandidates === undefined) {
      return { steps }
    }
    candidates = nextCandidates
  }

  return { steps, selectedConfiguration: candidates.length === 1 ? candidates[0] : undefined }
}

interface SurchargeBreakdownItem {
  label: string
  percent: number
}

// Проценты надбавок вычисляются локально из уже загруженных правил (см. design.md) — не из ответа calculate(),
// эндпоинт расчёта стоимости не меняется и разбивку не возвращает.
function computeSurchargeBreakdown(
  pricingSurcharges: PricingSurchargesDto | null,
  leafSelection: ComponentSelectionDto,
  mirrorFinishTypeId: number | undefined,
  isReverse: boolean,
): SurchargeBreakdownItem[] {
  if (!pricingSurcharges) {
    return []
  }
  const items: SurchargeBreakdownItem[] = []
  const lengthRule =
    leafSelection.customLengthValueMm !== undefined
      ? pricingSurcharges.dimensionSurchargeRules.find(
          (rule) => rule.dimensionType.code === LENGTH_TYPE_CODE && rule.value === leafSelection.customLengthValueMm,
        )
      : undefined
  if (lengthRule) {
    items.push({ label: 'За нестандартную ширину', percent: lengthRule.surchargePercent })
  }
  const heightRule =
    leafSelection.customHeightValueMm !== undefined
      ? pricingSurcharges.dimensionSurchargeRules.find(
          (rule) => rule.dimensionType.code === HEIGHT_TYPE_CODE && rule.value === leafSelection.customHeightValueMm,
        )
      : undefined
  if (heightRule) {
    items.push({ label: 'За нестандартную высоту', percent: heightRule.surchargePercent })
  }
  const mirrorFinishSurcharge =
    mirrorFinishTypeId !== undefined
      ? pricingSurcharges.mirrorFinishSurcharges.find((surcharge) => surcharge.id === mirrorFinishTypeId)
      : undefined
  if (mirrorFinishSurcharge) {
    items.push({ label: 'За исполнение зеркала', percent: mirrorFinishSurcharge.surchargePercent })
  }
  if (isReverse) {
    items.push({ label: 'За реверс', percent: pricingSurcharges.reverseSurchargePercent })
  }
  return items
}

function App() {
  const [configurations, setConfigurations] = useState<DoorConfigurationDto[]>([])
  const [catalogLoading, setCatalogLoading] = useState(true)
  const [catalogError, setCatalogError] = useState<string | null>(null)

  const [pricingSurcharges, setPricingSurcharges] = useState<PricingSurchargesDto | null>(null)

  const [reverseSelection, setReverseSelection] = useState<boolean | undefined>(undefined)
  const [mirrorFinishEnabled, setMirrorFinishEnabled] = useState(false)
  const [mirrorFinishTypeId, setMirrorFinishTypeId] = useState<number | undefined>(undefined)
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

  useEffect(() => {
    let cancelled = false
    // Ошибка загрузки процентов надбавок не должна блокировать каскад/расчёт — только отключает разбивку.
    fetchPricingSurcharges()
      .then((data) => {
        if (!cancelled) {
          setPricingSurcharges(data)
        }
      })
      .catch(() => {
        // Намеренно молча: разбивка надбавок просто не будет показана после расчёта.
      })
    return () => {
      cancelled = true
    }
  }, [])

  const { reverseStep, resolvedReverse } = resolveReverseStep(configurations, reverseSelection)
  const reverseFilteredConfigurations = configurations.filter((configuration) => configuration.reverse === resolvedReverse)

  const mirrorFinishStep = resolveMirrorFinishStep(reverseFilteredConfigurations)
  const mirrorFilteredConfigurations = filterByMirrorFinish(reverseFilteredConfigurations, mirrorFinishEnabled, mirrorFinishTypeId)

  const collectionOptions = uniqueById(
    mirrorFilteredConfigurations
      .map((configuration) => configuration.leaf.collection)
      .filter((type): type is ReferenceDto => Boolean(type)),
  )
  const collectionFilteredConfigurations =
    selectedCollectionId === undefined
      ? []
      : mirrorFilteredConfigurations.filter((configuration) => configuration.leaf.collection?.id === selectedCollectionId)

  const { steps: cascadeSteps, selectedConfiguration } = buildCascadeSteps(collectionFilteredConfigurations, cascadeSelection)

  const leafComponent = cascadeSteps.find((step) => step.key === 'leaf')?.resolvedComponent
  const leafHeightValue =
    selection.leaf.customHeightValueMm ??
    leafComponent?.dimensionOptions.find((option) => option.id === selection.leaf.heightOptionId)?.value

  function handleReverseChange(value: boolean) {
    setReverseSelection(value)
    setSelectedCollectionId(undefined)
    setCascadeSelection({})
    setSelection(emptySelection())
    setPricingResult(null)
    setPricingError(null)
  }

  function handleMirrorFinishEnabledChange(checked: boolean) {
    setMirrorFinishEnabled(checked)
    setMirrorFinishTypeId(undefined)
    setSelectedCollectionId(undefined)
    setCascadeSelection({})
    setSelection(emptySelection())
    setPricingResult(null)
    setPricingError(null)
  }

  function handleMirrorFinishTypeChange(id: number | undefined) {
    setMirrorFinishTypeId(id)
    setSelectedCollectionId(undefined)
    setCascadeSelection({})
    setSelection(emptySelection())
    setPricingResult(null)
    setPricingError(null)
  }

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
    // Сбрасываются опции изменённого шага и всех последующих по каскаду (их тип мог измениться или
    // стать недоступным) — опции более ранних, уже пройденных шагов (например, полотна при смене
    // кромки) сохраняются, т.к. ввод теперь идёт сразу за каждым шагом, а не единым блоком в конце.
    setSelection((prev) => {
      const next = { ...prev }
      let resetFromHere = false
      for (const k of CASCADE_ORDER) {
        if (k === key) {
          resetFromHere = true
        }
        if (resetFromHere) {
          next[k] = {}
        }
      }
      return next
    })
    setPricingResult(null)
    setPricingError(null)
  }

  function updateSelection(key: ComponentKey, patch: Partial<ComponentSelectionDto>) {
    const isLeafHeightChange = key === 'leaf' && ('heightOptionId' in patch || 'customHeightValueMm' in patch)
    setSelection((prev) => {
      const next = { ...prev, [key]: { ...prev[key], ...patch } }
      if (isLeafHeightChange) {
        next.edge = { ...next.edge, heightOptionId: undefined }
      }
      return next
    })
    // Любое изменение в блоке «Введите данные двери» делает показанный результат неактуальным —
    // скрываем его до тех пор, пока пользователь заново не вызовет расчёт стоимости.
    setPricingResult(null)
    setPricingError(null)
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
          request[key] =
            key === 'leaf' && mirrorFinishTypeId !== undefined ? { ...selection.leaf, mirrorFinishTypeId } : selection[key]
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

  const surchargeBreakdown = computeSurchargeBreakdown(
    pricingSurcharges,
    selection.leaf,
    mirrorFinishTypeId,
    selectedConfiguration?.reverse ?? false,
  )

  return (
    <div className="page">
      <Typography.Title level={2}>Конфигуратор межкомнатных дверей</Typography.Title>

      <Typography.Title level={4}>Введите данные двери</Typography.Title>
      {catalogLoading && <Spin />}
      {catalogError && <Alert type="error" message={catalogError} showIcon />}
      {!catalogLoading && !catalogError && configurations.length === 0 && (
        <Empty description="Нет доступных конфигураций" />
      )}
      {!catalogLoading && !catalogError && configurations.length > 0 && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {(reverseStep?.visible || mirrorFinishStep.visible) && (
            <Space align="center" size="large">
              {reverseStep?.visible && (
                <Space align="center">
                  <Typography.Text>Реверс</Typography.Text>
                  <Switch checked={reverseStep.value} onChange={handleReverseChange} />
                </Space>
              )}
              {mirrorFinishStep.visible && (
                <Space align="center">
                  <Typography.Text>{MIRROR_FINISH_NEEDED_LABEL}</Typography.Text>
                  <Switch checked={mirrorFinishEnabled} onChange={handleMirrorFinishEnabledChange} />
                </Space>
              )}
            </Space>
          )}
          {mirrorFinishStep.visible && mirrorFinishEnabled && (
            <OptionGroup
              label={MIRROR_FINISH_LABEL}
              options={mirrorFinishStep.options.map((type) => ({ id: type.id, label: type.name }))}
              selectedId={mirrorFinishTypeId}
              onChange={handleMirrorFinishTypeChange}
            />
          )}
          <OptionGroup
            label={COLLECTION_LABEL}
            options={collectionOptions.map((type) => ({ id: type.id, label: type.name }))}
            selectedId={selectedCollectionId}
            onChange={handleCollectionChange}
          />
          {cascadeSteps.map((step) => {
            const component = step.resolvedComponent
            return (
              <Fragment key={step.key}>
                <OptionGroup
                  label={COMPONENT_LABELS[step.key]}
                  options={[
                    ...step.availableTypes.map((type) => ({ id: type.id, label: type.name })),
                    ...(step.hasNoneOption ? [{ id: NONE_OPTION_ID, label: NONE_OPTION_LABELS[step.key] }] : []),
                  ]}
                  selectedId={step.selectedId}
                  onChange={(id) => handleCascadeStepChange(step.key, id)}
                />
                {component && (
                  <Card size="small" title={`${COMPONENT_LABELS[step.key]}: ${component.type.name}`}>
                    <Space direction="vertical" size="middle">
                      {step.key === 'frame' && component.posts.length > 0 && (
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
                      {step.key === 'frame' && component.posts.length === 0 && component.colourOptions.length > 0 && (
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
                        selectedId={selection[step.key].lengthOptionId}
                        onChange={(id) => updateSelection(step.key, { lengthOptionId: id, customLengthValueMm: undefined })}
                      />
                      {step.key === 'leaf' && (
                        <Space align="center">
                          <Typography.Text type="secondary">Другое значение длины (мм)</Typography.Text>
                          <InputNumber
                            min={1}
                            value={selection.leaf.customLengthValueMm}
                            onChange={(value) =>
                              updateSelection('leaf', { customLengthValueMm: value ?? undefined, lengthOptionId: undefined })
                            }
                          />
                        </Space>
                      )}
                      <OptionGroup
                        label="Высота"
                        options={(step.key === 'edge'
                          ? edgeHeightOptions(component, leafHeightValue)
                          : component.dimensionOptions.filter((option) => option.dimensionType.code === HEIGHT_TYPE_CODE)
                        ).map((option) => ({ id: option.id, label: String(option.value) }))}
                        selectedId={selection[step.key].heightOptionId}
                        onChange={(id) => updateSelection(step.key, { heightOptionId: id, customHeightValueMm: undefined })}
                      />
                      {step.key === 'leaf' && (
                        <Space align="center">
                          <Typography.Text type="secondary">Другое значение высоты (мм)</Typography.Text>
                          <InputNumber
                            min={1}
                            value={selection.leaf.customHeightValueMm}
                            onChange={(value) =>
                              updateSelection('leaf', { customHeightValueMm: value ?? undefined, heightOptionId: undefined })
                            }
                          />
                        </Space>
                      )}
                      <OptionGroup
                        label="Толщина"
                        options={component.dimensionOptions
                          .filter((option) => option.dimensionType.code === THICKNESS_TYPE_CODE)
                          .map((option) => ({ id: option.id, label: String(option.value) }))}
                        selectedId={selection[step.key].thicknessOptionId}
                        onChange={(id) => updateSelection(step.key, { thicknessOptionId: id })}
                      />
                      <OptionGroup
                        label="Цвет"
                        options={component.colourOptions.map((option) => ({
                          id: option.id,
                          label: option.colourType.name,
                        }))}
                        selectedId={selection[step.key].colourOptionId}
                        onChange={(id) => updateSelection(step.key, { colourOptionId: id })}
                      />
                      {(step.key === 'doorCasing' || step.key === 'frameExtensions') && (
                        <Space align="center">
                          <Typography.Text type="secondary">Количество</Typography.Text>
                          <InputNumber
                            min={1}
                            value={selection[step.key].quantity ?? 1}
                            onChange={(value) => updateSelection(step.key, { quantity: value ?? undefined })}
                          />
                        </Space>
                      )}
                    </Space>
                  </Card>
                )}
              </Fragment>
            )
          })}
        </Space>
      )}

      {selectedConfiguration && (
        <>
          <Divider />
          <Typography.Title level={4}>Расчёт стоимости</Typography.Title>
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
              {surchargeBreakdown.length > 0 && (
                <List
                  style={{ marginTop: 16 }}
                  size="small"
                  header={<Typography.Text type="secondary">Надбавки к цене полотна</Typography.Text>}
                  bordered
                  dataSource={surchargeBreakdown}
                  renderItem={(item) => (
                    <List.Item>
                      {item.label}: +{item.percent}%
                    </List.Item>
                  )}
                />
              )}
              <List
                style={{ marginTop: 16 }}
                bordered
                dataSource={pricingResult.components}
                renderItem={(item) => {
                  const hasSurcharge =
                    item.priced && (item.baseRetailPrice !== item.retailPrice || item.baseDealerPrice !== item.dealerPrice)
                  const key = item.component as ComponentKey
                  const hasQuantity = key === 'doorCasing' || key === 'frameExtensions'
                  const quantity = hasQuantity ? (selection[key].quantity ?? 1) : undefined
                  return (
                    <List.Item>
                      <Space direction="vertical" size={0}>
                        <span>
                          {COMPONENT_LABELS[key] ?? item.component}:{' '}
                          {item.priced
                            ? `${item.retailPrice} ₽ / ${item.dealerPrice} ₽ (дилер)`
                            : 'цена не найдена'}
                          {item.priced && quantity !== undefined && ` × ${quantity} шт.`}
                        </span>
                        {hasSurcharge && (
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            Без надбавок: {item.baseRetailPrice} ₽ / {item.baseDealerPrice} ₽ (дилер)
                          </Typography.Text>
                        )}
                      </Space>
                    </List.Item>
                  )
                }}
              />
            </div>
          )}
        </>
      )}
    </div>
  )
}

export default App
