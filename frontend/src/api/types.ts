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

export interface ComponentCatalogDto {
  type: ReferenceDto
  collection: ReferenceDto | null
  dimensionOptions: LinerDimensionOptionDto[]
  colourOptions: ColourOptionDto[]
  posts: FramePostDto[]
  mirrorFinishOptions: ReferenceDto[]
  panelType: LeafPanelType | null
}

export interface DoorConfigurationDto {
  id: number
  leaf: ComponentCatalogDto
  frame: ComponentCatalogDto | null
  edge: ComponentCatalogDto | null
  doorCasing: ComponentCatalogDto | null
  frameExtensions: ComponentCatalogDto | null
  reverse: boolean
}

export type ComponentKey = 'leaf' | 'frame' | 'edge' | 'doorCasing' | 'frameExtensions'

export interface ComponentSelectionDto {
  lengthOptionId?: number
  heightOptionId?: number
  thicknessOptionId?: number
  colourOptionId?: number
  customLengthValueMm?: number
  customHeightValueMm?: number
  quantity?: number
  mirrorFinishTypeId?: number
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
  surchargePercent: number
}

export interface MirrorFinishSurchargeDto {
  id: number
  name: string
  shortName: string | null
  surchargePercent: number
}

export interface PricingSurchargesDto {
  reverseSurchargePercent: number
  dimensionSurchargeRules: DimensionSurchargeRuleDto[]
  mirrorFinishSurcharges: MirrorFinishSurchargeDto[]
}

export interface UpdateCheckDto {
  updateAvailable: boolean
  latestVersion: string | null
  downloadUrl: string | null
}
