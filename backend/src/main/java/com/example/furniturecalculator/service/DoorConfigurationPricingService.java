package com.example.furniturecalculator.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import com.example.furniturecalculator.domain.FramePost;
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
import com.example.furniturecalculator.repository.FramePostRepository;
import com.example.furniturecalculator.repository.LinerDimensionOptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoorConfigurationPricingService {

    // Код типа размера "ВЫСОТА" из справочника liner_dimension_type (см. db.changelog 0004) — стабильный бизнес-ключ.
    private static final String HEIGHT_TYPE_CODE = "DT-002";

    // Временно: если выбранный короб реверсивный (frame_type.is_reverse), надбавка за реверс —
    // фиксированный процент от цены полотна той же конфигурации. В перспективе будет вынесена
    // в движок бизнес-правил (Drools); тогда applyReverseSurcharge заменится вызовом правил
    // вместо жёстко заданного множителя.
    private static final BigDecimal REVERSE_SURCHARGE_MULTIPLIER = new BigDecimal("1.10");

    private final DoorConfigurationRepository doorConfigurationRepository;
    private final LinerDimensionOptionRepository linerDimensionOptionRepository;
    private final ColourOptionRepository colourOptionRepository;
    private final ConfigurationPriceRepository configurationPriceRepository;
    private final FramePostRepository framePostRepository;

    @Transactional(readOnly = true)
    public PricingResponseDto calculate(Long doorConfigurationId, PricingRequestDto request) {
        DoorConfiguration configuration = doorConfigurationRepository.findById(doorConfigurationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "door_configuration с id=" + doorConfigurationId + " не найдена"));

        ComponentSelectionDto leafSelection = selectionOf(request, PricingRequestDto::leaf);
        LinerDimensionOption leafHeightOption =
                validatedDimensionOption("leaf", configuration.getLeafType(), leafSelection.heightOptionId());
        boolean reverseFrameSelected =
                configuration.getFrameType() != null && configuration.getFrameType().isReverse();

        List<ComponentPriceDto> components = new ArrayList<>();
        addComponentIfPresent(components, "leaf", configuration.getLeafType(), leafSelection, leafHeightOption, reverseFrameSelected);
        addComponentIfPresent(components, "frame", configuration.getFrameType(), selectionOf(request, PricingRequestDto::frame), leafHeightOption, false);
        addComponentIfPresent(components, "edge", configuration.getEdgeType(), selectionOf(request, PricingRequestDto::edge), leafHeightOption, false);
        addComponentIfPresent(components, "doorCasing", configuration.getDoorCasingType(), selectionOf(request, PricingRequestDto::doorCasing), leafHeightOption, false);
        addComponentIfPresent(components, "frameExtensions", configuration.getFrameExtensionsType(), selectionOf(request, PricingRequestDto::frameExtensions), leafHeightOption, false);

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
            List<ComponentPriceDto> components, String componentName, CatalogType type, ComponentSelectionDto selection,
            LinerDimensionOption leafHeightOption, boolean applyReverseSurcharge) {
        if (type == null) {
            return;
        }

        if (type instanceof FrameType frameType) {
            ColourOption colourOption = validatedColourOption(componentName, frameType, selection.colourOptionId());
            components.add(framePostPrice(componentName, frameType, colourOption));
            return;
        }

        LinerDimensionOption lengthOption = validatedDimensionOption(componentName, type, selection.lengthOptionId());
        LinerDimensionOption heightOption = type instanceof LeafType
                ? leafHeightOption
                : validatedDimensionOption(componentName, type, selection.heightOptionId());
        LinerDimensionOption thicknessOption = validatedDimensionOption(componentName, type, selection.thicknessOptionId());
        ColourOption colourOption = validatedColourOption(componentName, type, selection.colourOptionId());

        if (type instanceof EdgeType && heightOption != null) {
            validateHeightWithinLeafRange(componentName, heightOption, leafHeightOption);
        }

        Optional<ConfigurationPrice> matched = findMostSpecificPrice(type, lengthOption, heightOption, thicknessOption, colourOption);
        components.add(matched
                .map(price -> componentPriceFrom(componentName, price, applyReverseSurcharge))
                .orElseGet(() -> new ComponentPriceDto(componentName, false, null, null)));
    }

    private ComponentPriceDto componentPriceFrom(String componentName, ConfigurationPrice price, boolean applyReverseSurcharge) {
        BigDecimal retailPrice = price.getRetailPrice();
        BigDecimal dealerPrice = price.getDealerPrice();
        if (applyReverseSurcharge) {
            retailPrice = applyReverseSurcharge(retailPrice);
            dealerPrice = applyReverseSurcharge(dealerPrice);
        }
        return new ComponentPriceDto(componentName, true, retailPrice, dealerPrice);
    }

    private BigDecimal applyReverseSurcharge(BigDecimal price) {
        return price.multiply(REVERSE_SURCHARGE_MULTIPLIER).setScale(0, RoundingMode.HALF_UP);
    }

    private void validateHeightWithinLeafRange(
            String componentName, LinerDimensionOption edgeHeightOption, LinerDimensionOption leafHeightOption) {
        if (!HEIGHT_TYPE_CODE.equals(edgeHeightOption.getLinerDimensionType().getCode())) {
            return;
        }
        if (leafHeightOption == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "для компонента " + componentName + " выбрана высота, но высота полотна не выбрана");
        }
        BigDecimal leafHeight = leafHeightOption.getValue();
        BigDecimal min = edgeHeightOption.getMinValue();
        BigDecimal max = edgeHeightOption.getValue();
        boolean withinRange = (min == null || leafHeight.compareTo(min) >= 0) && leafHeight.compareTo(max) <= 0;
        if (!withinRange) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "liner_dimension_option с id=" + edgeHeightOption.getId() + " не совместима с высотой полотна");
        }
    }

    private ComponentPriceDto framePostPrice(String componentName, FrameType frameType, ColourOption colourOption) {
        List<FramePost> posts = framePostRepository.findByFrameTypeId(frameType.getId());
        BigDecimal postsRetailPrice = posts.stream().map(FramePost::getRetailPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal postsDealerPrice = posts.stream().map(FramePost::getDealerPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ColourOption> availableColours = colourOptionRepository.findByFrameTypeId(frameType.getId());
        if (availableColours.isEmpty()) {
            if (posts.isEmpty()) {
                return new ComponentPriceDto(componentName, false, null, null);
            }
            return new ComponentPriceDto(componentName, true, postsRetailPrice, postsDealerPrice);
        }

        Optional<ConfigurationPrice> matched = findMostSpecificPrice(frameType, null, null, null, colourOption);
        return matched
                .map(price -> new ComponentPriceDto(
                        componentName, true, postsRetailPrice.add(price.getRetailPrice()), postsDealerPrice.add(price.getDealerPrice())))
                .orElseGet(() -> new ComponentPriceDto(componentName, false, null, null));
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
