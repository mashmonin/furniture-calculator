import { Fragment, useEffect, useRef, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Collapse,
  Empty,
  InputNumber,
  List,
  Space,
  Spin,
  Statistic,
  Switch,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import {
  calculateFrameGroupPrice,
  calculateHardwarePrice,
  calculateLeafPrice,
  fetchDoorConfigurations,
  fetchHardwareCatalog,
  fetchPricingSurcharges,
} from './api/doorConfigurations'
import type {
  ComponentCatalogDto,
  ComponentKey,
  ComponentPriceDto,
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
} from './api/types'
import { OptionGroup } from './components/OptionGroup'
import { ComponentBreakdownList, type SurchargeBreakdownItem } from './ComponentBreakdownList'
import type { CartDetailRow, CartItem, CartItemContent } from './cart'
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
// Толщина, доступная только с исполнением «с четвертью» (см. change add-leaf-quarter-attribute) — то же
// значение, что и backend-константа THICKNESS_REQUIRING_QUARTER_MM в DoorConfigurationPricingService.
const THICKNESS_REQUIRING_QUARTER_MM = 59
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

interface HasQuarterStep {
  visible: boolean
  value: boolean
}

// «Четверть» — независимый от «Реверс» переключатель того же уровня (см. change add-leaf-quarter-attribute):
// сужает каталог тем же принципом, что и resolveReverseStep выше, но по атрибуту has_quarter, а не
// is_reverse — оба атрибута независимы друг от друга (см. specs, door-configuration-catalog).
function resolveHasQuarterStep(
  configurations: DoorConfigurationDto[],
  hasQuarterSelection: boolean | undefined,
): { hasQuarterStep?: HasQuarterStep; resolvedHasQuarter: boolean } {
  const hasQuarterValues = new Set(configurations.map((configuration) => configuration.hasQuarter))
  if (hasQuarterValues.size > 1) {
    const resolvedHasQuarter = hasQuarterSelection ?? false
    return { hasQuarterStep: { visible: true, value: resolvedHasQuarter }, resolvedHasQuarter }
  }
  return { resolvedHasQuarter: configurations.length > 0 ? configurations[0].hasQuarter : false }
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
  hasQuarter: boolean,
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
  // «Четверть» — только когда она истинна САМА ПО СЕБЕ, а не как следствие уже показанной наценки за
  // реверс или за толщину 59мм (см. change add-leaf-quarter-attribute, тот же принцип, что и на backend в
  // resolveQuarterSurchargeMultiplier) — иначе клиент увидел бы задвоенную наценку за одно и то же.
  if (hasQuarter && !isReverse && !thicknessRule) {
    items.push({ label: 'За исполнение с четвертью', percent: pricingSurcharges.quarterSurchargePercent })
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

// Запрос на открытие ранее добавленной в корзину конфигурации обратно в конфигураторе (см. change
// add-order-cart-screen, «Кнопка «Посмотреть» открывает конфигурацию в конфигураторе») — requestedAt нужен,
// чтобы useEffect срабатывал заново, даже если пользователь открывает ту же самую позицию второй раз подряд
// (иначе объект request с теми же значениями не считался бы новым для React).
export interface ConfiguratorLoadRequest {
  request: SpecificationExportRequestDto
  requestedAt: number
}

interface ConfiguratorScreenProps {
  // sourceRect — координаты кнопки «Добавить в корзину» на момент клика (см. ниже, buttonRef), нужны App.tsx
  // для визуального эффекта «полёта» добавленной конфигурации к пункту «Корзина заказа» в левом меню; null,
  // если координаты почему-то недоступны (эффект тогда просто не показывается, сама конфигурация всё равно
  // добавляется).
  onAddToCart: (item: CartItem, sourceRect: DOMRect | null) => void
  cartSaveError: boolean
  loadRequest: ConfiguratorLoadRequest | null
  // editingItemId — id позиции корзины, открытой через «Посмотреть» (см. order-cart-ui, «Живая
  // синхронизация конфигурации, открытой из корзины»); null — обычный режим «добавить новую». Пока задан,
  // любое изменение конфигурации сразу пишется в эту же позицию через onSyncEditedItem, а кнопка «Добавить
  // в корзину» недоступна (см. handleAddToCart). onStopEditing вызывается из handleClearAll — сбрасывает
  // editingItemId в App.tsx, возвращая обычный режим.
  editingItemId: string | null
  onSyncEditedItem: (id: string, content: CartItemContent) => void
  onStopEditing: () => void
}

function ConfiguratorScreen({
  onAddToCart,
  cartSaveError,
  loadRequest,
  editingItemId,
  onSyncEditedItem,
  onStopEditing,
}: ConfiguratorScreenProps) {
  // Ref на кнопку «Добавить в корзину» — только чтобы прочитать её координаты в момент клика
  // (getBoundingClientRect) для визуального эффекта «полёта» в App.tsx; на саму логику добавления не влияет.
  const addToCartButtonRef = useRef<HTMLButtonElement>(null)
  // См. useEffect реконструкции загруженной из корзины позиции ниже и useEffect «Живая синхронизация» —
  // защита от записи ещё не обновлённого (старого) содержимого в открываемую позицию на переходном рендере.
  const skipNextSyncRef = useRef(false)
  const [configurations, setConfigurations] = useState<DoorConfigurationDto[]>([])
  const [catalogLoading, setCatalogLoading] = useState(true)
  const [catalogError, setCatalogError] = useState<string | null>(null)

  const [pricingSurcharges, setPricingSurcharges] = useState<PricingSurchargesDto | null>(null)

  const [hardwareCatalog, setHardwareCatalog] = useState<HardwareCategoryDto[]>([])
  const [hardwareCatalogLoading, setHardwareCatalogLoading] = useState(true)
  const [hardwareCatalogError, setHardwareCatalogError] = useState<string | null>(null)
  const [hardwareLines, setHardwareLines] = useState<HardwareLine[]>([])
  const [nextHardwareLineKey, setNextHardwareLineKey] = useState(1)

  const [reverseSelection, setReverseSelection] = useState<boolean | undefined>(undefined)
  const [hasQuarterSelection, setHasQuarterSelection] = useState<boolean | undefined>(undefined)
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

  // Открытие ранее добавленной в корзину позиции обратно в конфигураторе (см. change add-order-cart-screen,
  // «Кнопка «Посмотреть» открывает конфигурацию в конфигураторе») — заполнение того же состояния каскада
  // (cascadeSelection/selection и псевдо-шагов), которое обычно накапливается кликами пользователя; дальше
  // существующая каскадная логика отображает и пересчитывает всё как обычно. Реконструируется из
  // exportRequest, сохранённого в позиции корзины (то же тело, что уходит в POST /api/specification/export),
  // без нового обращения к backend — с использованием уже загруженного каталога (configurations/
  // hardwareCatalog). Пока editingItemId задан (см. проп), любое дальнейшее изменение конфигурации живьём
  // синхронизируется обратно в эту же позицию корзины (см. useEffect «Живая синхронизация» ниже) — поэтому
  // здесь же взводится skipNextSyncRef.current = true: без этой защиты синхронизация могла бы в этом же
  // рендере (до того, как setCascadeSelection/setSelection ниже применятся) записать в открываемую позицию
  // ещё СТАРЫЙ pricingResult — если в конфигураторе на момент клика «Посмотреть» уже был другой, посчитанный,
  // но не добавленный в корзину выбор.
  useEffect(() => {
    if (!loadRequest || configurations.length === 0) {
      return
    }
    const { request } = loadRequest
    const leafCatalogEntry = configurations.find((c) => c.leaf.type.id === request.leafTypeId)?.leaf
    if (!leafCatalogEntry) {
      return
    }
    skipNextSyncRef.current = true

    setReverseSelection(request.isReverse ?? false)
    const edgeCode =
      request.edgeTypeId !== undefined
        ? configurations.find((c) => c.edge?.type.id === request.edgeTypeId)?.edge?.type.code
        : undefined
    setHasQuarterSelection(edgeCode === 'ET-002' ? true : edgeCode === 'ET-001' ? false : (request.isReverse ?? undefined))
    setPanelTypeSelection(leafCatalogEntry.panelType ?? undefined)
    setSelectedCollectionId(leafCatalogEntry.collection?.id)

    const nextCascadeSelection: Partial<Record<ComponentKey, number>> = { leaf: request.leafTypeId }
    if (request.edgeTypeId !== undefined) {
      nextCascadeSelection.edge = request.edgeTypeId
    }
    if (request.frameTypeId !== undefined) {
      nextCascadeSelection.frame = request.frameTypeId
    }
    if (request.doorCasingTypeId !== undefined) {
      nextCascadeSelection.doorCasing = request.doorCasingTypeId
    }
    if (request.frameExtensionsTypeId !== undefined) {
      nextCascadeSelection.frameExtensions = request.frameExtensionsTypeId
    }
    setCascadeSelection(nextCascadeSelection)

    setSelection({
      leaf: request.leaf ?? {},
      edge: request.edge ?? {},
      frame: request.frame ?? {},
      doorCasing: request.doorCasing ?? {},
      frameExtensions: request.frameExtensions ?? {},
    })
    setMirrorFinishTypeId(request.leaf?.mirrorFinishTypeId)
    setGlazingTypeId(request.leaf?.glazingTypeId)
    setCustomLengthMode(request.leaf?.customLengthValueMm !== undefined)
    setCustomHeightMode(request.leaf?.customHeightValueMm !== undefined)

    // Обратный поиск categoryId/typeId по hardwareOptionId — в запросе хранится только id варианта, каскад
    // выбора фурнитуры (категория → тип → цвет) восстанавливается поиском по уже загруженному каталогу.
    const lines: HardwareLine[] = (request.hardware ?? []).map((item, index) => {
      for (const category of hardwareCatalog) {
        for (const type of category.types) {
          if (type.options.some((option) => option.id === item.hardwareOptionId)) {
            return {
              key: index + 1,
              categoryId: category.category.id,
              typeId: type.type.id,
              hardwareOptionId: item.hardwareOptionId,
              quantity: item.quantity,
            }
          }
        }
      }
      return { key: index + 1, hardwareOptionId: item.hardwareOptionId, quantity: item.quantity }
    })
    setHardwareLines(lines)
    setNextHardwareLineKey(lines.length + 1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadRequest])

  // «Реверс» и «Четверть» — независимые переключатели одного уровня, оба оцениваются по ПОЛНОМУ
  // (нефильтрованному) каталогу — так же, как «Реверс» всегда оценивался сам по себе. Раньше «Четверть»
  // оценивалась по уже суженному reverseFilteredConfigurations, из-за чего при is_reverse=true (где
  // has_quarter всегда true) переключатель пропадал — так как выбора внутри уже суженного набора не
  // оставалось. По обратной связи это исправлено: «Четверть» видна и активна всегда, когда в каталоге
  // в принципе есть оба значения, независимо от текущего положения «Реверс»; согласованность двух
  // переключателей (is_reverse=true возможен только с has_quarter=true) обеспечивается в
  // handleReverseChange/handleHasQuarterChange — переключение одного при конфликте автоматически
  // подстраивает другой, а не прячет/блокирует виджет.
  const { reverseStep, resolvedReverse } = resolveReverseStep(configurations, reverseSelection)
  const { hasQuarterStep, resolvedHasQuarter } = resolveHasQuarterStep(configurations, hasQuarterSelection)
  const hasQuarterFilteredConfigurations = configurations.filter(
    (configuration) => configuration.reverse === resolvedReverse && configuration.hasQuarter === resolvedHasQuarter,
  )

  const { panelTypeStep, resolvedPanelType } = resolvePanelTypeStep(hasQuarterFilteredConfigurations, panelTypeSelection)
  // «Глухое» — не отдельная непересекающаяся категория моделей, а базовое исполнение, доступное любому
  // полотну (в том числе тем, что дополнительно поддерживают зеркало/остекление) — поэтому оно не сужает
  // каталог, в отличие от «Зеркальное»/«С остеклением», которые сужают точным совпадением panelType до
  // моделей, дополнительно это поддерживающих (см. обратную связь после первой реализации, design.md).
  const panelTypeFilteredConfigurations =
    resolvedPanelType === 'BLIND'
      ? hasQuarterFilteredConfigurations
      : hasQuarterFilteredConfigurations.filter((configuration) => configuration.leaf.panelType === resolvedPanelType)
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

  // Кнопка «Добавить в корзину» (см. change add-order-cart-screen) — собирает SpecificationExportRequestDto
  // из уже имеющегося состояния (тем же кодом, что раньше уходил в скачивание файла напрямую) и вместе с уже
  // посчитанным pricingResult формирует новую позицию корзины через onAddToCart — без обращения к backend
  // (корзина хранится только в localStorage, см. cart.ts). Повторное нажатие с той же конфигурацией создаёт
  // отдельную новую позицию (id всегда новый) — количество существующей не увеличивается автоматически.
  // Собирает содержимое позиции корзины (всё, кроме id/addedAt/quantity — это специфика самой позиции, а не
  // текущего состояния конфигуратора) из уже имеющегося состояния — используется и для «Добавить в корзину»
  // (см. handleAddToCart), и для живой синхронизации при редактировании уже открытой из корзины позиции
  // (см. useEffect ниже, «Живая синхронизация...») — не дублируем логику построения exportRequest/
  // detailRows дважды. Возвращает null, если конфигурация ещё не готова (нет цены/типа полотна).
  function buildCartItemContent(): {
    displayName: string
    dimensionsLabel: string
    exportRequest: SpecificationExportRequestDto
    pricingSnapshot: PricingResponseDto
    detailRows: CartDetailRow[]
  } | null {
    if (leafTypeId === undefined || !pricingResult || !leafComponent) {
      return null
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

    const leafLengthValue =
      selection.leaf.customLengthValueMm ??
      leafComponent.dimensionOptions.find((option) => option.id === selection.leaf.lengthOptionId)?.value
    const leafThicknessValue = leafComponent.dimensionOptions.find(
      (option) => option.id === selection.leaf.thicknessOptionId,
    )?.value
    const dimensionsLabel = [leafLengthValue, leafHeightValue, leafThicknessValue]
      .filter((value): value is number => value !== undefined)
      .join(' × ')

    // Детализация по компонентам для раскрываемой таблицы корзины (см. order-cart-ui, «Разворачиваемая
    // детализация позиции корзины») — строится здесь же, на фронте, из тех же catalog/selection данных, что
    // уже использует сам конфигуратор (см. cart.ts, CartDetailRow), а не отдельным backend-эндпоинтом:
    // itemPriceFor/priceByComponent — цены уже посчитаны в pricingResult, соответствие «Полотно»→100%
    // размеров/цвета — из leafComponent/selection.leaf, аналогично для остальных компонентов. dealerSum/
    // retailSum здесь равны price.dealerPrice/retailPrice как есть (backend уже умножает их на quantity для
    // doorCasing/frameExtensions/фурнитуры, см. DoorConfigurationPricingService.componentPriceFrom) — цена
    // «за единицу» получается делением обратно на quantity.
    const priceByComponent = (component: string) => pricingResult.components.find((item) => item.component === component)
    function detailRow(
      element: string,
      name: string,
      size: string | null,
      colour: string | null,
      quantity: number,
      price: ComponentPriceDto | undefined,
    ): CartDetailRow {
      const retailTotal = price?.priced ? price.retailPrice : null
      const dealerTotal = price?.priced ? price.dealerPrice : null
      return {
        element,
        name,
        size,
        colour,
        quantity,
        retailPrice: retailTotal !== null ? retailTotal / quantity : null,
        dealerPrice: dealerTotal !== null ? dealerTotal / quantity : null,
        retailSum: retailTotal,
        dealerSum: dealerTotal,
      }
    }
    function colourOf(component: ComponentCatalogDto, colourOptionId: number | undefined): string | undefined {
      return colourOptionId !== undefined
        ? component.colourOptions.find((option) => option.id === colourOptionId)?.colourType.name
        : undefined
    }

    const detailRows: CartDetailRow[] = []
    const leafFrontColour = colourOf(leafComponent, selection.leaf.colourOptionId)
    const leafBackColour = colourOf(leafComponent, selection.leaf.backColourOptionId)
    const leafColour =
      leafFrontColour && leafBackColour ? `${leafFrontColour} / ${leafBackColour}` : (leafFrontColour ?? leafBackColour ?? null)
    detailRows.push(
      detailRow('Полотно', leafComponent.type.name, dimensionsLabel || null, leafColour, 1, priceByComponent('leaf')),
    )
    if (edgeComponent) {
      detailRows.push(detailRow('Кромка', edgeComponent.type.name, null, null, 1, priceByComponent('edge')))
    }
    if (frameComponent) {
      const frameSize = frameMatchedValue !== undefined ? `${frameMatchedValue}` : null
      detailRows.push(detailRow('Короб', frameComponent.type.name, frameSize, null, 1, priceByComponent('frame')))
      frameComponent.posts.forEach((post) => {
        detailRows.push({
          element: 'Короб',
          name: post.postType.name,
          size: post.length !== null ? `${post.length}` : null,
          colour: null,
          quantity: post.quantity,
          dealerPrice: post.dealerPrice,
          retailPrice: post.retailPrice,
          dealerSum: post.dealerPrice * post.quantity,
          retailSum: post.retailPrice * post.quantity,
        })
      })
    }
    if (doorCasingComponent) {
      const quantity = selection.doorCasing.quantity ?? 1
      const size = doorCasingMatchedValue !== undefined ? `${doorCasingMatchedValue}` : null
      const colour = colourOf(doorCasingComponent, selection.doorCasing.colourOptionId) ?? null
      detailRows.push(
        detailRow('Наличник', doorCasingComponent.type.name, size, colour, quantity, priceByComponent('doorCasing')),
      )
    }
    if (frameExtensionsComponent) {
      const quantity = selection.frameExtensions.quantity ?? 1
      const size = frameExtensionsMatchedValue !== undefined ? `${frameExtensionsMatchedValue}` : null
      const colour = colourOf(frameExtensionsComponent, selection.frameExtensions.colourOptionId) ?? null
      detailRows.push(
        detailRow('Добор', frameExtensionsComponent.type.name, size, colour, quantity, priceByComponent('frameExtensions')),
      )
    }
    pricingResult.hardware.forEach((item) => {
      detailRows.push({
        element: 'Фурнитура',
        name: `${item.category.name} — ${item.type.name}`,
        size: null,
        colour: item.colourName,
        quantity: item.quantity,
        dealerPrice: item.dealerPrice / item.quantity,
        retailPrice: item.retailPrice / item.quantity,
        dealerSum: item.dealerPrice,
        retailSum: item.retailPrice,
      })
    })

    return {
      displayName: leafComponent.type.name,
      dimensionsLabel,
      exportRequest: request,
      pricingSnapshot: pricingResult,
      detailRows,
    }
  }

  // Кнопка «Добавить в корзину» недоступна, пока открыта позиция из корзины (editingItemId задан, см. проп
  // и «Живая синхронизация» ниже) — в этом режиме изменения и так сразу сохраняются в ту же позицию, у
  // добавления новой позиции здесь нет смысла (см. правку пользователя).
  function handleAddToCart() {
    if (editingItemId) {
      return
    }
    const content = buildCartItemContent()
    if (!content) {
      return
    }
    onAddToCart(
      { id: crypto.randomUUID(), addedAt: new Date().toISOString(), quantity: 1, ...content },
      addToCartButtonRef.current?.getBoundingClientRect() ?? null,
    )
    // После добавления в корзину форма конфигуратора освобождается — тем же сбросом, что и по кнопке
    // «Очистить» (см. правку пользователя) — координаты кнопки для эффекта «полёта» уже прочитаны строкой
    // выше, до сброса, поэтому порядок вызовов здесь важен.
    handleClearAll()
  }

  // Живая синхронизация открытой из корзины позиции (см. правку пользователя: «если пользователь открыл
  // конфигурацию из корзины и что-то в ней изменил, изменения автоматически отразились в корзине»). Ключ —
  // зависимости эффекта: это исходные useState-значения (стабильны по ссылке между рендерами, где не
  // менялись), а не производный pricingResult (новый объект на каждый рендер) — иначе эффект срабатывал бы
  // на каждый рендер и мог зациклиться через onSyncEditedItem -> обновление cart в App -> ре-рендер. Как
  // только editingItemId сброшен (клик «Очистить», см. handleClearAll/onStopEditing), синхронизация
  // прекращается сама собой (guard в начале).
  useEffect(() => {
    if (!editingItemId) {
      return
    }
    // Пропускаем ровно один переходный рендер сразу после открытия позиции (см. skipNextSyncRef,
    // useEffect реконструкции выше) — на нём pricingResult/detailRows ещё соответствуют предыдущему
    // (возможно, чужому) состоянию конфигуратора, а не только что загруженной позиции.
    if (skipNextSyncRef.current) {
      skipNextSyncRef.current = false
      return
    }
    const content = buildCartItemContent()
    if (content) {
      onSyncEditedItem(editingItemId, content)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    editingItemId,
    leafPricingResult,
    frameGroupPricingResult,
    hardwarePricingResult,
    selection,
    cascadeSelection,
    resolvedReverse,
    resolvedHasQuarter,
    mirrorFinishTypeId,
    glazingTypeId,
    hardwareLines,
  ])

  // Реверс возможен только вместе с четвертью (is_reverse=true всегда подразумевает has_quarter=true,
  // см. specs, door-configuration-catalog) — включение реверса принудительно включает и «Четверть», чтобы
  // пара переключателей никогда не оказывалась в несуществующей комбинации (is_reverse=true,
  // has_quarter=false). Выключение реверса не трогает «Четверть» — is_reverse=false совместимо с любым
  // её значением.
  function handleReverseChange(value: boolean) {
    setReverseSelection(value)
    if (value) {
      setHasQuarterSelection(true)
    }
    setSelectedCollectionId(undefined)
    setCascadeSelection({})
    setSelection(emptySelection())
    setCustomLengthMode(false)
    setCustomHeightMode(false)
  }

  // Симметрично handleReverseChange: выключение «Четверть» при включённом «Реверс» принудительно
  // выключает и реверс — той же причине (несуществующая комбинация is_reverse=true, has_quarter=false).
  // Включение «Четверть» не трогает «Реверс» — обе его позиции совместимы с has_quarter=true.
  function handleHasQuarterChange(value: boolean) {
    setHasQuarterSelection(value)
    if (!value && resolvedReverse) {
      setReverseSelection(false)
    }
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
    // Сброс формы всегда выходит из режима «редактирование позиции из корзины» (если он был активен) — см.
    // editingItemId/onStopEditing выше; иначе после «Очистить» живая синхронизация продолжала бы молча
    // писать пустую/новую конфигурацию в уже открытую позицию корзины.
    onStopEditing()
    setReverseSelection(undefined)
    setHasQuarterSelection(undefined)
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
    resolvedHasQuarter,
  )

  // Наценка за нестандартный погонаж показывается под ценой своего конкретного компонента (короб/
  // наличник/добор), а не в общем блоке (см. change show-surcharges-under-component-price) — поэтому
  // здесь просто сопоставляем ключ компонента с его собственным процентом, без объединения в общий
  // список и без дедупликации одинаковых процентов между разными компонентами.
  const pogonazhSurchargePercentByComponent: Partial<Record<ComponentKey, number>> = {
    frame: framePogonazhSurchargePercent,
    doorCasing: doorCasingPogonazhSurchargePercent,
    frameExtensions: frameExtensionsPogonazhSurchargePercent,
  }

  // Та же карта, но в форме, которую принимает общий ComponentBreakdownList (surchargesByComponent) —
  // один элемент списка на компонент, у которого наценка за погонаж применилась.
  const pogonazhSurchargesByComponentForBreakdown: Partial<Record<ComponentKey, SurchargeBreakdownItem[]>> =
    Object.fromEntries(
      (Object.entries(pogonazhSurchargePercentByComponent) as [ComponentKey, number | undefined][])
        .filter(([, percent]) => percent !== undefined)
        .map(([key, percent]) => [key, [{ label: 'Наценка за нестандартную длину погонажа', percent: percent! }]]),
    )

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
                // Толщина 59мм физически изготавливается только с четвертью (см. change
                // add-leaf-quarter-attribute) — скрыта, когда «Четверть» точно выключена; выбор её сам
                // включает «Четверть» автоматически, тем же принципом, что и выбор реверса, но без сброса
                // остальных уже сделанных шагов (это не отдельный шаг каскада, а следствие толщины).
                options={component.dimensionOptions
                  .filter((option) => option.dimensionType.code === THICKNESS_TYPE_CODE)
                  .filter((option) => option.value !== THICKNESS_REQUIRING_QUARTER_MM || resolvedHasQuarter !== false)
                  .map((option) => ({ id: option.id, label: String(option.value) }))}
                selectedId={selection.leaf.thicknessOptionId}
                onChange={(id) => {
                  const chosen = component.dimensionOptions.find(
                    (option) => option.dimensionType.code === THICKNESS_TYPE_CODE && option.id === id,
                  )
                  if (chosen?.value === THICKNESS_REQUIRING_QUARTER_MM && resolvedHasQuarter !== true) {
                    setHasQuarterSelection(true)
                  }
                  updateSelection('leaf', { thicknessOptionId: id })
                }}
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
      {(reverseStep?.visible || hasQuarterStep?.visible || (leafComponent?.colourOptions.length ?? 0) > 0) && (
        <Space align="center" size="large">
          {reverseStep?.visible && (
            <Space align="center">
              <Typography.Text>Реверс</Typography.Text>
              <Switch checked={reverseStep.value} onChange={handleReverseChange} />
            </Space>
          )}
          {hasQuarterStep?.visible && (
            // Четверть — переключатель того же уровня, что и «Реверс» (см. change add-leaf-quarter-attribute):
            // самостоятельно сужает каталог по has_quarter, до выбора коллекции/полотна. Кромка для уже
            // суженного набора автоматически предлагает только вариант, соответствующий текущему значению
            // (см. specs, door-configuration-catalog, «Соответствие вида кромки и атрибута «Четверть»»).
            <Space align="center">
              <Typography.Text>Четверть</Typography.Text>
              <Switch checked={hasQuarterStep.value} onChange={handleHasQuarterChange} />
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
              selectedId={
                panelTypeSelection !== undefined && panelTypeStep.options.includes(panelTypeSelection)
                  ? LEAF_PANEL_TYPE_OPTIONS.find((option) => option.code === panelTypeSelection)?.id
                  : undefined
              }
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
    <>
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
          {/* Вынесено из-под leafTypeId/pricingResult намеренно: после успешного «Добавить в корзину» форма
              сразу сбрасывается (см. handleAddToCart, вызов handleClearAll в конце) — если бы алерт лежал
              внутри блока результата, он исчезал бы вместе со сбросом в том же рендере, не успев показаться
              пользователю, даже при ошибке сохранения в localStorage. */}
          {cartSaveError && (
            <Alert
              style={{ marginBottom: 16 }}
              type="error"
              message="Не удалось добавить конфигурацию в корзину"
              showIcon
            />
          )}
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
                  <ComponentBreakdownList
                    components={pricingResult.components}
                    hardware={pricingResult.hardware}
                    quantities={{
                      doorCasing: selection.doorCasing.quantity ?? 1,
                      frameExtensions: selection.frameExtensions.quantity ?? 1,
                    }}
                    surchargesByComponent={{
                      leaf: surchargeBreakdown,
                      ...pogonazhSurchargesByComponentForBreakdown,
                    }}
                  />
                  {editingItemId && (
                    <Typography.Text type="secondary" style={{ display: 'block', marginTop: 16 }}>
                      Вы открыли позицию из корзины — изменения сохраняются в неё автоматически
                    </Typography.Text>
                  )}
                  <Tooltip
                    title={
                      editingItemId
                        ? 'Изменения уже сохраняются в открытую позицию корзины — нажмите «Очистить», чтобы начать новую'
                        : undefined
                    }
                  >
                    <Button
                      ref={addToCartButtonRef}
                      type="primary"
                      block
                      disabled={Boolean(editingItemId)}
                      style={{ marginTop: editingItemId ? 8 : 16 }}
                      onClick={handleAddToCart}
                    >
                      Добавить в корзину
                    </Button>
                  </Tooltip>
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
    </>
  )
}

export default ConfiguratorScreen
