package com.example.furniturecalculator.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.furniturecalculator.domain.CatalogType;
import com.example.furniturecalculator.domain.CollectionDimensionRange;
import com.example.furniturecalculator.domain.ColourOption;
import com.example.furniturecalculator.domain.DoorCasingType;
import com.example.furniturecalculator.domain.DoorConfiguration;
import com.example.furniturecalculator.domain.EdgeType;
import com.example.furniturecalculator.domain.FrameExtensionsType;
import com.example.furniturecalculator.domain.FramePost;
import com.example.furniturecalculator.domain.FrameType;
import com.example.furniturecalculator.domain.GlazingOption;
import com.example.furniturecalculator.domain.LeafType;
import com.example.furniturecalculator.domain.LinerDimensionOption;
import com.example.furniturecalculator.domain.MirrorFinishOption;
import com.example.furniturecalculator.dto.ColourOptionDto;
import com.example.furniturecalculator.dto.ComponentCatalogDto;
import com.example.furniturecalculator.dto.DimensionRangeDto;
import com.example.furniturecalculator.dto.DoorConfigurationDto;
import com.example.furniturecalculator.dto.FramePostDto;
import com.example.furniturecalculator.dto.LinerDimensionOptionDto;
import com.example.furniturecalculator.dto.ReferenceDto;
import com.example.furniturecalculator.repository.ColourOptionRepository;
import com.example.furniturecalculator.repository.CollectionDimensionRangeRepository;
import com.example.furniturecalculator.repository.DoorConfigurationRepository;
import com.example.furniturecalculator.repository.FramePostRepository;
import com.example.furniturecalculator.repository.GlazingOptionRepository;
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
    private final GlazingOptionRepository glazingOptionRepository;
    private final CollectionDimensionRangeRepository collectionDimensionRangeRepository;

    // Каталог отдаёт до нескольких тысяч door_configuration, многие из которых ссылаются на одни и те же
    // leaf_type/frame_type/edge_type/door_casing_type/frame_extensions_type — buildXxxComponent(...) в 5-6 раз
    // дороже одного SELECT (dimension/colour/mirror/glazing/posts), поэтому строится один раз на каждый
    // различный id, а не на каждую строку door_configuration (см. change add-glazing-catalog-for-v-models,
    // после которого производительность стала заметна: без дедупликации запрос отдавал каталог за 35–67 с).
    @Transactional(readOnly = true)
    public List<DoorConfigurationDto> getAllConfigurations() {
        List<DoorConfiguration> configurations = doorConfigurationRepository.findAllWithTypes();

        Map<Long, ComponentCatalogDto> leafComponents = distinctById(configurations, DoorConfiguration::getLeafType).values()
                .stream()
                .collect(Collectors.toMap(LeafType::getId, this::buildLeafComponent));
        Map<Long, ComponentCatalogDto> frameComponents = distinctById(configurations, DoorConfiguration::getFrameType).values()
                .stream()
                .collect(Collectors.toMap(FrameType::getId, this::buildFrameComponent));
        Map<Long, ComponentCatalogDto> edgeComponents = distinctById(configurations, DoorConfiguration::getEdgeType).values()
                .stream()
                .collect(Collectors.toMap(EdgeType::getId, this::buildComponent));
        Map<Long, ComponentCatalogDto> doorCasingComponents =
                distinctById(configurations, DoorConfiguration::getDoorCasingType).values().stream()
                        .collect(Collectors.toMap(DoorCasingType::getId, this::buildComponent));
        Map<Long, ComponentCatalogDto> frameExtensionsComponents =
                distinctById(configurations, DoorConfiguration::getFrameExtensionsType).values().stream()
                        .collect(Collectors.toMap(FrameExtensionsType::getId, this::buildComponent));

        return configurations.stream()
                .map(configuration -> toDto(
                        configuration, leafComponents, frameComponents, edgeComponents, doorCasingComponents, frameExtensionsComponents))
                .toList();
    }

    private static <T extends CatalogType> Map<Long, T> distinctById(
            List<DoorConfiguration> configurations, Function<DoorConfiguration, T> extractor) {
        return configurations.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(CatalogType::getId, Function.identity(), (first, second) -> first));
    }

    private DoorConfigurationDto toDto(
            DoorConfiguration configuration,
            Map<Long, ComponentCatalogDto> leafComponents,
            Map<Long, ComponentCatalogDto> frameComponents,
            Map<Long, ComponentCatalogDto> edgeComponents,
            Map<Long, ComponentCatalogDto> doorCasingComponents,
            Map<Long, ComponentCatalogDto> frameExtensionsComponents) {
        return new DoorConfigurationDto(
                configuration.getId(),
                leafComponents.get(configuration.getLeafType().getId()),
                componentOrNull(configuration.getFrameType(), frameComponents),
                componentOrNull(configuration.getEdgeType(), edgeComponents),
                componentOrNull(configuration.getDoorCasingType(), doorCasingComponents),
                componentOrNull(configuration.getFrameExtensionsType(), frameExtensionsComponents),
                configuration.isReverse());
    }

    private static ComponentCatalogDto componentOrNull(CatalogType type, Map<Long, ComponentCatalogDto> componentsById) {
        return type == null ? null : componentsById.get(type.getId());
    }

    private ComponentCatalogDto buildLeafComponent(LeafType leafType) {
        ComponentCatalogDto component = buildComponent(leafType);
        List<ReferenceDto> mirrorFinishOptions = mirrorFinishOptionRepository.findByLeafTypeId(leafType.getId()).stream()
                .map(this::toDto)
                .toList();
        List<ReferenceDto> glazingOptions = glazingOptionRepository.findByLeafTypeId(leafType.getId()).stream()
                .map(this::toDto)
                .toList();
        List<DimensionRangeDto> dimensionRanges = collectionDimensionRangeRepository
                .findByCollectionId(leafType.getCollection().getId()).stream()
                .map(this::toDto)
                .toList();
        return new ComponentCatalogDto(
                component.type(),
                ReferenceDto.from(leafType.getCollection()),
                component.dimensionOptions(),
                component.colourOptions(),
                component.posts(),
                mirrorFinishOptions,
                leafType.getPanelType(),
                glazingOptions,
                dimensionRanges);
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
                List.of(), null, List.of(), List.of());
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
        return new ComponentCatalogDto(
                ReferenceDto.from(type), null, dimensionOptions, colourOptions, List.of(), List.of(), null, List.of(), List.of());
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

    private DimensionRangeDto toDto(CollectionDimensionRange range) {
        return new DimensionRangeDto(ReferenceDto.from(range.getLinerDimensionType()), range.getMinValue(), range.getMaxValue());
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

    // id — glazing_type.id (не glazing_option.id), по образцу id mirror_finish_type в toDto(MirrorFinishOption)
    // (см. change add-glazing-catalog-for-v-models) — короткого имени у вида остекления пока нет.
    private ReferenceDto toDto(GlazingOption option) {
        return new ReferenceDto(option.getGlazingType().getId(), null, option.getGlazingType().getName(), null);
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
