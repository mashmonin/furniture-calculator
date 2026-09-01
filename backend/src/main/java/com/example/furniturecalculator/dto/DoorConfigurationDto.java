package com.example.furniturecalculator.dto;

public record DoorConfigurationDto(
        Long id,
        ComponentCatalogDto leaf,
        ComponentCatalogDto frame,
        ComponentCatalogDto edge,
        ComponentCatalogDto doorCasing,
        ComponentCatalogDto frameExtensions,
        boolean reverse) {
}
