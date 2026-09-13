import { Fragment, useEffect, useRef, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Collapse,
  Empty,
  InputNumber,
  List,
  Menu,
  Space,
  Spin,
  Statistic,
  Switch,
  Typography,
} from 'antd'
import {
  calculateLeafPrice,
  calculatePrice,
  fetchDoorConfigurations,
  fetchHardwareCatalog,
  fetchPricingSurcharges,
} from './api/doorConfigurations'
import { fetchUpdateCheck } from './api/updateCheck'
import type {
  ComponentCatalogDto,
  ComponentKey,
  ComponentSelectionDto,
  DoorConfigurationDto,
  HardwareCategoryDto,
  HardwareOptionDto,
  HardwareSelectionDto,
  HardwareTypeDto,
  LinerDimensionOptionDto,
  PricingRequestDto,
  PricingResponseDto,
  PricingSurchargesDto,
  ReferenceDto,
  UpdateCheckDto,
} from './api/types'
import { OptionGroup } from './components/OptionGroup'
import './App.css'

const COMPONENT_ORDER: ComponentKey[] = ['leaf', 'frame', 'edge', 'doorCasing', 'frameExtensions']

// Единый поток «Введите данные двери»: реверс → коллекция → полотно → кромка → короб → наличник → добор
// (см. change redesign-door-configurator-flow). Реверс и коллекция вычисляются отдельно от этого списка —
// каждый сужает candidates до того, как начинается перебор CASCADE_ORDER, точно так же, как раньше только
// коллекция вычислялась отдельно от каскада типов компонентов.
const CASCADE_ORDER: ComponentKey[] = ['leaf', 'edge', 'frame', 'doorCasing', 'frameExtensions']

// Раздел «Полотно» (см. change redesign-configurator-layout) — кромка относится сюда, а не к «Короб и
// обрамление»: она характеризует край самого полотна, тогда как короб/наличник/добор образуют дверной
// портал, существующий независимо от факта установки полотна.
const LEAF_PANEL_STEP_KEYS: ComponentKey[] = ['leaf', 'edge']
const FRAME_GROUP_PANEL_STEP_KEYS: ComponentKey[] = ['frame', 'doorCasing', 'frameExtensions']

// Задержка перед отправкой запроса расчёта после последнего изменения конфигурации (debounce) — единая
// для всех типов изменений (см. design.md изменения redesign-configurator-layout).
const PRICING_DEBOUNCE_MS = 500

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

// Синтетический id единственного варианта высоты короба «Фантом» (см. HEIGHT_MIRROR_FRAME_TYPE_CODES) —
// у этого короба нет каталожных liner_dimension_option, поэтому кнопка выбора высоты не ссылается на
// реальный id, а лишь подтверждает применение значения, скопированного из высоты полотна
// (customHeightValueMm), как и требует явный клик даже при единственной альтернативе.
const MIRROR_HEIGHT_OPTION_ID = -1

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
// Коды коробов, у которых высота ограничена диапазоном высоты полотна: «Компланар» и «НЕО»
// (см. change link-frame-neo-height-to-leaf-height, link-komplanar-height-to-leaf-height).
const HEIGHT_RANGE_FRAME_TYPE_CODES = ['FT-002', 'FT-003']
// Коды коробов, у которых высота не выбирается из каталога, а всегда в точности равна высоте полотна:
// «Фантом» (см. change mirror-fantom-frame-height-to-leaf-height) — у него вообще нет каталожных опций
// высоты, в отличие от HEIGHT_RANGE_FRAME_TYPE_CODES.
const HEIGHT_MIRROR_FRAME_TYPE_CODES = ['FT-001']
// Коды добора, у которого длина ограничена диапазоном высоты полотна — добор «ТС» (см. change
// link-dobor-ts-length-to-leaf-height) и добор «КОМПЛАНАР», все 6 ширин (см. change
// link-komplanar-dobor-length-to-leaf-height) — по тому же принципу, что и HEIGHT_RANGE_FRAME_TYPE_CODES,
// но на оси «Длина». Это все 10 реально достижимых через каталог кодов frame_extensions_type.
const LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES = [
  'FET-004', 'FET-005', 'FET-006', 'FET-007',
  'FET-008', 'FET-009', 'FET-010', 'FET-011', 'FET-012', 'FET-013',
]
// Коды наличников, у которых длина ограничена диапазоном высоты полотна — «Модо»/«Онда» (см. change
// link-modo-onda-casing-length-to-leaf-height) и наличники короба «Компланар»: «Эво», «Авеню»/
// «Авеню-реверс», «Аура»/«Аура-реверс», «Ария»/«Ария-реверс» (см. change
// link-komplanar-casing-length-to-leaf-height) — тот же принцип, что и у добора «ТС», на той же оси
// «Длина». Это все 9 существующих кодов door_casing_type.
const LENGTH_RANGE_DOOR_CASING_TYPE_CODES = [
  'DCT-001', 'DCT-002', 'DCT-003', 'DCT-004', 'DCT-005', 'DCT-006', 'DCT-007', 'DCT-008', 'DCT-009',
]

