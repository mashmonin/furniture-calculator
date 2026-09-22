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
  Tag,
  Typography,
} from 'antd'
import { DeleteOutlined, HomeOutlined } from '@ant-design/icons'
import {
  calculateFrameGroupPrice,
  calculateHardwarePrice,
  calculateLeafPrice,
  exportSpecification,
  fetchDoorConfigurations,
  fetchHardwareCatalog,
  fetchPricingSurcharges,
} from './api/doorConfigurations'
import { fetchUpdateCheck } from './api/updateCheck'
import type {
  ComponentCatalogDto,
  ComponentKey,
  ComponentSelectionDto,
  DimensionRangeDto,
  DimensionSurchargeRuleDto,
  DoorConfigurationDto,
  FrameGroupPricingRequestDto,
  FrameGroupPricingResponseDto,
  HardwareCategoryDto,
  HardwareOptionDto,
  HardwarePricingResponseDto,
  HardwareSelectionDto,
  HardwareTypeDto,
  LeafPanelType,
  LinerDimensionOptionDto,
  PricingRequestDto,
  PricingResponseDto,
  PricingSurchargesDto,
  ReferenceDto,
  SpecificationExportRequestDto,
  UpdateCheckDto,
} from './api/types'
import { OptionGroup } from './components/OptionGroup'
import './App.css'

const COMPONENT_ORDER: ComponentKey[] = ['leaf', 'frame', 'edge', 'doorCasing', 'frameExtensions']

// Сортировка объединённой разбивки по компонентам из ответов этапов «Полотно» ([leaf, edge]) и «Короб и
// обрамление» ([frame, doorCasing, frameExtensions]) в привычном порядке COMPONENT_ORDER — простая
// конкатенация дала бы [leaf, edge, frame, ...] вместо [leaf, frame, edge, ...] (см. change
// frontend-staged-pricing, design.md).
function componentOrderIndex(component: string): number {
  const index = COMPONENT_ORDER.indexOf(component as ComponentKey)
  return index === -1 ? COMPONENT_ORDER.length : index
}

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

// Длина у этой позиции (2170 мм) описывает одну зарезную стойку внутри комплекта, а не комплект целиком —
// показ её рядом с «× 2» вводит в заблуждение, как будто это длина всего комплекта (см. change
// refine-catalog-and-edge-reverse-rule).
const FRAME_POST_NAME_WITHOUT_LENGTH = 'Комплект зарезных стоек'

const COMPONENT_LABELS: Record<ComponentKey, string> = {
  leaf: 'Полотно',
  frame: 'Коробка',
  edge: 'Кромка',
  doorCasing: 'Наличник',
  frameExtensions: 'Добор',
}

// Тип полотна (панель leaf_type) — пред-коллекционный псевдо-шаг каскада (см. resolvePanelTypeStep),
// сегментированный переключатель в стиле макета Figma (те же радио-кнопки, что у «Кромки»/«Короба»/
// «Толщины»). Сужает каталог и заменяет прежний переключатель «Нужно зеркало» (см. change
// filter-by-leaf-panel-type, show-leaf-panel-type, add-leaf-panel-type). Синтетические id нужны только
// для OptionGroup.
const LEAF_PANEL_TYPE_OPTIONS: { code: LeafPanelType; id: number; label: string }[] = [
  { code: 'BLIND', id: 1, label: 'Глухое' },
  { code: 'GLAZED', id: 2, label: 'С остеклением' },
  { code: 'MIRRORED', id: 3, label: 'Зеркальное' },
]

const COLLECTION_LABEL = 'Коллекция'
const MIRROR_FINISH_LABEL = 'Вид зеркала'
const GLAZING_LABEL = 'Вид остекления'

// Синтетический id варианта «без этого компонента» — реальные id из БД начинаются с 1.
const NONE_OPTION_ID = 0

// Синтетические id варианта «Другое» в группах «Длина»/«Высота» полотна — показывают его как ещё один
// сегмент в общем ряду со стандартными значениями (см. change restyle-configurator-per-figma); выбор
// этого варианта не задаёт lengthOptionId/heightOptionId, а лишь переключает ряд в режим произвольного
// значения (ввод — в поле ниже, customLengthValueMm/customHeightValueMm).
const CUSTOM_LENGTH_OPTION_ID = -2
const CUSTOM_HEIGHT_OPTION_ID = -3
const CUSTOM_OPTION_LABEL = 'Другое'

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
// Каскадная наценка за промежуточные значения сетки 50мм высоты полотна (см. change
// add-leaf-height-cascade-surcharge-50mm-grid) — дублирует ту же логику, что и
// DoorConfigurationPricingService.resolveHeightCascadeSurchargePercent на backend.
const HEIGHT_GRID_FLOOR = 1900
const HEIGHT_GRID_STEP = 50
const CASCADE_STEP_PERCENT = 20
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

interface PanelTypeStep {
  visible: boolean
  options: LeafPanelType[]
  value: LeafPanelType
}

// Тип полотна — пред-коллекционный псевдо-шаг, симметричный resolveReverseStep: идёт сразу после реверса
// и до коллекции, оценивается по configurations, суженным реверсом. Заменяет прежний переключатель
// «Нужно зеркало» (см. design.md, change filter-by-leaf-panel-type) — выбор «Зеркальное» даёт то же
// сужение и раскрывает тот же шаг выбора исполнения. Показывается, только если среди кандидатов
// встречается более одного значения panelType; по умолчанию выбрано «Глухое», если оно есть среди
// вариантов, иначе — первый встречающийся.
function resolvePanelTypeStep(
  configurations: DoorConfigurationDto[],
  panelTypeSelection: LeafPanelType | undefined,
): { panelTypeStep?: PanelTypeStep; resolvedPanelType: LeafPanelType | undefined } {
  const panelTypes = Array.from(
    new Set(configurations.map((configuration) => configuration.leaf.panelType).filter((type): type is LeafPanelType => Boolean(type))),
  )
  if (panelTypes.length <= 1) {
    return { resolvedPanelType: panelTypes[0] }
  }
  const resolved =
    panelTypeSelection !== undefined && panelTypes.includes(panelTypeSelection)
      ? panelTypeSelection
      : (panelTypes.includes('BLIND') ? 'BLIND' : panelTypes[0])
  return { panelTypeStep: { visible: true, options: panelTypes, value: resolved }, resolvedPanelType: resolved }
}

interface MirrorFinishStep {
  visible: boolean
  options: ReferenceDto[]
}

// Выбор конкретного исполнения зеркала — раскрывается только когда resolvedPanelType === 'MIRRORED'
// (см. resolvePanelTypeStep), от configurations, уже суженных по типу полотна.
function resolveMirrorFinishStep(configurations: DoorConfigurationDto[]): MirrorFinishStep {
  const options = uniqueById(configurations.flatMap((configuration) => configuration.leaf.mirrorFinishOptions))
  return { visible: options.length > 0, options }
}

interface GlazingStep {
  visible: boolean
  options: ReferenceDto[]
}

