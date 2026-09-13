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
import com.example.furniturecalculator.domain.FramePost;
import com.example.furniturecalculator.domain.FrameType;
import com.example.furniturecalculator.domain.LeafType;
import com.example.furniturecalculator.domain.LinerDimensionOption;
import com.example.furniturecalculator.domain.MirrorFinishOption;
import com.example.furniturecalculator.dto.ColourOptionDto;
import com.example.furniturecalculator.dto.ComponentCatalogDto;
import com.example.furniturecalculator.dto.DoorConfigurationDto;
import com.example.furniturecalculator.dto.FramePostDto;
import com.example.furniturecalculator.dto.LinerDimensionOptionDto;
import com.example.furniturecalculator.dto.ReferenceDto;
import com.example.furniturecalculator.repository.ColourOptionRepository;
import com.example.furniturecalculator.repository.DoorConfigurationRepository;
import com.example.furniturecalculator.repository.FramePostRepository;
import com.example.furniturecalculator.repository.LinerDimensionOptionRepository;
import com.example.furniturecalculator.repository.MirrorFinishOptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoorConfigurationCatalogService {

    private final DoorConfigurationRepository doorConfigurationRepository;
    private final LinerDimensionOptionRepository linerDimensionOptionRepository;
    private final ColourOptionRepository colourOptionRepository;
    private final FramePostRepository framePostRepository;
    private final MirrorFinishOptionRepository mirrorFinishOptionRepository;

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
                buildFrameComponent(configuration.getFrameType()),
                buildComponent(configuration.getEdgeType()),
                buildComponent(configuration.getDoorCasingType()),
                buildComponent(configuration.getFrameExtensionsType()),
                configuration.isReverse());
    }

    private ComponentCatalogDto buildLeafComponent(LeafType leafType) {
        ComponentCatalogDto component = buildComponent(leafType);
        List<ReferenceDto> mirrorFinishOptions = mirrorFinishOptionRepository.findByLeafTypeId(leafType.getId()).stream()
                .map(this::toDto)
                .toList();
        return new ComponentCatalogDto(
                component.type(),
                ReferenceDto.from(leafType.getCollection()),
                component.dimensionOptions(),
                component.colourOptions(),
                component.posts(),
                mirrorFinishOptions);
    }

    private ComponentCatalogDto buildFrameComponent(FrameType frameType) {
        if (frameType == null) {
            return null;
        }
        ComponentCatalogDto component = buildComponent(frameType);
        List<FramePostDto> posts = framePostRepository.findByFrameTypeId(frameType.getId()).stream()
                .map(this::toDto)
                .toList();
        return new ComponentCatalogDto(
                component.type(), component.collection(), component.dimensionOptions(), component.colourOptions(), posts,
                List.of());
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
        return new ComponentCatalogDto(ReferenceDto.from(type), null, dimensionOptions, colourOptions, List.of(), List.of());
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
                option.getMinValue(),
                option.getMaxValue(),
                option.isStandard());
    }

    private ColourOptionDto toDto(ColourOption option) {
        return new ColourOptionDto(option.getId(), ReferenceDto.from(option.getColourType()));
    }

    // id — mirror_finish_type.id (не mirror_finish_option.id): тот же id клиент передаёт в запросе расчёта
    // и по нему же ищет процент в ответе GET /api/pricing-surcharges — единое пространство id для каталога,
    // запроса и разбивки надбавок (см. change add-mirror-finish-leaf-option).
    private ReferenceDto toDto(MirrorFinishOption option) {
        return new ReferenceDto(
                option.getMirrorFinishType().getId(),
                null,
                option.getMirrorFinishType().getName(),
                option.getMirrorFinishType().getShortName());
    }

    private FramePostDto toDto(FramePost post) {
        return new FramePostDto(
                post.getId(),
                ReferenceDto.from(post.getPostType()),
                post.getQuantity(),
                post.getLength(),
                post.getRetailPrice(),
                post.getDealerPrice());
    }
}