// Одна позиция блока «Фурнитура» (см. change add-hardware-catalog) — независима от выбора конфигурации
// двери и от остальных позиций; каскад категория → тип → цвет зеркалирует CascadeStep компонентов.
interface HardwareLine {
  key: number
  categoryId?: number
  typeId?: number
  hardwareOptionId?: number
  quantity?: number
}

function hardwareTypesFor(catalog: HardwareCategoryDto[], categoryId: number | undefined): HardwareTypeDto[] {
  if (categoryId === undefined) {
    return []
  }
  return catalog.find((category) => category.category.id === categoryId)?.types ?? []
}

function hardwareOptionsFor(
  catalog: HardwareCategoryDto[],
  categoryId: number | undefined,
  typeId: number | undefined,
): HardwareOptionDto[] {
  if (typeId === undefined) {
    return []
  }
  return hardwareTypesFor(catalog, categoryId).find((type) => type.type.id === typeId)?.options ?? []
}

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

// Диапазон опции [minValue, maxValue] покрывает высоту уже выбранного полотна. Ось-нейтрально: сравнивает
// только числа, не привязана к тому, что именно описывает опция (высоту короба или длину добора «ТС») —
// вызывающая сторона сама фильтрует по нужной оси (см. change link-frame-neo-height-to-leaf-height,
// link-dobor-ts-length-to-leaf-height). В отличие от кромки, физическое значение опции (value) не совпадает
// с границами этого диапазона, поэтому верхней границей служит maxValue, а не value.
function dimensionRangeCoversLeafHeight(option: LinerDimensionOptionDto, leafHeightValue: number): boolean {
  const max = option.maxValue ?? option.value
  return (option.minValue === null || option.minValue <= leafHeightValue) && leafHeightValue <= max
}

function frameHeightRangeOptions(component: ComponentCatalogDto, leafHeightValue: number | undefined): LinerDimensionOptionDto[] {
  if (leafHeightValue === undefined) {
    return []
  }
  return component.dimensionOptions.filter(
    (option) => option.dimensionType.code === HEIGHT_TYPE_CODE && dimensionRangeCoversLeafHeight(option, leafHeightValue),
  )
}

// Есть ли у компонента короба (из HEIGHT_RANGE_FRAME_TYPE_CODES) хотя бы одна опция высоты, покрывающая
// leafHeightValue — используется для исключения этого типа короба из каскадного выбора при несовместимой
// высоте полотна (см. buildCascadeSteps).
function frameCoversHeight(component: ComponentCatalogDto, leafHeightValue: number): boolean {
  return component.dimensionOptions.some(
    (option) => option.dimensionType.code === HEIGHT_TYPE_CODE && dimensionRangeCoversLeafHeight(option, leafHeightValue),
  )
}