// Выбор вида остекления — симметричен resolveMirrorFinishStep, раскрывается только когда
// resolvedPanelType === 'GLAZED' (см. change add-glazing-catalog-for-v-models). Выбранный вид остекления
// по-прежнему нигде не сужает каталог, но теперь участвует в запросе расчёта стоимости — наценка за вид
// остекления (см. change add-glazing-price-surcharge).
function resolveGlazingStep(configurations: DoorConfigurationDto[]): GlazingStep {
  const options = uniqueById(configurations.flatMap((configuration) => configuration.leaf.glazingOptions))
  return { visible: options.length > 0, options }
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

// Диапазон допустимой нестандартной длины/высоты по коллекции полотна — см. change
// add-collection-dimension-range. Данные уже загружены с каталогом компонентов (не константа в коде
// фронтенда), отсутствие записи для оси означает, что диапазон для неё не задан — подсказка не
// показывается (то же самое "отсутствие = проверка пропускается", что и на backend).
function dimensionRangeHint(component: ComponentCatalogDto, dimensionTypeCode: string): DimensionRangeDto | undefined {
  return component.dimensionRanges.find((r) => r.dimensionType.code === dimensionTypeCode)
}

// Тот же визуальный стиль бейджа, что и «Длина погонажа: X мм»/«для высоты полотна: Y мм» у кромки
// (см. renderEdgeCard) — Tag с полужирным числовым значением.
function dimensionRangeTag(component: ComponentCatalogDto, dimensionTypeCode: string) {
  const range = dimensionRangeHint(component, dimensionTypeCode)
  if (!range) {
    return null
  }
  return (
    <Tag>
      Доступно: <strong>{range.minValue}–{range.maxValue}</strong> мм
    </Tag>
  )
}

// Проценты надбавок вычисляются локально из уже загруженных правил (см. design.md) — не из ответа calculate(),
// эндпоинт расчёта стоимости не меняется и разбивку не возвращает.
// Правило, привязанное к конкретному leaf_type, имеет приоритет перед общим (leafTypeId === null) —
// см. change add-leaf-height-2800-2900-except-sibir-03, та же логика, что и на backend
// (DoorConfigurationPricingService.resolveAxisSurchargeMultiplier).
function findDimensionSurchargeRule(
  pricingSurcharges: PricingSurchargesDto,
  dimensionTypeCode: string,
  value: number,
  leafTypeId: number | undefined,
): DimensionSurchargeRuleDto | undefined {
  const rulesForValue = pricingSurcharges.dimensionSurchargeRules.filter(
    (rule) => rule.dimensionType.code === dimensionTypeCode && rule.value === value,
  )
  return (
    rulesForValue.find((rule) => rule.leafTypeId === leafTypeId) ??
    rulesForValue.find((rule) => rule.leafTypeId === null)
  )
}

// Каскадный спуск по сетке 50мм высоты полотна от 1900мм — см. change
// add-leaf-height-cascade-surcharge-50mm-grid. Блокирующие строки (unavailable) backend не отдаёт через
// GET /api/pricing-surcharges (см. design.md), поэтому здесь их учитывать не нужно: этот блок рендерится
// только после успешного расчёта, а высота, которую backend бы отклонил (в т.ч. через блокировку),
// просто не доходит до этой точки.
function findHeightCascadeSurchargePercent(
  pricingSurcharges: PricingSurchargesDto,
  leafTypeId: number | undefined,
  value: number,
): number | undefined {
  if (value < HEIGHT_GRID_FLOOR || (value - HEIGHT_GRID_FLOOR) % HEIGHT_GRID_STEP !== 0) {
    return undefined
  }
  let probe = value - HEIGHT_GRID_STEP
  let steps = 1
  while (probe >= HEIGHT_GRID_FLOOR) {
    const found = findDimensionSurchargeRule(pricingSurcharges, HEIGHT_TYPE_CODE, probe, leafTypeId)
    if (found) {
      return found.surchargePercent + CASCADE_STEP_PERCENT * steps
    }
    probe -= HEIGHT_GRID_STEP
    steps += 1
  }
  return undefined
}

// Значение, совпадающее со стандартной каталожной опцией этой оси, никогда не несёт наценку — та же
// проверка, что и первым шагом на backend (DoorConfigurationPricingService.resolveAxisSurchargeMultiplier,
// matchesStandardSize), обязательна и здесь: иначе, например, для 2000/2100мм (стандартные высоты,
// но не входящие ни в одну строку dimension_surcharge_rule) каскад ошибочно унаследовал бы наценку от
// соседнего меньшего значения 1950мм, хотя реальный расчёт наценку не применяет.
function matchesStandardDimension(component: ComponentCatalogDto | undefined, dimensionTypeCode: string, value: number): boolean {
  return (
    component?.dimensionOptions.some((option) => option.dimensionType.code === dimensionTypeCode && option.value === value) ??
    false
  )
}

function computeSurchargeBreakdown(
  pricingSurcharges: PricingSurchargesDto | null,
  leafSelection: ComponentSelectionDto,
  leafComponent: ComponentCatalogDto | undefined,
  leafTypeId: number | undefined,
  mirrorFinishTypeId: number | undefined,
  glazingTypeId: number | undefined,
  isReverse: boolean,
): SurchargeBreakdownItem[] {
  if (!pricingSurcharges) {
    return []
  }
  const items: SurchargeBreakdownItem[] = []
  const lengthRule =
    leafSelection.customLengthValueMm !== undefined &&
    !matchesStandardDimension(leafComponent, LENGTH_TYPE_CODE, leafSelection.customLengthValueMm)
      ? findDimensionSurchargeRule(
          pricingSurcharges, LENGTH_TYPE_CODE, leafSelection.customLengthValueMm, leafTypeId,
        )
      : undefined
  if (lengthRule) {
    items.push({ label: 'За нестандартную ширину', percent: lengthRule.surchargePercent })
  }
  const isCustomHeightStandard =
    leafSelection.customHeightValueMm !== undefined &&
    matchesStandardDimension(leafComponent, HEIGHT_TYPE_CODE, leafSelection.customHeightValueMm)
  const heightRule =
    leafSelection.customHeightValueMm !== undefined && !isCustomHeightStandard
      ? findDimensionSurchargeRule(
          pricingSurcharges, HEIGHT_TYPE_CODE, leafSelection.customHeightValueMm, leafTypeId,
        )
      : undefined
  const heightPercent =
    heightRule?.surchargePercent ??
    (leafSelection.customHeightValueMm !== undefined && !isCustomHeightStandard
      ? findHeightCascadeSurchargePercent(pricingSurcharges, leafTypeId, leafSelection.customHeightValueMm)
      : undefined)
  if (heightPercent !== undefined) {
    items.push({ label: 'За нестандартную высоту', percent: heightPercent })
  }
  // В отличие от длины/высоты, у толщины нет режима произвольного значения — клиент выбирает только id
  // каталожной опции (см. change add-leaf-thickness-59mm-option), поэтому значение резолвится через сам
  // выбранный dimensionOptions.id (а не через customLengthValueMm/customHeightValueMm), а дальше та же
  // наценка ищется тем же findDimensionSurchargeRule, что и для длины/высоты.
  const thicknessValue =
    leafSelection.thicknessOptionId !== undefined
      ? leafComponent?.dimensionOptions.find(
          (option) => option.dimensionType.code === THICKNESS_TYPE_CODE && option.id === leafSelection.thicknessOptionId,
        )?.value
      : undefined
  const thicknessRule =
    thicknessValue !== undefined
      ? findDimensionSurchargeRule(pricingSurcharges, THICKNESS_TYPE_CODE, thicknessValue, leafTypeId)
      : undefined
  if (thicknessRule) {
    items.push({ label: 'За нестандартную толщину', percent: thicknessRule.surchargePercent })
  }
  // colourOptionId ссылается на строку colour_option (владение), а не на colour_type напрямую — в отличие
  // от mirrorFinishTypeId/glazingTypeId, поэтому наценку ищем в два шага: colourOptionId -> colourType.id
  // (из уже загруженного каталога leafComponent.colourOptions) -> процент в pricingSurcharges.colourSurcharges
  // (см. change add-leaf-ral-ncs-colour-surcharge, design.md). При двусторонней покраске (см. change
  // add-leaf-double-sided-painting) проценты фронтального и заднего цвета складываются в одну строку — той
  // же суммой, что рассчитал backend, а не последовательным перемножением множителей каждой стороны.
  const colourSurchargePercent = (colourOptionId: number | undefined): number =>
    (colourOptionId !== undefined
      ? pricingSurcharges.colourSurcharges.find(
          (surcharge) => surcharge.id === leafComponent?.colourOptions.find((option) => option.id === colourOptionId)?.colourType.id,
        )?.surchargePercent
      : undefined) ?? 0
  let colourPercent = colourSurchargePercent(leafSelection.colourOptionId)
  if (leafSelection.doubleSidedPainting) {
    colourPercent += colourSurchargePercent(leafSelection.backColourOptionId)
  }
  if (colourPercent !== 0) {
    items.push({ label: 'За выбранный цвет', percent: colourPercent })
  }
  if (leafSelection.doubleSidedPainting) {
    items.push({ label: 'За двустороннюю покраску', percent: pricingSurcharges.doubleSidedPaintingSurchargePercent })
  }
  const mirrorFinishSurcharge =
    mirrorFinishTypeId !== undefined
      ? pricingSurcharges.mirrorFinishSurcharges.find((surcharge) => surcharge.id === mirrorFinishTypeId)
      : undefined
  if (mirrorFinishSurcharge) {
    items.push({ label: 'За исполнение зеркала', percent: mirrorFinishSurcharge.surchargePercent })
  }
  // «Прозрачное» тоже присутствует в glazingSurcharges (с surchargePercent = 0) — базовое остекление не
  // должно показываться как надбавка (см. change add-glazing-price-surcharge, «Базовое «Прозрачное»
  // остекление не показывается как надбавка»).
  const glazingSurcharge =
    glazingTypeId !== undefined
      ? pricingSurcharges.glazingSurcharges.find((surcharge) => surcharge.id === glazingTypeId)
      : undefined
  if (glazingSurcharge && glazingSurcharge.surchargePercent !== 0) {
    items.push({ label: 'За вид остекления', percent: glazingSurcharge.surchargePercent })
  }
  if (isReverse) {
    items.push({ label: 'За реверс', percent: pricingSurcharges.reverseSurchargePercent })
  }
  return items
}

// Процент наценки за нестандартную длину/высоту погонажа (короб/наличник/добор) для конкретного
// владельца и выбранного значения — по тому же принципу, что и computeSurchargeBreakdown для полотна:
// вычисляется локально из уже загруженных правил, не из ответа calculate() (см. design.md изменения
// add-pogonazh-length-surcharge, «Фронтенд»). undefined означает базовое значение — надбавки нет.
function pogonazhSurchargePercent(
  pricingSurcharges: PricingSurchargesDto | null,
  ownerType: string,
  ownerId: number | undefined,
  value: number | undefined,
): number | undefined {
  if (!pricingSurcharges || ownerId === undefined || value === undefined) {
    return undefined
  }
  const ownerRules = pricingSurcharges.pogonazhSurchargeRules.filter(
    (rule) => rule.ownerType === ownerType && rule.ownerId === ownerId,
  )
  const pointRule = ownerRules.find((rule) => rule.value === value)
  if (pointRule) {
    return pointRule.surchargePercent
  }
  // Диапазонные правила (value null, min_value_exclusive/max_value_inclusive) — см. change
  // add-pogonazh-surcharge-70-100-percent-tiers; на практике встречаются только у короба «Фантом».
  return ownerRules.find(
    (rule) =>
      rule.value === null &&
      (rule.minValueExclusive === null || value > rule.minValueExclusive) &&
      (rule.maxValueInclusive === null || value <= rule.maxValueInclusive),
  )?.surchargePercent
}

const SERVICE_MENU_ITEMS = [{ key: 'door-configurator', label: 'Межкомнатные двери' }]

// Шапка приложения (см. specs/door-configurator-ui, «Шапка приложения») — статичный текст, без
// отдельного API: подпись прайс-листа задаётся здесь же и правится однострочно при смене прайс-листа
// (см. design.md изменения restyle-configurator-per-figma, риск «Хардкод текста подписи прайс-листа»).
const APP_TITLE = 'Я-КОНФИГУРАТОР'
const PRICE_LIST_LABEL = 'Прайс-лист: hausdoors_emal_i_shpon_rf_13_07_2026'

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
  const [panelTypeSelection, setPanelTypeSelection] = useState<LeafPanelType | undefined>(undefined)
  const [mirrorFinishTypeId, setMirrorFinishTypeId] = useState<number | undefined>(undefined)
  const [glazingTypeId, setGlazingTypeId] = useState<number | undefined>(undefined)
  const [selectedCollectionId, setSelectedCollectionId] = useState<number | undefined>(undefined)
  const [cascadeSelection, setCascadeSelection] = useState<Partial<Record<ComponentKey, number>>>({})
  const [selection, setSelection] = useState(emptySelection)
  // Активен ли сегмент «Другое» в группе «Длина»/«Высота» полотна — отдельно от customLengthValueMm/
  // customHeightValueMm, чтобы поле ввода разблокировалось сразу по клику на «Другое», ещё до того, как
  // введено само значение (см. change restyle-configurator-per-figma).
  const [customLengthMode, setCustomLengthMode] = useState(false)
  const [customHeightMode, setCustomHeightMode] = useState(false)

  // Три независимых этапа расчёта стоимости — «Полотно» (+кромка), «Короб и обрамление», «Фурнитура» —
  // каждый со своей тройкой состояния и своим requestSeqRef (защита от гонки устаревших ответов, см.
  // design.md изменения redesign-configurator-layout), поскольку каждый этап вызывает свой backend-эндпоинт
  // независимо от готовности двух других (см. change add-staged-pricing-endpoints, frontend-staged-pricing).
  const [leafPricingResult, setLeafPricingResult] = useState<PricingResponseDto | null>(null)
  const [leafPricingLoading, setLeafPricingLoading] = useState(false)
  const [leafPricingError, setLeafPricingError] = useState<string | null>(null)
  const leafRequestSeqRef = useRef(0)

  const [frameGroupPricingResult, setFrameGroupPricingResult] = useState<FrameGroupPricingResponseDto | null>(null)
  const [frameGroupPricingLoading, setFrameGroupPricingLoading] = useState(false)
  const [frameGroupPricingError, setFrameGroupPricingError] = useState<string | null>(null)
  const frameGroupRequestSeqRef = useRef(0)

  const [hardwarePricingResult, setHardwarePricingResult] = useState<HardwarePricingResponseDto | null>(null)
  const [hardwarePricingLoading, setHardwarePricingLoading] = useState(false)
  const [hardwarePricingError, setHardwarePricingError] = useState<string | null>(null)
  const hardwareRequestSeqRef = useRef(0)

  // Выгрузка спецификации (см. change add-specification-export) — отдельное состояние загрузки/ошибки, не
  // трогающее уже показанный результат расчёта в pricingResult/pricingError.
  const [exportLoading, setExportLoading] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)

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

  const { panelTypeStep, resolvedPanelType } = resolvePanelTypeStep(reverseFilteredConfigurations, panelTypeSelection)
  // «Глухое» — не отдельная непересекающаяся категория моделей, а базовое исполнение, доступное любому
  // полотну (в том числе тем, что дополнительно поддерживают зеркало/остекление) — поэтому оно не сужает
  // каталог, в отличие от «Зеркальное»/«С остеклением», которые сужают точным совпадением panelType до
  // моделей, дополнительно это поддерживающих (см. обратную связь после первой реализации, design.md).
  const panelTypeFilteredConfigurations =
    resolvedPanelType === 'BLIND'
      ? reverseFilteredConfigurations
      : reverseFilteredConfigurations.filter((configuration) => configuration.leaf.panelType === resolvedPanelType)
  const mirrorFinishStep =
    resolvedPanelType === 'MIRRORED' ? resolveMirrorFinishStep(panelTypeFilteredConfigurations) : { visible: false, options: [] }
  const glazingStep =
    resolvedPanelType === 'GLAZED' ? resolveGlazingStep(panelTypeFilteredConfigurations) : { visible: false, options: [] }

  const collectionOptions = uniqueById(
    panelTypeFilteredConfigurations
      .map((configuration) => configuration.leaf.collection)
      .filter((type): type is ReferenceDto => Boolean(type)),
  )
  const collectionFilteredConfigurations =
    selectedCollectionId === undefined
      ? []
      : panelTypeFilteredConfigurations.filter((configuration) => configuration.leaf.collection?.id === selectedCollectionId)

  // Первый проход — только чтобы узнать leafHeightValue (шаг leaf не зависит от неё, поэтому второй
  // проход её не меняет). Второй проход использует эту высоту, чтобы исключить короб «НЕО» из шага
  // «frame», если он ей не покрывается (см. design.md, Decision 7).
  const firstPassSteps = buildCascadeSteps(collectionFilteredConfigurations, cascadeSelection)
  const leafComponent = firstPassSteps.steps.find((step) => step.key === 'leaf')?.resolvedComponent
  const leafHeightValue =
    selection.leaf.customHeightValueMm ??
    leafComponent?.dimensionOptions.find((option) => option.id === selection.leaf.heightOptionId)?.value

  // selectedConfiguration больше не читается: каждый из трёх этапов расчёта стоимости (см. ниже) резолвится
  // по своим собственным id (leafTypeId/edgeTypeId, frameTypeId/doorCasingTypeId/frameExtensionsTypeId), а
  // не по единой согласованной door_configuration (см. change frontend-staged-pricing) — но сама функция
  // buildCascadeSteps продолжает вычислять его для других потенциальных потребителей cascadeSteps.
  const { steps: cascadeSteps } = buildCascadeSteps(
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

  // Для короба, кромки, наличника и добора значение (высоты или длины погонажа — в зависимости от
  // компонента), зависящее от высоты полотна, подбирается автоматически из каталога (см. useEffect'ы
  // ниже) и только показывается в лейбле карточки «Длина погонажа: X для высоты полотна Y» — без
  // отдельного клика пользователя (см. обратную связь по change restyle-configurator-per-figma).
  const frameComponent = cascadeSteps.find((step) => step.key === 'frame')?.resolvedComponent
  // Этап «Короб и обрамление» расчёта стоимости запускается, как только определён короб, независимо
  // от готовности этапа «Полотно» (см. change add-staged-pricing-endpoints, frontend-staged-pricing).
  const frameTypeId = frameComponent?.type.id
  const frameTypeCode = frameComponent?.type.code
  const frameMatchedOption =
    frameComponent && frameTypeCode !== undefined && leafHeightValue !== undefined && HEIGHT_RANGE_FRAME_TYPE_CODES.includes(frameTypeCode)
      ? frameHeightRangeOptions(frameComponent, leafHeightValue)[0]
      : undefined
  const frameIsMirrorHeight = frameTypeCode !== undefined && HEIGHT_MIRROR_FRAME_TYPE_CODES.includes(frameTypeCode)
  const frameMatchedValue = frameMatchedOption?.value ?? (frameIsMirrorHeight ? leafHeightValue : undefined)

  useEffect(() => {
    if (!frameComponent || leafHeightValue === undefined) {
      return
    }
    if (frameMatchedOption && selection.frame.heightOptionId !== frameMatchedOption.id) {
      updateSelection('frame', { heightOptionId: frameMatchedOption.id, customHeightValueMm: undefined })
    } else if (!frameMatchedOption && frameIsMirrorHeight && selection.frame.customHeightValueMm !== leafHeightValue) {
      updateSelection('frame', { customHeightValueMm: leafHeightValue })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [frameComponent, frameMatchedOption, frameIsMirrorHeight, leafHeightValue, selection.frame.heightOptionId, selection.frame.customHeightValueMm])

  const edgeComponent = cascadeSteps.find((step) => step.key === 'edge')?.resolvedComponent
  // Передаётся в запрос этапа «Полотно» вместе с edge-опциями, как только определена — не блокирует сам
  // расчёт полотна (см. change add-staged-pricing-endpoints, frontend-staged-pricing).
  const edgeTypeId = edgeComponent?.type.id
  const edgeMatchedOption =
    edgeComponent && leafHeightValue !== undefined ? edgeHeightOptions(edgeComponent, leafHeightValue)[0] : undefined
  const edgeMatchedValue = edgeMatchedOption?.value

  useEffect(() => {
    if (!edgeComponent || leafHeightValue === undefined) {
      return
    }
    if (edgeMatchedOption && selection.edge.heightOptionId !== edgeMatchedOption.id) {
      updateSelection('edge', { heightOptionId: edgeMatchedOption.id, customHeightValueMm: undefined })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edgeComponent, edgeMatchedOption, leafHeightValue, selection.edge.heightOptionId])

  const doorCasingComponent = cascadeSteps.find((step) => step.key === 'doorCasing')?.resolvedComponent
  const doorCasingTypeId = doorCasingComponent?.type.id
  const doorCasingTypeCode = doorCasingComponent?.type.code
  const doorCasingMatchedOption =
    doorCasingComponent &&
    doorCasingTypeCode !== undefined &&
    leafHeightValue !== undefined &&
    LENGTH_RANGE_DOOR_CASING_TYPE_CODES.includes(doorCasingTypeCode)
      ? lengthRangeOptions(doorCasingComponent, leafHeightValue)[0]
      : undefined
  const doorCasingMatchedValue = doorCasingMatchedOption?.value

  useEffect(() => {
    if (!doorCasingComponent || leafHeightValue === undefined) {
      return
    }
    if (doorCasingMatchedOption && selection.doorCasing.lengthOptionId !== doorCasingMatchedOption.id) {
      updateSelection('doorCasing', { lengthOptionId: doorCasingMatchedOption.id, customLengthValueMm: undefined })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [doorCasingComponent, doorCasingMatchedOption, leafHeightValue, selection.doorCasing.lengthOptionId])

  const frameExtensionsComponent = cascadeSteps.find((step) => step.key === 'frameExtensions')?.resolvedComponent
  const frameExtensionsTypeId = frameExtensionsComponent?.type.id
  const frameExtensionsTypeCode = frameExtensionsComponent?.type.code
  const frameExtensionsMatchedOption =
    frameExtensionsComponent &&
    frameExtensionsTypeCode !== undefined &&
    leafHeightValue !== undefined &&
    LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES.includes(frameExtensionsTypeCode)
      ? lengthRangeOptions(frameExtensionsComponent, leafHeightValue)[0]
      : undefined
  const frameExtensionsMatchedValue = frameExtensionsMatchedOption?.value

  useEffect(() => {
    if (!frameExtensionsComponent || leafHeightValue === undefined) {
      return
    }
    if (frameExtensionsMatchedOption && selection.frameExtensions.lengthOptionId !== frameExtensionsMatchedOption.id) {
      updateSelection('frameExtensions', { lengthOptionId: frameExtensionsMatchedOption.id, customLengthValueMm: undefined })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [frameExtensionsComponent, frameExtensionsMatchedOption, leafHeightValue, selection.frameExtensions.lengthOptionId])

  // Наценка за нестандартную длину/высоту погонажа — по значению, автоматически подобранному выше для
  // короба/наличника/добора (frameMatchedValue/doorCasingMatchedValue/frameExtensionsMatchedValue), не
  // по customLengthValueMm/customHeightValueMm (см. specs/door-configurator-ui, «Разбивка наценки за
  // нестандартный погонаж короба, наличника и добора»).
  const framePogonazhSurchargePercent = pogonazhSurchargePercent(pricingSurcharges, 'frame', frameTypeId, frameMatchedValue)
  const doorCasingPogonazhSurchargePercent =
    pogonazhSurchargePercent(pricingSurcharges, 'doorCasing', doorCasingTypeId, doorCasingMatchedValue)
  const frameExtensionsPogonazhSurchargePercent =
    pogonazhSurchargePercent(pricingSurcharges, 'frameExtensions', frameExtensionsTypeId, frameExtensionsMatchedValue)

  // Автоматический расчёт стоимости по текущему выбору вместо кнопки «Рассчитать стоимость»
  // (см. specs/door-configurator-ui, «Автоматический расчёт стоимости по текущему выбору») — тремя
  // независимыми этапами, каждый ищет свой backend-эндпоинт по своим условиям запуска, не дожидаясь
  // готовности двух других (см. «Расчёт полотна и кромки», «Расчёт короба, наличника и добора», «Расчёт
  // фурнитуры», add-staged-pricing-endpoints). Sticky-панель показывает объединение результатов (см.
  // pricingResult/pricingLoading/pricingError ниже, «Объединение результатов трёх этапов расчёта»).

  // Этап «Полотно» (+ кромка, если определена) — запускается, как только определён тип полотна,
  // независимо от готовности короба/обрамления.
  useEffect(() => {
    // Сбрасываем ранее показанный результат сразу при любом изменении набора опций этого этапа — не
    // оставляем его видимым (даже приглушённым) пока не придёт ответ на новый запрос (см. обратную связь
    // пользователя, заменяет прежнее «приглушённое отображение предыдущего результата»).
    setLeafPricingResult(null)
    setLeafPricingError(null)

    if (leafTypeId === undefined) {
      setLeafPricingLoading(false)
      return
    }

    setLeafPricingLoading(true)
    const requestId = ++leafRequestSeqRef.current
    let cancelled = false

    const timer = window.setTimeout(() => {
      let leafSelection = selection.leaf
      if (mirrorFinishTypeId !== undefined) {
        leafSelection = { ...leafSelection, mirrorFinishTypeId }
      }
      if (glazingTypeId !== undefined) {
        leafSelection = { ...leafSelection, glazingTypeId }
      }

      // У отдельного полотна нет door_configuration.is_reverse — передаём текущее значение
      // переключателя «Реверс» явно (см. change add-standalone-leaf-pricing).
      const request: PricingRequestDto = { leaf: leafSelection, isReverse: resolvedReverse }
      if (edgeTypeId !== undefined) {
        request.edgeTypeId = edgeTypeId
        request.edge = selection.edge
      }

      calculateLeafPrice(leafTypeId, request)
        .then((result) => {
          if (!cancelled && requestId === leafRequestSeqRef.current) {
            setLeafPricingResult(result)
            setLeafPricingError(null)
          }
        })
        .catch((error: unknown) => {
          if (!cancelled && requestId === leafRequestSeqRef.current) {
            // Ошибка замещает собой ранее показанный результат, а не отображается рядом с ним
            // (см. design.md изменения redesign-configurator-layout).
            setLeafPricingResult(null)
            setLeafPricingError(error instanceof Error ? error.message : 'Не удалось рассчитать стоимость')
          }
        })
        .finally(() => {
          if (!cancelled && requestId === leafRequestSeqRef.current) {
            setLeafPricingLoading(false)
          }
        })
    }, PRICING_DEBOUNCE_MS)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [leafTypeId, resolvedReverse, selection.leaf, selection.edge, mirrorFinishTypeId, glazingTypeId, edgeTypeId])

  // Этап «Короб и обрамление» — запускается, как только определён тип короба, независимо от готовности
  // полотна/кромки/фурнитуры; высота полотна передаётся явно (см. leafHeightValue выше).
  useEffect(() => {
    setFrameGroupPricingResult(null)
    setFrameGroupPricingError(null)

    if (frameTypeId === undefined) {
      setFrameGroupPricingLoading(false)
      return
    }

    setFrameGroupPricingLoading(true)
    const requestId = ++frameGroupRequestSeqRef.current
    let cancelled = false

    const timer = window.setTimeout(() => {
      const request: FrameGroupPricingRequestDto = { frame: selection.frame, leafHeightValue }
      if (doorCasingTypeId !== undefined) {
        request.doorCasingTypeId = doorCasingTypeId
        request.doorCasing = selection.doorCasing
      }
      if (frameExtensionsTypeId !== undefined) {
        request.frameExtensionsTypeId = frameExtensionsTypeId
        request.frameExtensions = selection.frameExtensions
      }

      calculateFrameGroupPrice(frameTypeId, request)
        .then((result) => {
          if (!cancelled && requestId === frameGroupRequestSeqRef.current) {
            setFrameGroupPricingResult(result)
            setFrameGroupPricingError(null)
          }
        })
        .catch((error: unknown) => {
          if (!cancelled && requestId === frameGroupRequestSeqRef.current) {
            setFrameGroupPricingResult(null)
            setFrameGroupPricingError(error instanceof Error ? error.message : 'Не удалось рассчитать стоимость')
          }
        })
        .finally(() => {
          if (!cancelled && requestId === frameGroupRequestSeqRef.current) {
            setFrameGroupPricingLoading(false)
          }
        })
    }, PRICING_DEBOUNCE_MS)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [frameTypeId, selection.frame, doorCasingTypeId, selection.doorCasing, frameExtensionsTypeId, selection.frameExtensions, leafHeightValue])

  // Этап «Фурнитура» — запускается, как только определён тип полотна И есть хотя бы одна завершённая
  // позиция фурнитуры; правка полотна/короба не перезапускает этот эффект, и наоборот.
  useEffect(() => {
    setHardwarePricingResult(null)
    setHardwarePricingError(null)

    // Незавершённые позиции (без выбранного цветового варианта) в запрос не включаются
    // (см. change add-hardware-catalog, «Выбор позиций фурнитуры»).
    const hardwareSelections: HardwareSelectionDto[] = hardwareLines
      .filter((line): line is HardwareLine & { hardwareOptionId: number } => line.hardwareOptionId !== undefined)
      .map((line) => ({ hardwareOptionId: line.hardwareOptionId, quantity: line.quantity }))

    if (leafTypeId === undefined || hardwareSelections.length === 0) {
      setHardwarePricingLoading(false)
      return
    }

    setHardwarePricingLoading(true)
    const requestId = ++hardwareRequestSeqRef.current
    let cancelled = false

    const timer = window.setTimeout(() => {
      calculateHardwarePrice({ hardware: hardwareSelections })
        .then((result) => {
          if (!cancelled && requestId === hardwareRequestSeqRef.current) {
            setHardwarePricingResult(result)
            setHardwarePricingError(null)
          }
        })
        .catch((error: unknown) => {
          if (!cancelled && requestId === hardwareRequestSeqRef.current) {
            setHardwarePricingResult(null)
            setHardwarePricingError(error instanceof Error ? error.message : 'Не удалось рассчитать стоимость')
          }
        })
        .finally(() => {
          if (!cancelled && requestId === hardwareRequestSeqRef.current) {
            setHardwarePricingLoading(false)
          }
        })
    }, PRICING_DEBOUNCE_MS)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [leafTypeId, hardwareLines])

  // Объединение результатов трёх этапов в единый результат для sticky-панели (см. specs/door-configurator-ui,
  // «Объединение результатов трёх этапов расчёта») — намеренно названо так же, как раньше называлось
  // единственное состояние расчёта, чтобы JSX sticky-панели ниже не менялся.
  const pricingLoading = leafPricingLoading || frameGroupPricingLoading || hardwarePricingLoading
  const pricingError = leafPricingError ?? frameGroupPricingError ?? hardwarePricingError
  const pricingResult: PricingResponseDto | null =
    !pricingLoading && !pricingError && leafPricingResult
      ? {
          totalRetailPrice:
            leafPricingResult.totalRetailPrice +
            (frameGroupPricingResult?.totalRetailPrice ?? 0) +
            (hardwarePricingResult?.totalRetailPrice ?? 0),
          totalDealerPrice:
            leafPricingResult.totalDealerPrice +
            (frameGroupPricingResult?.totalDealerPrice ?? 0) +
            (hardwarePricingResult?.totalDealerPrice ?? 0),
          components: [...leafPricingResult.components, ...(frameGroupPricingResult?.components ?? [])].sort(
            (a, b) => componentOrderIndex(a.component) - componentOrderIndex(b.component),
          ),
          hardware: hardwarePricingResult?.hardware ?? [],
        }
      : null

  // Кнопка «Скачать excel спецификацию» (см. change add-specification-export) — собирает
  // SpecificationExportRequestDto из уже имеющегося состояния (то же, что уходит в три этапных запроса
  // расчёта выше) и скачивает файл через синтетический <a download>, не трогая уже показанный
  // pricingResult/pricingError при ошибке.
  function handleExportSpecification() {
    if (leafTypeId === undefined) {
      return
    }

    let leafSelection = selection.leaf
    if (mirrorFinishTypeId !== undefined) {
      leafSelection = { ...leafSelection, mirrorFinishTypeId }
    }
    if (glazingTypeId !== undefined) {
      leafSelection = { ...leafSelection, glazingTypeId }
    }

    const request: SpecificationExportRequestDto = {
      leafTypeId,
      leaf: leafSelection,
      isReverse: resolvedReverse,
      leafHeightValue,
    }
    if (edgeTypeId !== undefined) {
      request.edgeTypeId = edgeTypeId
      request.edge = selection.edge
    }
    if (frameTypeId !== undefined) {
      request.frameTypeId = frameTypeId
      request.frame = selection.frame
    }
    if (doorCasingTypeId !== undefined) {
      request.doorCasingTypeId = doorCasingTypeId
      request.doorCasing = selection.doorCasing
    }
    if (frameExtensionsTypeId !== undefined) {
      request.frameExtensionsTypeId = frameExtensionsTypeId
      request.frameExtensions = selection.frameExtensions
    }
    const hardwareSelections: HardwareSelectionDto[] = hardwareLines
      .filter((line): line is HardwareLine & { hardwareOptionId: number } => line.hardwareOptionId !== undefined)
      .map((line) => ({ hardwareOptionId: line.hardwareOptionId, quantity: line.quantity }))
    if (hardwareSelections.length > 0) {
      request.hardware = hardwareSelections
    }

    setExportLoading(true)
    setExportError(null)
    exportSpecification(request)
      .then(({ blob, filename }) => {
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = filename
        link.click()
        URL.revokeObjectURL(url)
      })
      .catch((error: unknown) => {
        setExportError(error instanceof Error ? error.message : 'Не удалось сформировать спецификацию')
      })
      .finally(() => {
        setExportLoading(false)
      })
  }

  function handleReverseChange(value: boolean) {
    setReverseSelection(value)
    setSelectedCollectionId(undefined)
    setCascadeSelection({})
    setSelection(emptySelection())
    setCustomLengthMode(false)
    setCustomHeightMode(false)
  }

  function handlePanelTypeChange(value: LeafPanelType) {
    setPanelTypeSelection(value)
    setMirrorFinishTypeId(undefined)
    setGlazingTypeId(undefined)
    setSelectedCollectionId(undefined)
    setCascadeSelection({})
    setSelection(emptySelection())
    setCustomLengthMode(false)
    setCustomHeightMode(false)
  }

  // Ничего не сбрасывает: несмотря на прежнюю формулировку в спецификации, выбор конкретного исполнения
  // зеркала фактически не сужает collectionOptions/panelTypeFilteredConfigurations (это отпало при переходе
  // на сужение по «Тип полотна» — см. change filter-by-leaf-panel-type) — сброс каскада был лишним и стирал
  // уже выбранную модель полотна при каждой смене исполнения (см. обратную связь, тот же баг, что и у вида
  // остекления). Выбранное исполнение по-прежнему уходит в запрос расчёта и влияет на наценку.
  function handleMirrorFinishTypeChange(id: number | undefined) {
    setMirrorFinishTypeId(id)
  }

  // Как и handleMirrorFinishTypeChange, ничего не сбрасывает: вид остекления не сужает каталог (см. change
  // add-glazing-catalog-for-v-models), поэтому выбор коллекции/модели/уже введённых опций должен
  // сохраняться. Выбранный вид остекления по-прежнему уходит в запрос расчёта и влияет на наценку
  // (см. change add-glazing-price-surcharge).
  function handleGlazingTypeChange(id: number | undefined) {
    setGlazingTypeId(id)
  }

  function handleCollectionChange(id: number | undefined) {
    setSelectedCollectionId(id)
    setCascadeSelection({})
    setSelection(emptySelection())
    setCustomLengthMode(false)
    setCustomHeightMode(false)
  }

  function handleCascadeStepChange(key: ComponentKey, id: number | undefined) {
    if (key === 'leaf') {
      setCustomLengthMode(false)
      setCustomHeightMode(false)
    }
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
    // Оба измерения полотна («Высота» и «Длина» — ширина) одинаково недостоверны для уже выбранных
    // кромки, короба, наличника и добора: изменение любого из них полностью сбрасывает выбор типа
    // (не только его зависимую от полотна высоту/длину) — по требованию пользователя (см. «Полный сброс
    // типа короба, наличника и добора при изменении измерения полотна»).
    const isLeafDimensionChange =
      key === 'leaf' &&
      ('heightOptionId' in patch ||
        'customHeightValueMm' in patch ||
        'lengthOptionId' in patch ||
        'customLengthValueMm' in patch)
    setSelection((prev) => {
      const next = { ...prev, [key]: { ...prev[key], ...patch } }
      if (isLeafDimensionChange) {
        next.edge = {}
        next.frame = {}
        next.doorCasing = {}
        next.frameExtensions = {}
      }
      return next
    })
    if (isLeafDimensionChange) {
      setCascadeSelection((prev) => {
        const { edge: _edge, frame: _frame, doorCasing: _doorCasing, frameExtensions: _frameExtensions, ...rest } = prev
        return rest
      })
    }
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

  function handleClearAll() {
    setReverseSelection(undefined)
    setPanelTypeSelection(undefined)
    setMirrorFinishTypeId(undefined)
    setGlazingTypeId(undefined)
    setSelectedCollectionId(undefined)
    setCascadeSelection({})
    setSelection(emptySelection())
    setCustomLengthMode(false)
    setCustomHeightMode(false)
    setHardwareLines([])
    setLeafPricingResult(null)
    setLeafPricingError(null)
    setFrameGroupPricingResult(null)
    setFrameGroupPricingError(null)
    setHardwarePricingResult(null)
    setHardwarePricingError(null)
  }

  // resolvedReverse (не selectedConfiguration?.reverse) — оно совпадает с ней, когда конфигурация
  // определена, но остаётся верным и до этого, пока действует расчёт отдельного полотна
  // (см. change add-standalone-leaf-pricing), которому тоже передаётся именно resolvedReverse.
  const surchargeBreakdown = computeSurchargeBreakdown(
    pricingSurcharges,
    selection.leaf,
    leafComponent,
    leafTypeId,
    mirrorFinishTypeId,
    glazingTypeId,
    resolvedReverse,
  )

  // Наценка за нестандартный погонаж показывается в общем блоке «Надбавки к цене за нестандарт» вместе
  // с надбавками к цене полотна, а не рядом с ценой конкретного компонента (короб/наличник/добор) —
  // одна строка на каждое встретившееся значение процента, без дублирования, если несколько компонентов
  // одновременно нестандартны с одинаковым процентом (см. change add-pogonazh-length-surcharge,
  // обновление отображения).
  const pogonazhPercents = [framePogonazhSurchargePercent, doorCasingPogonazhSurchargePercent, frameExtensionsPogonazhSurchargePercent]
  const uniquePogonazhPercents = [...new Set(pogonazhPercents.filter((percent): percent is number => percent !== undefined))]
  const combinedSurchargeBreakdown = [
    ...surchargeBreakdown,
    ...uniquePogonazhPercents.map((percent) => ({ label: 'Наценка за нестандартную длину погонажа', percent })),
  ]

  // Кромка — единственный компонент, у которого выбор типа и цвет показаны внутри одной карточки
  // (заголовок «Кромка», ряд «Тип» + «Цвет»), а не отдельной группой над карточкой, как у остальных
  // компонентов (см. change restyle-configurator-per-figma). Высота кромки не выбирается вручную —
  // подставляется автоматически из высоты полотна (см. useEffect с edgeComponent) и видна только
  // в бейдже заголовка карточки.
  function renderEdgeCard(step: CascadeStep) {
    const component = step.resolvedComponent
    return (
      <Card
        key={step.key}
        size="small"
        title={
          <Space align="center">
            <span>Кромка</span>
            {edgeMatchedValue !== undefined && leafHeightValue !== undefined && (
              <>
                <Tag>
                  Длина погонажа: <strong>{edgeMatchedValue}</strong> мм
                </Tag>
                <Tag>
                  для высоты полотна: <strong>{leafHeightValue}</strong> мм
                </Tag>
              </>
            )}
          </Space>
        }
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div style={{ display: 'flex', gap: 16 }}>
            <div style={{ flex: 2 }}>
              <OptionGroup
                label="Тип"
                options={[
                  ...step.availableTypes.map((type) => ({ id: type.id, label: displayName(type) })),
                  ...(step.hasNoneOption ? [{ id: NONE_OPTION_ID, label: NONE_OPTION_LABELS.edge }] : []),
                ]}
                selectedId={step.selectedId}
                onChange={(id) => handleCascadeStepChange('edge', id)}
              />
            </div>
            <div style={{ flex: 1 }}>
              <OptionGroup
                label="Цвет"
                options={(component?.colourOptions ?? []).map((option) => ({
                  id: option.id,
                  label: option.colourType.name,
                }))}
                selectedId={selection.edge.colourOptionId}
                onChange={(id) => updateSelection('edge', { colourOptionId: id })}
                variant="select"
              />
            </div>
          </div>
        </Space>
      </Card>
    )
  }

  // Короб — та же схема, что и кромка: заголовок «Короб» с тегами длины/высоты полотна, «Тип» внутри
  // карточки (не отдельной группой над ней), высота не выбирается вручную — подставляется автоматически
  // (см. useEffect с frameComponent). Положение самого блока «Короб» в разделе «Короб и обрамление»
  // не меняется (см. change restyle-configurator-per-figma).
  function renderFrameCard(step: CascadeStep) {
    const component = step.resolvedComponent
    return (
      <Card
        key={step.key}
        size="small"
        title={
          <Space align="center">
            <span>Короб</span>
            {frameMatchedValue !== undefined && leafHeightValue !== undefined && (
              <>
                <Tag>
                  Длина погонажа: <strong>{frameMatchedValue}</strong> мм
                </Tag>
                <Tag>
                  для высоты полотна: <strong>{leafHeightValue}</strong> мм
                </Tag>
              </>
            )}
          </Space>
        }
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <OptionGroup
            label="Тип"
            options={[
              ...step.availableTypes.map((type) => ({ id: type.id, label: displayName(type) })),
              ...(step.hasNoneOption ? [{ id: NONE_OPTION_ID, label: NONE_OPTION_LABELS.frame }] : []),
            ]}
            selectedId={step.selectedId}
            onChange={(id) => handleCascadeStepChange('frame', id)}
          />
          {component && component.posts.length > 0 && (
            <List
              size="small"
              header={<Typography.Text type="secondary">Состав короба</Typography.Text>}
              bordered
              dataSource={component.posts}
              renderItem={(post) => (
                <List.Item>
                  <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                    <span>
                      {post.postType.name} × {post.quantity}
                      {post.length !== null && post.postType.name !== FRAME_POST_NAME_WITHOUT_LENGTH
                        ? `, длина ${post.length}`
                        : ''}
                    </span>
                    <span>
                      {post.retailPrice} ₽ / {post.dealerPrice} ₽ (дилер)
                    </span>
                  </div>
                </List.Item>
              )}
            />
          )}
          {component && component.posts.length === 0 && component.colourOptions.length > 0 && (
            <List
              size="small"
              header={<Typography.Text type="secondary">Состав короба</Typography.Text>}
              bordered
              dataSource={[FRAME_KIT_WITHOUT_POSTS_DESCRIPTION]}
              renderItem={(item) => <List.Item>{item}</List.Item>}
            />
          )}
          {component && (
            <OptionGroup
              label="Толщина"
              options={component.dimensionOptions
                .filter((option) => option.dimensionType.code === THICKNESS_TYPE_CODE)
                .map((option) => ({ id: option.id, label: String(option.value) }))}
              selectedId={selection.frame.thicknessOptionId}
              onChange={(id) => updateSelection('frame', { thicknessOptionId: id })}
            />
          )}
          {component && (
            <OptionGroup
              label="Цвет"
              options={component.colourOptions.map((option) => ({ id: option.id, label: option.colourType.name }))}
              selectedId={selection.frame.colourOptionId}
              onChange={(id) => updateSelection('frame', { colourOptionId: id })}
            />
          )}
        </Space>
      </Card>
    )
  }

  // Наличник и добор — та же схема, что короб и кромка: заголовок с тегами длины/высоты полотна,
  // «Тип» внутри карточки, а рядом с ним, в одну строку — «Количество» (см. change
  // restyle-configurator-per-figma). Длина здесь не выбирается вручную — подставляется автоматически
  // (см. useEffect с doorCasingComponent/frameExtensionsComponent).
  function renderLengthLinkedCard(
    step: CascadeStep,
    title: string,
    matchedValue: number | undefined,
    key: 'doorCasing' | 'frameExtensions',
  ) {
    const component = step.resolvedComponent
    return (
      <Card
        key={step.key}
        size="small"
        title={
          <Space align="center">
            <span>{title}</span>
            {matchedValue !== undefined && leafHeightValue !== undefined && (
              <>
                <Tag>
                  Длина погонажа: <strong>{matchedValue}</strong> мм
                </Tag>
                <Tag>
                  для высоты полотна: <strong>{leafHeightValue}</strong> мм
                </Tag>
              </>
            )}
          </Space>
        }
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div style={{ display: 'flex', gap: 16 }}>
            {/* Фиксированная ширина «Количество» (как в блоке «Фурнитура») вместо доли ряда — не зависит
                от общей ширины карточки и не даёт подписи "Количество" переноситься на строку;
                «Тип» занимает всё оставшееся место. */}
            <div style={{ flex: 1 }}>
              <OptionGroup
                label="Тип"
                options={[
                  ...step.availableTypes.map((type) => ({ id: type.id, label: displayName(type) })),
                  ...(step.hasNoneOption ? [{ id: NONE_OPTION_ID, label: NONE_OPTION_LABELS[key] }] : []),
                ]}
                selectedId={step.selectedId}
                onChange={(id) => handleCascadeStepChange(key, id)}
              />
            </div>
            <div style={{ flex: '0 0 110px' }}>
              <div>
                <Typography.Text type="secondary">Количество</Typography.Text>
                <div style={{ marginTop: 4 }}>
                  <InputNumber
                    min={1}
                    style={{ width: '100%' }}
                    value={selection[key].quantity ?? 1}
                    onChange={(value) => updateSelection(key, { quantity: value ?? undefined })}
                  />
                </div>
              </div>
            </div>
          </div>
          {component && (
            <OptionGroup
              label="Толщина"
              options={component.dimensionOptions
                .filter((option) => option.dimensionType.code === THICKNESS_TYPE_CODE)
                .map((option) => ({ id: option.id, label: String(option.value) }))}
              selectedId={selection[key].thicknessOptionId}
              onChange={(id) => updateSelection(key, { thicknessOptionId: id })}
            />
          )}
          {component && (
            <OptionGroup
              label="Цвет"
              options={component.colourOptions.map((option) => ({ id: option.id, label: option.colourType.name }))}
              selectedId={selection[key].colourOptionId}
              onChange={(id) => updateSelection(key, { colourOptionId: id })}
            />
          )}
        </Space>
      </Card>
    )
  }

  function renderCascadeStep(step: CascadeStep) {
    if (step.key === 'edge') {
      return renderEdgeCard(step)
    }
    if (step.key === 'frame') {
      return renderFrameCard(step)
    }
    if (step.key === 'doorCasing') {
      return renderLengthLinkedCard(step, 'Наличник', doorCasingMatchedValue, 'doorCasing')
    }
    if (step.key === 'frameExtensions') {
      return renderLengthLinkedCard(step, 'Добор', frameExtensionsMatchedValue, 'frameExtensions')
    }
    // После вынесения кромки/короба/наличника/добора в отдельные функции здесь остаётся только
    // полотно — раскладка блока «Параметры выбранного полотна» не меняется (см. change
    // restyle-configurator-per-figma).
    const component = step.resolvedComponent
    return (
      <Fragment key={step.key}>
        <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
          {mirrorFinishStep.visible && (
            <div style={{ flex: 1 }}>
              <OptionGroup
                label={MIRROR_FINISH_LABEL}
                options={mirrorFinishStep.options.map((type) => ({ id: type.id, label: displayName(type) }))}
                selectedId={mirrorFinishTypeId}
                onChange={handleMirrorFinishTypeChange}
              />
            </div>
          )}
          {glazingStep.visible && (
            <div style={{ flex: 1 }}>
              <OptionGroup
                label={GLAZING_LABEL}
                options={glazingStep.options.map((type) => ({ id: type.id, label: displayName(type) }))}
                selectedId={glazingTypeId}
                onChange={handleGlazingTypeChange}
                variant="select"
              />
            </div>
          )}
          <div style={{ flex: 1 }}>
            <OptionGroup
              label={COMPONENT_LABELS.leaf}
              options={step.availableTypes.map((type) => ({ id: type.id, label: type.name }))}
              selectedId={step.selectedId}
              onChange={(id) => handleCascadeStepChange('leaf', id)}
              variant="select"
            />
          </div>
        </div>
        {component && (
          <Card size="small" title="Параметры выбранного полотна">
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <OptionGroup
                label="Длина (стандарт)"
                options={[
                  ...component.dimensionOptions
                    .filter((option) => option.dimensionType.code === LENGTH_TYPE_CODE)
                    .map((option) => ({ id: option.id, label: String(option.value) })),
                  { id: CUSTOM_LENGTH_OPTION_ID, label: CUSTOM_OPTION_LABEL },
                ]}
                selectedId={selection.leaf.lengthOptionId ?? (customLengthMode ? CUSTOM_LENGTH_OPTION_ID : undefined)}
                onChange={(id) => {
                  if (id === CUSTOM_LENGTH_OPTION_ID) {
                    setCustomLengthMode(true)
                    updateSelection('leaf', { lengthOptionId: undefined })
                  } else {
                    setCustomLengthMode(false)
                    updateSelection('leaf', { lengthOptionId: id, customLengthValueMm: undefined })
                  }
                }}
              />
              <div>
                <Space align="center">
                  <Typography.Text type="secondary">Нестандартное значение:</Typography.Text>
                  {dimensionRangeTag(component, LENGTH_TYPE_CODE)}
                </Space>
                <div style={{ marginTop: 4 }}>
                  <InputNumber
                    min={1}
                    step={50}
                    style={{ width: '100%' }}
                    disabled={!customLengthMode}
                    value={selection.leaf.customLengthValueMm}
                    onChange={(value) => updateSelection('leaf', { customLengthValueMm: value ?? undefined })}
                  />
                </div>
              </div>
              <OptionGroup
                label="Высота (стандарт)"
                options={[
                  ...component.dimensionOptions
                    .filter((option) => option.dimensionType.code === HEIGHT_TYPE_CODE)
                    .map((option) => ({ id: option.id, label: String(option.value) })),
                  { id: CUSTOM_HEIGHT_OPTION_ID, label: CUSTOM_OPTION_LABEL },
                ]}
                selectedId={selection.leaf.heightOptionId ?? (customHeightMode ? CUSTOM_HEIGHT_OPTION_ID : undefined)}
                onChange={(id) => {
                  if (id === CUSTOM_HEIGHT_OPTION_ID) {
                    setCustomHeightMode(true)
                    updateSelection('leaf', { heightOptionId: undefined })
                  } else {
                    setCustomHeightMode(false)
                    updateSelection('leaf', { heightOptionId: id, customHeightValueMm: undefined })
                  }
                }}
              />
              <div>
                <Space align="center">
                  <Typography.Text type="secondary">Нестандартное значение:</Typography.Text>
                  {dimensionRangeTag(component, HEIGHT_TYPE_CODE)}
                </Space>
                <div style={{ marginTop: 4 }}>
                  <InputNumber
                    min={1}
                    step={50}
                    style={{ width: '100%' }}
                    disabled={!customHeightMode}
                    value={selection.leaf.customHeightValueMm}
                    onChange={(value) => updateSelection('leaf', { customHeightValueMm: value ?? undefined })}
                  />
                </div>
              </div>
              <OptionGroup
                label="Толщина"
                options={component.dimensionOptions
                  .filter((option) => option.dimensionType.code === THICKNESS_TYPE_CODE)
                  .map((option) => ({ id: option.id, label: String(option.value) }))}
                selectedId={selection.leaf.thicknessOptionId}
                onChange={(id) => updateSelection('leaf', { thicknessOptionId: id })}
              />
              {component.colourOptions.length > 0 && (
                <div>
                  {selection.leaf.doubleSidedPainting ? (
                    <div style={{ display: 'flex', gap: 16 }}>
                      <div style={{ flex: 1 }}>
                        <OptionGroup
                          label="Цвет фронтальный"
                          options={component.colourOptions.map((option) => ({
                            id: option.id,
                            label: option.colourType.name,
                          }))}
                          selectedId={selection.leaf.colourOptionId}
                          onChange={(id) => updateSelection('leaf', { colourOptionId: id })}
                          variant="select"
                        />
                      </div>
                      <div style={{ flex: 1 }}>
                        <OptionGroup
                          label="Цвет задний"
                          options={component.colourOptions.map((option) => ({
                            id: option.id,
                            label: option.colourType.name,
                          }))}
                          selectedId={selection.leaf.backColourOptionId}
                          onChange={(id) => updateSelection('leaf', { backColourOptionId: id })}
                          variant="select"
                        />
                      </div>
                    </div>
                  ) : (
                    <OptionGroup
                      label="Цвет"
                      options={component.colourOptions.map((option) => ({
                        id: option.id,
                        label: option.colourType.name,
                      }))}
                      selectedId={selection.leaf.colourOptionId}
                      onChange={(id) => updateSelection('leaf', { colourOptionId: id })}
                      variant="select"
                    />
                  )}
                </div>
              )}
            </Space>
          </Card>
        )}
      </Fragment>
    )
  }

  const leafPanelContent = (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {(reverseStep?.visible || (leafComponent?.colourOptions.length ?? 0) > 0) && (
        <Space align="center" size="large">
          {reverseStep?.visible && (
            <Space align="center">
              <Typography.Text>Реверс</Typography.Text>
              <Switch checked={reverseStep.value} onChange={handleReverseChange} />
            </Space>
          )}
          {(leafComponent?.colourOptions.length ?? 0) > 0 && (
            <Space align="center">
              <Typography.Text>Двустороннее</Typography.Text>
              <Switch
                checked={Boolean(selection.leaf.doubleSidedPainting)}
                onChange={(checked) =>
                  updateSelection('leaf', {
                    doubleSidedPainting: checked,
                    backColourOptionId: checked ? selection.leaf.backColourOptionId : undefined,
                  })
                }
              />
            </Space>
          )}
        </Space>
      )}
      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
        {panelTypeStep?.visible && (
          <div style={{ flex: 1 }}>
            <OptionGroup
              label="Тип полотна"
              options={LEAF_PANEL_TYPE_OPTIONS.filter((option) => panelTypeStep.options.includes(option.code)).map(
                ({ id, label }) => ({ id, label }),
              )}
              selectedId={LEAF_PANEL_TYPE_OPTIONS.find((option) => option.code === panelTypeStep.value)?.id}
              onChange={(id) => {
                const option = LEAF_PANEL_TYPE_OPTIONS.find((candidate) => candidate.id === id)
                if (option) {
                  handlePanelTypeChange(option.code)
                }
              }}
            />
          </div>
        )}
        <div style={{ flex: 1 }}>
          <OptionGroup
            label={COLLECTION_LABEL}
            options={collectionOptions.map((type) => ({ id: type.id, label: type.name }))}
            selectedId={selectedCollectionId}
            onChange={handleCollectionChange}
            variant="select"
          />
        </div>
      </div>
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
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12 }}>
                <div style={{ flex: 1 }}>
                  <OptionGroup
                    label="Категория"
                    options={hardwareCatalog.map((category) => ({ id: category.category.id, label: category.category.name }))}
                    selectedId={line.categoryId}
                    onChange={(id) => updateHardwareLine(line.key, { categoryId: id, typeId: undefined, hardwareOptionId: undefined })}
                    variant="select"
                    truncateSelectedLabel
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <OptionGroup
                    label="Тип"
                    options={hardwareTypesFor(hardwareCatalog, line.categoryId).map((type) => ({
                      id: type.type.id,
                      label: type.type.name,
                    }))}
                    selectedId={line.typeId}
                    onChange={(id) => updateHardwareLine(line.key, { typeId: id, hardwareOptionId: undefined })}
                    variant="select"
                    truncateSelectedLabel
                  />
                </div>
                <div style={{ flex: 1 }}>
                  <OptionGroup
                    label="Цвет"
                    // Один и тот же цветовой вариант (hardwareOptionId) нельзя выбрать в двух позициях
                    // одновременно — исключаем варианты, уже занятые другими позициями, из списка этой
                    // (см. обратную связь по дублированию фурнитуры). Собственный текущий выбор строки
                    // не исключается, чтобы не пропадал из её же списка.
                    options={hardwareOptionsFor(hardwareCatalog, line.categoryId, line.typeId)
                      .filter(
                        (option) =>
                          option.id === line.hardwareOptionId ||
                          !hardwareLines.some((other) => other.key !== line.key && other.hardwareOptionId === option.id),
                      )
                      .map((option) => ({
                        id: option.id,
                        label: option.colourName,
                      }))}
                    selectedId={line.hardwareOptionId}
                    onChange={(id) => updateHardwareLine(line.key, { hardwareOptionId: id })}
                    variant="select"
                    truncateSelectedLabel
                  />
                </div>
                <div style={{ flex: '0 0 110px' }}>
                  <Typography.Text type="secondary">Количество</Typography.Text>
                  <div style={{ marginTop: 4 }}>
                    <InputNumber
                      min={1}
                      style={{ width: '100%' }}
                      value={line.quantity ?? 1}
                      onChange={(value) => updateHardwareLine(line.key, { quantity: value ?? undefined })}
                    />
                  </div>
                </div>
                <Button
                  danger
                  icon={<DeleteOutlined />}
                  aria-label="Удалить"
                  onClick={() => removeHardwareLine(line.key)}
                />
              </div>
            </Card>
          ))}
          <Button onClick={addHardwareLine}>Добавить позицию фурнитуры</Button>
        </Space>
      )}
    </>
  )


  return (
    <div className="page">
      <div className="app-header">
        <Space align="center" size={12}>
          <div className="app-header__logo">
            <HomeOutlined />
          </div>
          <Typography.Title level={5} style={{ margin: 0 }}>
            {APP_TITLE}
          </Typography.Title>
        </Space>
        <Typography.Text strong>{PRICE_LIST_LABEL}</Typography.Text>
      </div>

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
              accordion
              defaultActiveKey={[]}
              expandIconPosition="start"
              items={[
                { key: 'leaf', label: 'Полотно', children: leafPanelContent },
                { key: 'frameGroup', label: 'Короб и обрамление', children: frameGroupPanelContent },
                { key: 'hardware', label: 'Фурнитура', children: hardwarePanelContent },
              ]}
            />
          )}
        </div>

        <div className="app-pricing">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <Typography.Title level={4} style={{ margin: 0, fontSize: 16, whiteSpace: 'nowrap' }}>
              Расчёт стоимости
            </Typography.Title>
            <Button onClick={handleClearAll}>Очистить</Button>
          </div>
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
            <div>
              {pricingError && <Alert type="error" message={pricingError} showIcon />}
              {!pricingError && pricingResult && (
                <>
                  <Space direction="vertical" size="small" style={{ width: '100%' }}>
                    <Statistic title="Розничная цена" value={pricingResult.totalRetailPrice} suffix="₽" />
                    <div className="app-pricing__dealer-block">
                      <Statistic title="Дилерская цена" value={pricingResult.totalDealerPrice} suffix="₽" />
                    </div>
                  </Space>
                  {combinedSurchargeBreakdown.length > 0 && (
                    <List
                      style={{ marginTop: 16 }}
                      size="small"
                      header={<Typography.Text type="secondary">Надбавки к цене за нестандарт</Typography.Text>}
                      bordered
                      dataSource={combinedSurchargeBreakdown}
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
                  {exportError && <Alert style={{ marginTop: 16 }} type="error" message={exportError} showIcon />}
                  <Button
                    type="primary"
                    block
                    style={{ marginTop: 16 }}
                    loading={exportLoading}
                    onClick={handleExportSpecification}
                  >
                    Скачать excel спецификацию
                  </Button>
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
