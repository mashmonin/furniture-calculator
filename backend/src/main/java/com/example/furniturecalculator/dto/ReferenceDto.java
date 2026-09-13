package com.example.furniturecalculator.dto;

import com.example.furniturecalculator.domain.CatalogType;

public record ReferenceDto(Long id, String code, String name, String shortName) {

    public static ReferenceDto from(CatalogType type) {
        return new ReferenceDto(type.getId(), type.getCode(), type.getName(), type.getShortName());
    }
}