// Диапазон длины [minValue, maxValue] покрывает высоту уже выбранного полотна — тот же принцип, что и
// frameHeightRangeOptions, но на оси «Длина» (см. change link-dobor-ts-length-to-leaf-height). Ось-нейтрально
// по владельцу — переиспользуется и для добора «ТС», и для наличников «Модо»/«Онда» (см. change
// link-modo-onda-casing-length-to-leaf-height); вызывающая сторона сама решает, к какому владельцу применять.
function lengthRangeOptions(component: ComponentCatalogDto, leafHeightValue: number | undefined): LinerDimensionOptionDto[] {
  if (leafHeightValue === undefined) {
    return []
  }
  return component.dimensionOptions.filter(
    (option) => option.dimensionType.code === LENGTH_TYPE_CODE && dimensionRangeCoversLeafHeight(option, leafHeightValue),
  )
}

// Есть ли у компонента хотя бы одна опция длины, покрывающая leafHeightValue — используется для исключения
// добора «ТС»/наличников «Модо»/«Онда» из каскадного выбора при несовместимой высоте полотна (см. buildCascadeSteps).
function lengthRangeCoversHeight(component: ComponentCatalogDto, leafHeightValue: number): boolean {
  return component.dimensionOptions.some(
    (option) => option.dimensionType.code === LENGTH_TYPE_CODE && dimensionRangeCoversLeafHeight(option, leafHeightValue),
  )
}

