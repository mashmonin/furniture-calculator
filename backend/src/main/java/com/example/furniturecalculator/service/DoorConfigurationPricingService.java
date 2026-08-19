package com.example.furniturecalculator.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.furniturecalculator.domain.CatalogType;
import com.example.furniturecalculator.domain.ColourOption;
import com.example.furniturecalculator.domain.ConfigurationPrice;
import com.example.furniturecalculator.domain.DoorCasingType;
import com.example.furniturecalculator.domain.DoorConfiguration;
import com.example.furniturecalculator.domain.EdgeType;
import com.example.furniturecalculator.domain.FrameExtensionsType;
import com.example.furniturecalculator.domain.FrameType;
import com.example.furniturecalculator.domain.LeafType;
import com.example.furniturecalculator.domain.LinerDimensionOption;
import com.example.furniturecalculator.dto.ComponentPriceDto;
import com.example.furniturecalculator.dto.ComponentSelectionDto;
import com.example.furniturecalculator.dto.PricingRequestDto;
import com.example.furniturecalculator.dto.PricingResponseDto;
import com.example.furniturecalculator.repository.ColourOptionRepository;
import com.example.furniturecalculator.repository.ConfigurationPriceRepository;
import com.example.furniturecalculator.repository.DoorConfigurationRepository;
import com.example.furniturecalculator.repository.LinerDimensionOptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoorConfigurationPricingService {

    private final DoorConfigurationRepository doorConfigurationRepository;
    private final LinerDimensionOptionRepository linerDimensionOptionRepository;
    private final ColourOptionRepository colourOptionRepository;
    private final ConfigurationPriceRepository configurationPriceRepository;

    @Transactional(readOnly = true)
    public PricingResponseDto calculate(Long doorConfigurationId, PricingRequestDto request) {
        DoorConfiguration configuration = doorConfigurationRepository.findById(doorConfigurationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "door_configuration с id=" + doorConfigurationId + " не найдена"));

        List<ComponentPriceDto> components = new ArrayList<>();
        addComponentIfPresent(components, "leaf", configuration.getLeafType(), selectionOf(request, PricingRequestDto::leaf));
        addComponentIfPresent(components, "frame", configuration.getFrameType(), selectionOf(request, PricingRequestDto::frame));
        addComponentIfPresent(components, "edge", configuration.getEdgeType(), selectionOf(request, PricingRequestDto::edge));
        addComponentIfPresent(components, "doorCasing", configuration.getDoorCasingType(), selectionOf(request, PricingRequestDto::doorCasing));
        addComponentIfPresent(components, "frameExtensions", configuration.getFrameExtensionsType(), selectionOf(request, PricingRequestDto::frameExtensions));

        BigDecimal totalRetail = components.stream()
                .filter(ComponentPriceDto::priced)
                .map(ComponentPriceDto::retailPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDealer = components.stream()
                .filter(ComponentPriceDto::priced)
                .map(ComponentPriceDto::dealerPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PricingResponseDto(totalRetail, totalDealer, components);
    }

    private ComponentSelectionDto selectionOf(
            PricingRequestDto request,
            java.util.function.Function<PricingRequestDto, ComponentSelectionDto> extractor) {
        if (request == null) {
            return ComponentSelectionDto.EMPTY;
        }
        ComponentSelectionDto selection = extractor.apply(request);
        return selection != null ? selection : ComponentSelectionDto.EMPTY;
    }

    private void addComponentIfPresent(
            List<ComponentPriceDto> components, String componentName, CatalogType type, ComponentSelectionDto selection) {
        if (type == null) {
            return;
        }

        LinerDimensionOption lengthOption = validatedDimensionOption(componentName, type, selection.lengthOptionId());
        LinerDimensionOption heightOption = validatedDimensionOption(componentName, type, selection.heightOptionId());
        LinerDimensionOption thicknessOption = validatedDimensionOption(componentName, type, selection.thicknessOptionId());
        ColourOption colourOption = validatedColourOption(componentName, type, selection.colourOptionId());

        Optional<ConfigurationPrice> matched = findMostSpecificPrice(type, lengthOption, heightOption, thicknessOption, colourOption);
        components.add(matched
                .map(price -> new ComponentPriceDto(componentName, true, price.getRetailPrice(), price.getDealerPrice()))
                .orElseGet(() -> new ComponentPriceDto(componentName, false, null, null)));
    }

    private LinerDimensionOption validatedDimensionOption(String componentName, CatalogType type, Long optionId) {
        if (optionId == null) {
            return null;
        }
        LinerDimensionOption option = linerDimensionOptionRepository.findById(optionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "liner_dimension_option с id=" + optionId + " не найдена"));
        if (!belongsToLeaf(option.getLeafType(), option.getFrameType(), option.getEdgeType(),
                option.getDoorCasingType(), option.getFrameExtensionsType(), type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "liner_dimension_option с id=" + optionId + " не принадлежит компоненту " + componentName);
        }
        return option;
    }

    private ColourOption validatedColourOption(String componentName, CatalogType type, Long optionId) {
        if (optionId == null) {
            return null;
        }
        ColourOption option = colourOptionRepository.findById(optionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "colour_option с id=" + optionId + " не найдена"));
        if (!belongsToLeaf(option.getLeafType(), option.getFrameType(), option.getEdgeType(),
                option.getDoorCasingType(), option.getFrameExtensionsType(), type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "colour_option с id=" + optionId + " не принадлежит компоненту " + componentName);
        }
        return option;
    }

    private boolean belongsToLeaf(
            LeafType leaf, FrameType frame, EdgeType edge, DoorCasingType doorCasing, FrameExtensionsType frameExtensions,
            CatalogType type) {
        Long ownerId = switch (type) {
            case LeafType t -> leaf != null ? leaf.getId() : null;
            case FrameType t -> frame != null ? frame.getId() : null;
            case EdgeType t -> edge != null ? edge.getId() : null;
            case DoorCasingType t -> doorCasing != null ? doorCasing.getId() : null;
            case FrameExtensionsType t -> frameExtensions != null ? frameExtensions.getId() : null;
            default -> null;
        };
        return ownerId != null && ownerId.equals(type.getId());
    }

    private Optional<ConfigurationPrice> findMostSpecificPrice(
            CatalogType type, LinerDimensionOption length, LinerDimensionOption height,
            LinerDimensionOption thickness, ColourOption colour) {
        List<ConfigurationPrice> matching = pricesFor(type).stream()
                .filter(price -> matchesDimension(price.getLengthOption(), length))
                .filter(price -> matchesDimension(price.getHeightOption(), height))
                .filter(price -> matchesDimension(price.getThicknessOption(), thickness))
                .filter(price -> matchesColour(price.getColourOption(), colour))
                .toList();

        if (matching.isEmpty()) {
            return Optional.empty();
        }

        int maxSpecificity = matching.stream().mapToInt(this::specificity).max().orElseThrow();
        List<ConfigurationPrice> mostSpecific = matching.stream()
                .filter(price -> specificity(price) == maxSpecificity)
                .toList();

        return mostSpecific.size() == 1 ? Optional.of(mostSpecific.get(0)) : Optional.empty();
    }

    private boolean matchesDimension(LinerDimensionOption priceOption, LinerDimensionOption selected) {
        return priceOption == null || Objects.equals(priceOption.getId(), selected != null ? selected.getId() : null);
    }

    private boolean matchesColour(ColourOption priceOption, ColourOption selected) {
        return priceOption == null || Objects.equals(priceOption.getId(), selected != null ? selected.getId() : null);
    }

    private int specificity(ConfigurationPrice price) {
        int count = 0;
        if (price.getLengthOption() != null) count++;
        if (price.getHeightOption() != null) count++;
        if (price.getThicknessOption() != null) count++;
        if (price.getColourOption() != null) count++;
        return count;
    }

    private List<ConfigurationPrice> pricesFor(CatalogType type) {
        return switch (type) {
            case LeafType t -> configurationPriceRepository.findByLeafTypeId(t.getId());
            case FrameType t -> configurationPriceRepository.findByFrameTypeId(t.getId());
            case EdgeType t -> configurationPriceRepository.findByEdgeTypeId(t.getId());
            case DoorCasingType t -> configurationPriceRepository.findByDoorCasingTypeId(t.getId());
            case FrameExtensionsType t -> configurationPriceRepository.findByFrameExtensionsTypeId(t.getId());
            default -> List.of();
        };
    }
}
