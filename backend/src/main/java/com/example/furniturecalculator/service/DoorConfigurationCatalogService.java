package com.example.furniturecalculator.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.furniturecalculator.domain.CatalogType;
import com.example.furniturecalculator.domain.ColourOption;
import com.example.furniturecalculator.domain.DoorCasingType;
import com.example.furniturecalculator.domain.DoorConfiguration;
import com.example.furniturecalculator.domain.EdgeType;
import com.example.furniturecalculator.domain.FrameExtensionsType;
import com.example.furniturecalculator.domain.FrameType;
import com.example.furniturecalculator.domain.LeafType;
import com.example.furniturecalculator.domain.LinerDimensionOption;
import com.example.furniturecalculator.dto.ColourOptionDto;
import com.example.furniturecalculator.dto.ComponentCatalogDto;
import com.example.furniturecalculator.dto.DoorConfigurationDto;
import com.example.furniturecalculator.dto.LinerDimensionOptionDto;
import com.example.furniturecalculator.dto.ReferenceDto;
import com.example.furniturecalculator.repository.ColourOptionRepository;
import com.example.furniturecalculator.repository.DoorConfigurationRepository;
import com.example.furniturecalculator.repository.LinerDimensionOptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoorConfigurationCatalogService {

    private final DoorConfigurationRepository doorConfigurationRepository;
    private final LinerDimensionOptionRepository linerDimensionOptionRepository;
    private final ColourOptionRepository colourOptionRepository;

    @Transactional(readOnly = true)
    public List<DoorConfigurationDto> getAllConfigurations() {
        return doorConfigurationRepository.findAllWithTypes().stream()
                .map(this::toDto)
                .toList();
    }

    private DoorConfigurationDto toDto(DoorConfiguration configuration) {
        return new DoorConfigurationDto(
                configuration.getId(),
                buildLeafComponent(configuration.getLeafType()),
                buildComponent(configuration.getFrameType()),
                buildComponent(configuration.getEdgeType()),
                buildComponent(configuration.getDoorCasingType()),
                buildComponent(configuration.getFrameExtensionsType()));
    }

    private ComponentCatalogDto buildLeafComponent(LeafType leafType) {
        ComponentCatalogDto component = buildComponent(leafType);
        return new ComponentCatalogDto(
                component.type(),
                ReferenceDto.from(leafType.getCollection()),
                component.dimensionOptions(),
                component.colourOptions());
    }

    private ComponentCatalogDto buildComponent(CatalogType type) {
        if (type == null) {
            return null;
        }
        List<LinerDimensionOptionDto> dimensionOptions = dimensionOptionsFor(type).stream()
                .map(this::toDto)
                .toList();
        List<ColourOptionDto> colourOptions = colourOptionsFor(type).stream()
                .map(this::toDto)
                .toList();
        return new ComponentCatalogDto(ReferenceDto.from(type), null, dimensionOptions, colourOptions);
    }

    private List<LinerDimensionOption> dimensionOptionsFor(CatalogType type) {
        return switch (type) {
            case LeafType t -> linerDimensionOptionRepository.findByLeafTypeId(t.getId());
            case FrameType t -> linerDimensionOptionRepository.findByFrameTypeId(t.getId());
            case EdgeType t -> linerDimensionOptionRepository.findByEdgeTypeId(t.getId());
            case DoorCasingType t -> linerDimensionOptionRepository.findByDoorCasingTypeId(t.getId());
            case FrameExtensionsType t -> linerDimensionOptionRepository.findByFrameExtensionsTypeId(t.getId());
            default -> throw new IllegalStateException("Неизвестный тип компонента: " + type.getClass());
        };
    }

    private List<ColourOption> colourOptionsFor(CatalogType type) {
        return switch (type) {
            case LeafType t -> colourOptionRepository.findByLeafTypeId(t.getId());
            case FrameType t -> colourOptionRepository.findByFrameTypeId(t.getId());
            case EdgeType t -> colourOptionRepository.findByEdgeTypeId(t.getId());
            case DoorCasingType t -> colourOptionRepository.findByDoorCasingTypeId(t.getId());
            case FrameExtensionsType t -> colourOptionRepository.findByFrameExtensionsTypeId(t.getId());
            default -> throw new IllegalStateException("Неизвестный тип компонента: " + type.getClass());
        };
    }

    private LinerDimensionOptionDto toDto(LinerDimensionOption option) {
        return new LinerDimensionOptionDto(
                option.getId(),
                ReferenceDto.from(option.getLinerDimensionType()),
                option.getValue(),
                option.isStandard());
    }

    private ColourOptionDto toDto(ColourOption option) {
        return new ColourOptionDto(option.getId(), ReferenceDto.from(option.getColourType()));
    }
}