function displayName(ref: { name: string; shortName: string | null }): string {
  return ref.shortName ?? ref.name
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
  leafHeightValue?: number,
): { steps: CascadeStep[]; selectedConfiguration?: DoorConfigurationDto } {
  const steps: CascadeStep[] = []
  let candidates = configurations

  for (const key of CASCADE_ORDER) {
    // Короб из HEIGHT_RANGE_FRAME_TYPE_CODES исключается из выбора, если высота полотна уже известна
    // и не покрывается ни одним его диапазоном — доп. UX-слой поверх обязательной проверки на бэкенде
    // (см. design.md, Decision 7 в link-frame-neo-height-to-leaf-height).
    if (key === 'frame' && leafHeightValue !== undefined) {
      candidates = candidates.filter(
        (configuration) =>
          !configuration.frame ||
          !HEIGHT_RANGE_FRAME_TYPE_CODES.includes(configuration.frame.type.code) ||
          frameCoversHeight(configuration.frame, leafHeightValue),
      )
    }
    // Добор из LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES исключается из выбора по тому же принципу, что и
    // короб выше, но по диапазону длины (см. change link-dobor-ts-length-to-leaf-height,
    // link-komplanar-dobor-length-to-leaf-height).
    if (key === 'frameExtensions' && leafHeightValue !== undefined) {
      candidates = candidates.filter(
        (configuration) =>
          !configuration.frameExtensions ||
          !LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES.includes(configuration.frameExtensions.type.code) ||
          lengthRangeCoversHeight(configuration.frameExtensions, leafHeightValue),
      )
    }
    // Наличники «Модо»/«Онда» из LENGTH_RANGE_DOOR_CASING_TYPE_CODES исключаются из выбора по тому же
    // принципу, что и добор «ТС» выше (см. change link-modo-onda-casing-length-to-leaf-height).
    if (key === 'doorCasing' && leafHeightValue !== undefined) {
      candidates = candidates.filter(
        (configuration) =>
          !configuration.doorCasing ||
          !LENGTH_RANGE_DOOR_CASING_TYPE_CODES.includes(configuration.doorCasing.type.code) ||
          lengthRangeCoversHeight(configuration.doorCasing, leafHeightValue),
      )
    }
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

const SERVICE_MENU_ITEMS = [{ key: 'door-configurator', label: 'Межкомнатные двери' }]

function App() {
  const [configurations, setConfigurations] = useState<DoorConfigurationDto[]>([])
  const [catalogLoading, setCatalogLoading] = useState(true)
  const [catalogError, setCatalogError] = useState<string | null>(null)

  const [pricingSurcharges, setPricingSurcharges] = useState<PricingSurchargesDto | null>(null)
  const [updateCheck, setUpdateCheck] = useState<UpdateCheckDto | null>(null)

  const [hardwareCatalog, setHardwareCatalog] = useState<HardwareCategoryDto[]>([])
  const [hardwareCatalogLoading, setHardwareCatalogLoading] = useState(true)
  const [hardwareCatalogError, setHardwareCatalogError] = useState<string | null>(null)
  const [hardwareLines, setHardwareLines] = useState<HardwareLine[]>([])
  const [nextHardwareLineKey, setNextHardwareLineKey] = useState(1)

  const [reverseSelection, setReverseSelection] = useState<boolean | undefined>(undefined)
  const [mirrorFinishEnabled, setMirrorFinishEnabled] = useState(false)
  const [mirrorFinishTypeId, setMirrorFinishTypeId] = useState<number | undefined>(undefined)
  const [selectedCollectionId, setSelectedCollectionId] = useState<number | undefined>(undefined)
  const [cascadeSelection, setCascadeSelection] = useState<Partial<Record<ComponentKey, number>>>({})
  const [selection, setSelection] = useState(emptySelection)

  const [pricingResult, setPricingResult] = useState<PricingResponseDto | null>(null)
  const [pricingLoading, setPricingLoading] = useState(false)
  const [pricingError, setPricingError] = useState<string | null>(null)
  // Возрастающий номер последнего фактически отправленного запроса расчёта — ответ применяется к
  // состоянию, только если совпадает с этим номером на момент получения (см. design.md изменения
  // redesign-configurator-layout, «Замена кнопки на debounce-триггер с защитой от гонки устаревших ответов»).
  const requestSeqRef = useRef(0)

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

  useEffect(() => {
    let cancelled = false
    setHardwareCatalogLoading(true)
    setHardwareCatalogError(null)
    // В отличие от процентов надбавок, ошибка загрузки каталога фурнитуры показывается видимо —
    // блок выбора фурнитуры не может работать без него (см. change add-hardware-catalog).
    fetchHardwareCatalog()
      .then((data) => {
        if (!cancelled) {
          setHardwareCatalog(data)
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setHardwareCatalogError(error instanceof Error ? error.message : 'Не удалось загрузить каталог фурнитуры')
        }
      })
      .finally(() => {
        if (!cancelled) {
          setHardwareCatalogLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    // Проверка обновлений не должна ничего блокировать — при ошибке просто не
    // показываем баннер (см. change add-desktop-app-packaging, раздел 5).
    fetchUpdateCheck()
      .then((data) => {
        if (!cancelled) {
          setUpdateCheck(data)
        }
      })
      .catch(() => {
        // Намеренно молча.
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

  // Первый проход — только чтобы узнать leafHeightValue (шаг leaf не зависит от неё, поэтому второй
  // проход её не меняет). Второй проход использует эту высоту, чтобы исключить короб «НЕО» из шага
  // «frame», если он ей не покрывается (см. design.md, Decision 7).
  const firstPassSteps = buildCascadeSteps(collectionFilteredConfigurations, cascadeSelection)
  const leafComponent = firstPassSteps.steps.find((step) => step.key === 'leaf')?.resolvedComponent
  const leafHeightValue =
    selection.leaf.customHeightValueMm ??
    leafComponent?.dimensionOptions.find((option) => option.id === selection.leaf.heightOptionId)?.value

  const { steps: cascadeSteps, selectedConfiguration } = buildCascadeSteps(
    collectionFilteredConfigurations,
    cascadeSelection,
    leafHeightValue,
  )

  // Пока конкретная door_configuration ещё не определена, но тип полотна уже определён, расчёт
  // выполняется по отдельному полотну (см. change add-standalone-leaf-pricing) — само по себе
  // отсутствие короба/наличника/добора больше не блокирует появление цены.
  const leafTypeId = leafComponent?.type.id

  // Если даже полотно ещё не определено, cascadeSteps заканчивается ровно тем шагом, который его
  // блокирует (buildCascadeSteps возвращается сразу же, как только очередной шаг требует явного
  // выбора) — используем это, чтобы подсказать пользователю, какой шаг заполнить дальше.
  const pendingStepKey =
    leafTypeId === undefined && cascadeSteps.length > 0 ? cascadeSteps[cascadeSteps.length - 1].key : undefined

  // Автоматический расчёт стоимости по текущему выбору вместо кнопки «Рассчитать стоимость»
  // (см. specs/door-configurator-ui, «Автоматический расчёт стоимости по текущему выбору»): срабатывает
  // при каждом изменении входов запроса, с паузой debounce, и игнорирует ответы, устаревшие к моменту
  // получения (см. requestSeqRef выше). Пока не определена конкретная конфигурация, но определено
  // полотно, используется расчёт отдельного полотна (см. «Использование расчёта отдельного полотна
  // до определения конфигурации»); как только конфигурация определена — расчёт переключается на неё.
  useEffect(() => {
    if (!selectedConfiguration && leafTypeId === undefined) {
      setPricingResult(null)
      setPricingError(null)
      setPricingLoading(false)
      return
    }

    setPricingLoading(true)
    const requestId = ++requestSeqRef.current
    let cancelled = false

    const timer = window.setTimeout(() => {
      const leafSelection =
        mirrorFinishTypeId !== undefined ? { ...selection.leaf, mirrorFinishTypeId } : selection.leaf
      // Незавершённые позиции (без выбранного цветового варианта) в запрос не включаются
      // (см. change add-hardware-catalog, «Выбор позиций фурнитуры»).
      const hardwareSelections: HardwareSelectionDto[] = hardwareLines
        .filter((line): line is HardwareLine & { hardwareOptionId: number } => line.hardwareOptionId !== undefined)
        .map((line) => ({ hardwareOptionId: line.hardwareOptionId, quantity: line.quantity }))

      let pricingPromise: Promise<PricingResponseDto>
      if (selectedConfiguration) {
        const request: PricingRequestDto = {}
        for (const key of COMPONENT_ORDER) {
          if (selectedConfiguration[key]) {
            request[key] = key === 'leaf' ? leafSelection : selection[key]
          }
        }
        if (hardwareSelections.length > 0) {
          request.hardware = hardwareSelections
        }
        pricingPromise = calculatePrice(selectedConfiguration.id, request)
      } else if (leafTypeId !== undefined) {
        // У отдельного полотна нет door_configuration.is_reverse — передаём текущее значение
        // переключателя «Реверс» явно (см. change add-standalone-leaf-pricing).
        const request: PricingRequestDto = { leaf: leafSelection, isReverse: resolvedReverse }
        if (hardwareSelections.length > 0) {
          request.hardware = hardwareSelections
        }
        pricingPromise = calculateLeafPrice(leafTypeId, request)
      } else {
        return
      }

      pricingPromise
        .then((result) => {
          if (!cancelled && requestId === requestSeqRef.current) {
            setPricingResult(result)
            setPricingError(null)
          }
        })
        .catch((error: unknown) => {
          if (!cancelled && requestId === requestSeqRef.current) {
            // Ошибка замещает собой ранее показанный результат, а не отображается рядом с ним
            // (см. design.md изменения redesign-configurator-layout).
            setPricingResult(null)
            setPricingError(error instanceof Error ? error.message : 'Не удалось рассчитать стоимость')
          }
        })
        .finally(() => {
          if (!cancelled && requestId === requestSeqRef.current) {
            setPricingLoading(false)
          }
        })
    }, PRICING_DEBOUNCE_MS)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedConfiguration, leafTypeId, resolvedReverse, selection, mirrorFinishTypeId, hardwareLines])

  function handleReverseChange(value: boolean) {
    setReverseSelection(value)
    setSelectedCollectionId(undefined)
    setCascadeSelection({})
    setSelection(emptySelection())
  }

  function handleMirrorFinishEnabledChange(checked: boolean) {
    setMirrorFinishEnabled(checked)
    setMirrorFinishTypeId(undefined)
    setSelectedCollectionId(undefined)
    setCascadeSelection({})
    setSelection(emptySelection())
  }

  function handleMirrorFinishTypeChange(id: number | undefined) {
    setMirrorFinishTypeId(id)
    setSelectedCollectionId(undefined)
    setCascadeSelection({})
    setSelection(emptySelection())
  }

  function handleCollectionChange(id: number | undefined) {
    setSelectedCollectionId(id)
    setCascadeSelection({})
    setSelection(emptySelection())
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
  }

  function updateSelection(key: ComponentKey, patch: Partial<ComponentSelectionDto>) {
    const isLeafHeightChange = key === 'leaf' && ('heightOptionId' in patch || 'customHeightValueMm' in patch)
    setSelection((prev) => {
      const next = { ...prev, [key]: { ...prev[key], ...patch } }
      if (isLeafHeightChange) {
        next.edge = { ...next.edge, heightOptionId: undefined }
        const frameType = cascadeSteps.find((step) => step.key === 'frame')?.resolvedComponent?.type
        if (frameType && HEIGHT_RANGE_FRAME_TYPE_CODES.includes(frameType.code)) {
          next.frame = { ...next.frame, heightOptionId: undefined }
        }
        if (frameType && HEIGHT_MIRROR_FRAME_TYPE_CODES.includes(frameType.code)) {
          next.frame = { ...next.frame, customHeightValueMm: undefined }
        }
        const frameExtensionsType = cascadeSteps.find((step) => step.key === 'frameExtensions')?.resolvedComponent?.type
        if (frameExtensionsType && LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES.includes(frameExtensionsType.code)) {
          next.frameExtensions = { ...next.frameExtensions, lengthOptionId: undefined }
        }
        const doorCasingType = cascadeSteps.find((step) => step.key === 'doorCasing')?.resolvedComponent?.type
        if (doorCasingType && LENGTH_RANGE_DOOR_CASING_TYPE_CODES.includes(doorCasingType.code)) {
          next.doorCasing = { ...next.doorCasing, lengthOptionId: undefined }
        }
      }
      return next
    })
  }

  function addHardwareLine() {
    setHardwareLines((prev) => [...prev, { key: nextHardwareLineKey }])
    setNextHardwareLineKey((key) => key + 1)
  }

  function removeHardwareLine(key: number) {
    setHardwareLines((prev) => prev.filter((line) => line.key !== key))
  }

  function updateHardwareLine(key: number, patch: Partial<HardwareLine>) {
    setHardwareLines((prev) => prev.map((line) => (line.key === key ? { ...line, ...patch } : line)))
  }

  // resolvedReverse (не selectedConfiguration?.reverse) — оно совпадает с ней, когда конфигурация
  // определена, но остаётся верным и до этого, пока действует расчёт отдельного полотна
  // (см. change add-standalone-leaf-pricing), которому тоже передаётся именно resolvedReverse.
  const surchargeBreakdown = computeSurchargeBreakdown(
    pricingSurcharges,
    selection.leaf,
    mirrorFinishTypeId,
    resolvedReverse,
  )

  function renderCascadeStep(step: CascadeStep) {
    const component = step.resolvedComponent
    return (
      <Fragment key={step.key}>
        <OptionGroup
          label={COMPONENT_LABELS[step.key]}
          options={[
            ...step.availableTypes.map((type) => ({
              id: type.id,
              label: step.key === 'edge' ? displayName(type) : type.name,
            })),
            ...(step.hasNoneOption ? [{ id: NONE_OPTION_ID, label: NONE_OPTION_LABELS[step.key] }] : []),
          ]}
          selectedId={step.selectedId}
          onChange={(id) => handleCascadeStepChange(step.key, id)}
        />
        {component && (
          <Card
            size="small"
            title={`${COMPONENT_LABELS[step.key]}: ${step.key === 'edge' ? displayName(component.type) : component.type.name}`}
          >
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
                options={(step.key === 'frameExtensions' && LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES.includes(component.type.code)
                  ? lengthRangeOptions(component, leafHeightValue)
                  : step.key === 'doorCasing' && LENGTH_RANGE_DOOR_CASING_TYPE_CODES.includes(component.type.code)
                    ? lengthRangeOptions(component, leafHeightValue)
                    : component.dimensionOptions.filter((option) => option.dimensionType.code === LENGTH_TYPE_CODE)
                ).map((option) => ({ id: option.id, label: String(option.value) }))}
                selectedId={selection[step.key].lengthOptionId}
                onChange={(id) => updateSelection(step.key, { lengthOptionId: id, customLengthValueMm: undefined })}
              />
              {step.key === 'leaf' && (
                <Space align="center">
                  <Typography.Text type="secondary">Другое значение длины (мм)</Typography.Text>
                  <InputNumber
                    min={1}
                    step={50}
                    value={selection.leaf.customLengthValueMm}
                    onChange={(value) =>
                      updateSelection('leaf', { customLengthValueMm: value ?? undefined, lengthOptionId: undefined })
                    }
                  />
                </Space>
              )}
              {step.key === 'frame' && HEIGHT_MIRROR_FRAME_TYPE_CODES.includes(component.type.code) ? (
                <OptionGroup
                  label="Высота"
                  options={
                    leafHeightValue === undefined
                      ? []
                      : [{ id: MIRROR_HEIGHT_OPTION_ID, label: String(leafHeightValue) }]
                  }
                  selectedId={selection.frame.customHeightValueMm !== undefined ? MIRROR_HEIGHT_OPTION_ID : undefined}
                  onChange={(id) =>
                    updateSelection('frame', {
                      customHeightValueMm: id === MIRROR_HEIGHT_OPTION_ID ? leafHeightValue : undefined,
                    })
                  }
                />
              ) : (
                <OptionGroup
                  label="Высота"
                  options={(step.key === 'edge'
                    ? edgeHeightOptions(component, leafHeightValue)
                    : step.key === 'frame' && HEIGHT_RANGE_FRAME_TYPE_CODES.includes(component.type.code)
                      ? frameHeightRangeOptions(component, leafHeightValue)
                      : component.dimensionOptions.filter((option) => option.dimensionType.code === HEIGHT_TYPE_CODE)
                  ).map((option) => ({ id: option.id, label: String(option.value) }))}
                  selectedId={selection[step.key].heightOptionId}
                  onChange={(id) => updateSelection(step.key, { heightOptionId: id, customHeightValueMm: undefined })}
                />
              )}
              {step.key === 'leaf' && (
                <Space align="center">
                  <Typography.Text type="secondary">Другое значение высоты (мм)</Typography.Text>
                  <InputNumber
                    min={1}
                    step={50}
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
                variant={step.key === 'leaf' ? 'select' : 'buttons'}
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
  }

  const leafPanelContent = (
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
          options={mirrorFinishStep.options.map((type) => ({ id: type.id, label: displayName(type) }))}
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
      {cascadeSteps.filter((step) => LEAF_PANEL_STEP_KEYS.includes(step.key)).map(renderCascadeStep)}
    </Space>
  )

  const frameGroupPanelContent = (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {cascadeSteps.filter((step) => FRAME_GROUP_PANEL_STEP_KEYS.includes(step.key)).map(renderCascadeStep)}
    </Space>
  )

  const hardwarePanelContent = (
    <>
      {hardwareCatalogLoading && <Spin />}
      {hardwareCatalogError && <Alert type="error" message={hardwareCatalogError} showIcon />}
      {!hardwareCatalogLoading && !hardwareCatalogError && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {hardwareLines.map((line) => (
            <Card size="small" key={line.key}>
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <OptionGroup
                  label="Категория"
                  options={hardwareCatalog.map((category) => ({ id: category.category.id, label: category.category.name }))}
                  selectedId={line.categoryId}
                  onChange={(id) => updateHardwareLine(line.key, { categoryId: id, typeId: undefined, hardwareOptionId: undefined })}
                  variant="select"
                />
                <OptionGroup
                  label="Тип"
                  options={hardwareTypesFor(hardwareCatalog, line.categoryId).map((type) => ({
                    id: type.type.id,
                    label: type.type.name,
                  }))}
                  selectedId={line.typeId}
                  onChange={(id) => updateHardwareLine(line.key, { typeId: id, hardwareOptionId: undefined })}
                  variant="select"
                />
                <OptionGroup
                  label="Цвет"
                  options={hardwareOptionsFor(hardwareCatalog, line.categoryId, line.typeId).map((option) => ({
                    id: option.id,
                    label: option.colourName,
                  }))}
                  selectedId={line.hardwareOptionId}
                  onChange={(id) => updateHardwareLine(line.key, { hardwareOptionId: id })}
                  variant="select"
                />
                <Space align="center">
                  <Typography.Text type="secondary">Количество</Typography.Text>
                  <InputNumber
                    min={1}
                    value={line.quantity ?? 1}
                    onChange={(value) => updateHardwareLine(line.key, { quantity: value ?? undefined })}
                  />
                  <Button danger onClick={() => removeHardwareLine(line.key)}>
                    Удалить
                  </Button>
                </Space>
              </Space>
            </Card>
          ))}
          <Button onClick={addHardwareLine}>Добавить позицию фурнитуры</Button>
        </Space>
      )}
    </>
  )

  // Пока новый расчёт ожидает ответа (включая паузу debounce), ранее показанный результат остаётся
  // видимым приглушённым, а не скрывается — иначе быстрый ввод вызывал бы постоянное «моргание» пустой
  // sticky-панели (см. specs/door-configurator-ui, «Приглушённое отображение предыдущего результата
  // во время пересчёта»).
  const pricingResultDimmed = pricingLoading && pricingResult !== null

  return (
    <div className="page">
      {updateCheck?.updateAvailable && (
        <Alert
          style={{ marginBottom: 16 }}
          type="info"
          showIcon
          closable
          message={`Доступна новая версия приложения: ${updateCheck.latestVersion}`}
          description={
            updateCheck.downloadUrl && (
              <a href={updateCheck.downloadUrl} target="_blank" rel="noreferrer">
                Скачать обновление
              </a>
            )
          }
        />
      )}

      <div className="app-columns">
        <div className="app-sidebar">
          <Typography.Title level={5} style={{ marginTop: 0 }}>
            Сервисы
          </Typography.Title>
          <Menu mode="inline" selectable={false} selectedKeys={['door-configurator']} items={SERVICE_MENU_ITEMS} />
        </div>

        <div className="app-main">
          {catalogLoading && <Spin />}
          {catalogError && <Alert type="error" message={catalogError} showIcon />}
          {!catalogLoading && !catalogError && configurations.length === 0 && (
            <Empty description="Нет доступных конфигураций" />
          )}
          {!catalogLoading && !catalogError && configurations.length > 0 && (
            <Collapse
              defaultActiveKey={[]}
              items={[
                { key: 'leaf', label: 'Полотно', children: leafPanelContent },
                { key: 'frameGroup', label: 'Короб и обрамление', children: frameGroupPanelContent },
                { key: 'hardware', label: 'Фурнитура', children: hardwarePanelContent },
              ]}
            />
          )}
        </div>

        <div className="app-pricing">
          <Typography.Title level={4} style={{ marginTop: 0 }}>
            Расчёт стоимости
          </Typography.Title>
          {leafTypeId === undefined && (
            <Empty
              description={
                pendingStepKey
                  ? `Выберите «${COMPONENT_LABELS[pendingStepKey]}», чтобы продолжить расчёт`
                  : 'Выберите конфигурацию, чтобы увидеть расчёт'
              }
            />
          )}
          {leafTypeId !== undefined && (
            <div style={{ opacity: pricingResultDimmed ? 0.55 : 1, transition: 'opacity 0.15s ease' }}>
              {pricingError && <Alert type="error" message={pricingError} showIcon />}
              {!pricingError && pricingResult && (
                <>
                  <Space direction="vertical" size="small">
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
                    size="small"
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
                  {pricingResult.hardware.length > 0 && (
                    <List
                      style={{ marginTop: 16 }}
                      size="small"
                      bordered
                      header={<Typography.Text type="secondary">Фурнитура</Typography.Text>}
                      dataSource={pricingResult.hardware}
                      renderItem={(item) => (
                        <List.Item>
                          {item.category.name} — {item.type.name} ({item.colourName}) × {item.quantity} шт.:{' '}
                          {item.retailPrice} ₽ / {item.dealerPrice} ₽ (дилер)
                        </List.Item>
                      )}
                    />
                  )}
                </>
              )}
              {!pricingError && !pricingResult && pricingLoading && (
                <Space align="center">
                  <Spin size="small" />
                  <Typography.Text type="secondary">Считаем стоимость…</Typography.Text>
                </Space>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default App
