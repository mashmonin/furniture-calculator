export interface ReferenceDto {
  id: number
  code: string
  name: string
  shortName: string | null
}

export interface LinerDimensionOptionDto {
  id: number
  dimensionType: ReferenceDto
  value: number
  minValue: number | null
  maxValue: number | null
  standard: boolean
}

export interface ColourOptionDto {
  id: number
  colourType: ReferenceDto
}

export interface FramePostDto {
  id: number
  postType: ReferenceDto
  quantity: number
  length: number | null
  retailPrice: number
  dealerPrice: number
}

export type LeafPanelType = 'BLIND' | 'GLAZED' | 'MIRRORED'

export interface DimensionRangeDto {
  dimensionType: ReferenceDto
  minValue: number
  maxValue: number
}

export interface ComponentCatalogDto {
  type: ReferenceDto
  collection: ReferenceDto | null
  dimensionOptions: LinerDimensionOptionDto[]
  colourOptions: ColourOptionDto[]
  posts: FramePostDto[]
  mirrorFinishOptions: ReferenceDto[]
  panelType: LeafPanelType | null
  glazingOptions: ReferenceDto[]
  dimensionRanges: DimensionRangeDto[]
}

export interface DoorConfigurationDto {
  id: number
  leaf: ComponentCatalogDto
  frame: ComponentCatalogDto | null
  edge: ComponentCatalogDto | null
  doorCasing: ComponentCatalogDto | null
  frameExtensions: ComponentCatalogDto | null
  reverse: boolean
  // Четверть по периметру полотна — самостоятельная опция, независимая от reverse (см. change
  // add-leaf-quarter-attribute). До этого change кромка жёстко соответствовала reverse; теперь — этому полю.
  hasQuarter: boolean
}

export type ComponentKey = 'leaf' | 'frame' | 'edge' | 'doorCasing' | 'frameExtensions'

export interface ComponentSelectionDto {
  lengthOptionId?: number
  heightOptionId?: number
  thicknessOptionId?: number
  // Для leaf при doubleSidedPainting = true — цвет фронтальной стороны (см. change
  // add-leaf-double-sided-painting); иначе — единственный цвет, как и раньше.
  colourOptionId?: number
  customLengthValueMm?: number
  customHeightValueMm?: number
  quantity?: number
  mirrorFinishTypeId?: number
  glazingTypeId?: number
  // Цвет задней стороны полотна — допустим только для leaf и только при doubleSidedPainting = true
  // (см. change add-leaf-double-sided-painting).
  backColourOptionId?: number
  // Признак двусторонней покраски полотна — допустим только для leaf (см. change
  // add-leaf-double-sided-painting).
  doubleSidedPainting?: boolean
}

export interface HardwareSelectionDto {
  hardwareOptionId: number
  quantity?: number
}

export type PricingRequestDto = Partial<Record<ComponentKey, ComponentSelectionDto>> & {
  hardware?: HardwareSelectionDto[]
  // Явный признак реверса — используется только расчётом отдельного полотна (см. change
  // add-standalone-leaf-pricing); расчётом по door_configuration игнорируется, там реверс определяется
  // самой конфигурацией.
  isReverse?: boolean
  // id вида кромки (edge_type, не edge_type владения через door_configuration) — читается только
  // расчётом отдельного полотна (см. change add-staged-pricing-endpoints, add-glazing-price-surcharge
  // про аналогичный принцип с mirrorFinishTypeId/glazingTypeId, но edgeTypeId — поле верхнего уровня,
  // а не часть ComponentSelectionDto полотна, поскольку кромка — самостоятельный компонент).
  edgeTypeId?: number
}

export interface ComponentPriceDto {
  component: string
  priced: boolean
  retailPrice: number | null
  dealerPrice: number | null
  baseRetailPrice: number | null
  baseDealerPrice: number | null
}

export interface HardwarePriceDto {
  category: ReferenceDto
  type: ReferenceDto
  colourName: string
  quantity: number
  retailPrice: number
  dealerPrice: number
}

export interface PricingResponseDto {
  totalRetailPrice: number
  totalDealerPrice: number
  components: ComponentPriceDto[]
  hardware: HardwarePriceDto[]
}

// Расчёт этапа «Короб и обрамление» независимо от полотна/кромки/фурнитуры (см. change
// add-staged-pricing-endpoints, frontend-staged-pricing) — frameTypeId передаётся отдельным
// path-параметром запроса, не полем этого DTO.
export interface FrameGroupPricingRequestDto {
  frame?: ComponentSelectionDto
  doorCasingTypeId?: number
  doorCasing?: ComponentSelectionDto
  frameExtensionsTypeId?: number
  frameExtensions?: ComponentSelectionDto
  // Значение высоты полотна, уже известное фронтенду с этапа «Полотно» — нужно только для диапазонных
  // проверок короба/наличника/добора, ранее вычислявшихся backend из опций полотна той же
  // door_configuration.
  leafHeightValue?: number
}

