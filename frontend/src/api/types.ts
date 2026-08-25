export interface ReferenceDto {
  id: number
  code: string
  name: string
}

export interface LinerDimensionOptionDto {
  id: number
  dimensionType: ReferenceDto
  value: number
  standard: boolean
}

export interface ColourOptionDto {
  id: number
  colourType: ReferenceDto
}

export interface ComponentCatalogDto {
  type: ReferenceDto
  collection: ReferenceDto | null
  dimensionOptions: LinerDimensionOptionDto[]
  colourOptions: ColourOptionDto[]
}

export interface DoorConfigurationDto {
  id: number
  leaf: ComponentCatalogDto
  frame: ComponentCatalogDto | null
  edge: ComponentCatalogDto | null
  doorCasing: ComponentCatalogDto | null
  frameExtensions: ComponentCatalogDto | null
}

export type ComponentKey = 'leaf' | 'frame' | 'edge' | 'doorCasing' | 'frameExtensions'

export interface ComponentSelectionDto {
  lengthOptionId?: number
  heightOptionId?: number
  thicknessOptionId?: number
  colourOptionId?: number
}

export type PricingRequestDto = Partial<Record<ComponentKey, ComponentSelectionDto>>

export interface ComponentPriceDto {
  component: string
  priced: boolean
  retailPrice: number | null
  dealerPrice: number | null
}

export interface PricingResponseDto {
  totalRetailPrice: number
  totalDealerPrice: number
  components: ComponentPriceDto[]
}
