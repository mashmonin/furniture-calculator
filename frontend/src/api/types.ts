export interface ReferenceDto {
  id: number
  code: string
  name: string
}

export interface LinerDimensionOptionDto {
  id: number
  dimensionType: ReferenceDto
  value: number
  minValue: number | null
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

export interface ComponentCatalogDto {
  type: ReferenceDto
  collection: ReferenceDto | null
  dimensionOptions: LinerDimensionOptionDto[]
  colourOptions: ColourOptionDto[]
  posts: FramePostDto[]
  mirrorFinishOptions: ReferenceDto[]
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

export type PricingRequestDto = Partial<Record<ComponentKey, ComponentSelectionDto>>

export interface ComponentPriceDto {
  component: string
  priced: boolean
  retailPrice: number | null
  dealerPrice: number | null
  baseRetailPrice: number | null
  baseDealerPrice: number | null
}

export interface PricingResponseDto {
  totalRetailPrice: number
  totalDealerPrice: number
  components: ComponentPriceDto[]
}

export interface DimensionSurchargeRuleDto {
  dimensionType: ReferenceDto
  value: number
  surchargePercent: number
}

export interface MirrorFinishSurchargeDto {
  id: number
  name: string
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
