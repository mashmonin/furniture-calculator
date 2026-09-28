package com.example.furniturecalculator.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.furniturecalculator.domain.DecorativeElementCategory;
import com.example.furniturecalculator.domain.DecorativeElementType;
import com.example.furniturecalculator.dto.DecorativeElementCategoryDto;
import com.example.furniturecalculator.dto.DecorativeElementTypeDto;
import com.example.furniturecalculator.dto.ReferenceDto;
import com.example.furniturecalculator.repository.DecorativeElementCategoryRepository;
import com.example.furniturecalculator.repository.DecorativeElementTypeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DecorativeElementCatalogService {

    private final DecorativeElementCategoryRepository decorativeElementCategoryRepository;
    private final DecorativeElementTypeRepository decorativeElementTypeRepository;

    @Transactional(readOnly = true)
    public List<DecorativeElementCategoryDto> getCatalog() {
        Map<Long, List<DecorativeElementType>> typesByCategoryId = decorativeElementTypeRepository.findAll().stream()
                .collect(Collectors.groupingBy(type -> type.getDecorativeElementCategory().getId()));

        return decorativeElementCategoryRepository.findAll().stream()
                .map(category -> toDto(category, typesByCategoryId.getOrDefault(category.getId(), List.of())))
                .toList();
    }

    private DecorativeElementCategoryDto toDto(DecorativeElementCategory category, List<DecorativeElementType> types) {
        List<DecorativeElementTypeDto> typeDtos = types.stream().map(this::toDto).toList();
        return new DecorativeElementCategoryDto(ReferenceDto.from(category), typeDtos);
    }

    private DecorativeElementTypeDto toDto(DecorativeElementType type) {
        return new DecorativeElementTypeDto(ReferenceDto.from(type), type.getLengthMm(), type.getRetailPrice(), type.getDealerPrice());
    }
}