export interface FrameGroupPricingResponseDto {
  totalRetailPrice: number
  totalDealerPrice: number
  components: ComponentPriceDto[]
}

// Объединяет поля PricingRequestDto (этап «Полотно») и FrameGroupPricingRequestDto (этап «Короб и
// обрамление») плюс hardware в один запрос выгрузки спецификации (см. change add-specification-export) —
// зеркально backend SpecificationExportRequestDto. leafTypeId обязателен, остальные id — опциональны, как
// и в исходных этапных запросах.
export interface SpecificationExportRequestDto {
  leafTypeId: number
  leaf?: ComponentSelectionDto
  edgeTypeId?: number
  edge?: ComponentSelectionDto
  isReverse?: boolean
  frameTypeId?: number
  frame?: ComponentSelectionDto
  doorCasingTypeId?: number
  doorCasing?: ComponentSelectionDto
  frameExtensionsTypeId?: number
  frameExtensions?: ComponentSelectionDto
  leafHeightValue?: number
  hardware?: HardwareSelectionDto[]
}

// Одна позиция запроса выгрузки всего заказа (см. change add-order-cart-screen, order-export-api) —
// зеркально backend OrderLineExportRequestDto.
export interface OrderLineExportRequestDto {
  displayName: string
  quantity: number
  specification: SpecificationExportRequestDto
  // Теги атрибутов конфигурации этой позиции (см. change refine-order-export-layout, order-export-api) —
  // те же значения, что и CartItem.attributeTags, добавляются backend в ячейку наименования листа заказа
  // без пересчёта по каталогу.
  attributeTags: string[]
}

// Расчёт этапа «Фурнитура» независимо от door_configuration и её компонентов (см. change
// add-staged-pricing-endpoints, frontend-staged-pricing).
export interface HardwarePricingRequestDto {
  hardware?: HardwareSelectionDto[]
}

export interface HardwarePricingResponseDto {
  totalRetailPrice: number
  totalDealerPrice: number
  hardware: HardwarePriceDto[]
}

export interface HardwareOptionDto {
  id: number
  colourName: string
  retailPrice: number
  dealerPrice: number
}

export interface HardwareTypeDto {
  type: ReferenceDto
  unit: string
  options: HardwareOptionDto[]
}

export interface HardwareCategoryDto {
  category: ReferenceDto
  types: HardwareTypeDto[]
}

export interface DimensionSurchargeRuleDto {
  dimensionType: ReferenceDto
  value: number
  leafTypeId: number | null
  surchargePercent: number
}

export interface MirrorFinishSurchargeDto {
  id: number
  name: string
  shortName: string | null
  surchargePercent: number
}

export interface GlazingSurchargeDto {
  id: number
  name: string
  surchargePercent: number
}

export interface ColourSurchargeDto {
  id: number
  name: string
  surchargePercent: number
}

// ownerType — тот же дискриминатор, что и ComponentPriceDto.component: "frame" | "doorCasing" | "frameExtensions".
export interface PogonazhSurchargeRuleDto {
  ownerType: string
  ownerId: number
  value: number | null
  minValueExclusive: number | null
  maxValueInclusive: number | null
  surchargePercent: number
}

export interface PricingSurchargesDto {
  reverseSurchargePercent: number
  dimensionSurchargeRules: DimensionSurchargeRuleDto[]
  mirrorFinishSurcharges: MirrorFinishSurchargeDto[]
  glazingSurcharges: GlazingSurchargeDto[]
  colourSurcharges: ColourSurchargeDto[]
  pogonazhSurchargeRules: PogonazhSurchargeRuleDto[]
  // См. change add-leaf-double-sided-painting — фиксированный процент, как и reverseSurchargePercent.
  doubleSidedPaintingSurchargePercent: number
  // См. change add-leaf-quarter-attribute — фиксированный процент, как и reverseSurchargePercent; сам факт,
  // применяется ли он (has_quarter истинна сама по себе, а не как следствие реверса/толщины 59мм), решает
  // backend при расчёте — на фронте нужно лишь то же значение для показа в разбивке.
  quarterSurchargePercent: number
}

export interface UpdateCheckDto {
  updateAvailable: boolean
  latestVersion: string | null
  downloadUrl: string | null
}
