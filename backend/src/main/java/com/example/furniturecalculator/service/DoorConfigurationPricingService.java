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
import com.example.furniturecalculator.domain.DimensionSurchargeRule;
import com.example.furniturecalculator.domain.DoorCasingType;
import com.example.furniturecalculator.domain.DoorConfiguration;
import com.example.furniturecalculator.domain.EdgeType;
import com.example.furniturecalculator.domain.FrameExtensionsType;
import com.example.furniturecalculator.domain.FramePost;
import com.example.furniturecalculator.domain.FrameType;
import com.example.furniturecalculator.domain.LeafType;
import com.example.furniturecalculator.domain.LinerDimensionOption;
import com.example.furniturecalculator.domain.LinerDimensionType;
import com.example.furniturecalculator.dto.ComponentPriceDto;
import com.example.furniturecalculator.dto.ComponentSelectionDto;
import com.example.furniturecalculator.dto.PricingRequestDto;
import com.example.furniturecalculator.dto.PricingResponseDto;
import com.example.furniturecalculator.repository.ColourOptionRepository;
import com.example.furniturecalculator.repository.ConfigurationPriceRepository;
import com.example.furniturecalculator.repository.DimensionSurchargeRuleRepository;
import com.example.furniturecalculator.repository.DoorConfigurationRepository;
import com.example.furniturecalculator.repository.FramePostRepository;
import com.example.furniturecalculator.repository.LinerDimensionOptionRepository;
import com.example.furniturecalculator.repository.LinerDimensionTypeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoorConfigurationPricingService {

    // Коды типов размера из справочника liner_dimension_type (см. db.changelog 0004) — стабильные бизнес-ключи.
    private static final String LENGTH_TYPE_CODE = "DT-001";
    private static final String HEIGHT_TYPE_CODE = "DT-002";

    // Временно: если выбранная конфигурация реверсивная (door_configuration.is_reverse), надбавка за реверс —
    // фиксированный процент от цены полотна той же конфигурации. В перспективе будет вынесена
    // в движок бизнес-правил (Drools); тогда applyReverseSurcharge заменится вызовом правил
    // вместо жёстко заданного множителя.
    private static final BigDecimal REVERSE_SURCHARGE_MULTIPLIER = new BigDecimal("1.10");

    private final DoorConfigurationRepository doorConfigurationRepository;
    private final LinerDimensionOptionRepository linerDimensionOptionRepository;
    private final LinerDimensionTypeRepository linerDimensionTypeRepository;
    private final DimensionSurchargeRuleRepository dimensionSurchargeRuleRepository;
    private final ColourOptionRepository colourOptionRepository;
    private final ConfigurationPriceRepository configurationPriceRepository;
    private final FramePostRepository framePostRepository;

    // Читается PricingSurchargesController для отображения процента надбавки фронтенду
    // (см. change redesign-door-configurator-flow) — не используется и не меняет calculate().
    public BigDecimal reverseSurchargePercent() {
        return REVERSE_SURCHARGE_MULTIPLIER.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100));
    }

    @Transactional(readOnly = true)
    public PricingResponseDto calculate(Long doorConfigurationId, PricingRequestDto request) {
        DoorConfiguration configuration = doorConfigurationRepository.findById(doorConfigurationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "door_configuration с id=" + doorConfigurationId + " не найдена"));

        ComponentSelectionDto leafSelection = selectionOf(request, PricingRequestDto::leaf);
        LinerDimensionOption leafHeightOption =
                validatedDimensionOption("leaf", configuration.getLeafType(), leafSelection.heightOptionId());
        // Значение высоты полотна нужно для сверки диапазона кромки независимо от того, выбрана ли
        // каталожная опция или введено произвольное значение (см. change add-dimension-surcharge-rules).
        BigDecimal leafHeightValue = leafHeightOption != null ? leafHeightOption.getValue() : leafSelection.customHeightValueMm();
        boolean reverseFrameSelected = configuration.isReverse();

        List<ComponentPriceDto> components = new ArrayList<>();
        addComponentIfPresent(components, "leaf", configuration.getLeafType(), leafSelection, leafHeightValue, reverseFrameSelected);
        addComponentIfPresent(components, "frame", configuration.getFrameType(), selectionOf(request, PricingRequestDto::frame), leafHeightValue, false);
        addComponentIfPresent(components, "edge", configuration.getEdgeType(), selectionOf(request, PricingRequestDto::edge), leafHeightValue, false);
        addComponentIfPresent(components, "doorCasing", configuration.getDoorCasingType(), selectionOf(request, PricingRequestDto::doorCasing), leafHeightValue, false);
        addComponentIfPresent(components, "frameExtensions", configuration.getFrameExtensionsType(), selectionOf(request, PricingRequestDto::frameExtensions), leafHeightValue, false);

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
            BigDecimal leafHeightValue, boolean applyReverseSurcharge) {
        if (type == null) {
            return;
        }

        if ((selection.customLengthValueMm() != null || selection.customHeightValueMm() != null) && !(type instanceof LeafType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "произвольное значение размера допустимо только для компонента leaf, а не для " + componentName);
        }

        int quantity = resolveQuantity(componentName, type, selection.quantity());

        if (type instanceof FrameType frameType) {
            ColourOption colourOption = validatedColourOption(componentName, frameType, selection.colourOptionId());
            components.add(framePostPrice(componentName, frameType, colourOption));
            return;
        }

        LeafDimensionSurcharge leafDimensionSurcharge = type instanceof LeafType leafType
                ? resolveLeafDimensionSurcharge(componentName, leafType, selection)
                : LeafDimensionSurcharge.NONE;

        LinerDimensionOption lengthOption = validatedDimensionOption(componentName, type, selection.lengthOptionId());
        // У leaf-компонента высота не сопоставляется с каталожной опцией напрямую (см. leafHeightValue выше) —
        // ни одна цена полотна не фильтрует по height_option_id, поэтому для поиска цены она не нужна.
        LinerDimensionOption heightOption = type instanceof LeafType
                ? null
                : validatedDimensionOption(componentName, type, selection.heightOptionId());
        LinerDimensionOption thicknessOption = validatedDimensionOption(componentName, type, selection.thicknessOptionId());
        ColourOption colourOption = validatedColourOption(componentName, type, selection.colourOptionId());

        if (type instanceof EdgeType && heightOption != null) {
            validateHeightWithinLeafRange(componentName, heightOption, leafHeightValue);
        }

        Optional<ConfigurationPrice> matched = findMostSpecificPrice(type, lengthOption, heightOption, thicknessOption, colourOption);
        components.add(matched
                .map(price -> componentPriceFrom(componentName, price, leafDimensionSurcharge, applyReverseSurcharge, quantity))
                .orElseGet(() -> new ComponentPriceDto(componentName, false, null, null, null, null)));
    }

    // Количество применимо только к doorCasing/frameExtensions (см. change add-casing-extensions-quantity);
    // для остальных компонентов (включая frame, у которого своя проверка в вызывающем коде) переданное
    // количество — ошибка. Отсутствие количества равносильно 1 — сохраняет расчёт как за одну единицу.
    private int resolveQuantity(String componentName, CatalogType type, Integer quantity) {
        boolean quantityEligible = type instanceof DoorCasingType || type instanceof FrameExtensionsType;
        if (quantity != null && !quantityEligible) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "количество допустимо только для компонентов doorCasing и frameExtensions, а не для " + componentName);
        }
        if (quantity == null) {
            return 1;
        }
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "количество для компонента " + componentName + " должно быть положительным числом");
        }
        return quantity;
    }

    // Надбавки применяются строго последовательно, с округлением после каждого шага (длина → высота → реверс),
    // а не единым перемножением коэффициентов — так итоговая цена зависит от порядка шагов, как того требует бизнес-логика
    // (см. change add-dimension-surcharge-rules, решение об отказе от коммутативной композиции). Количество
    // (см. change add-casing-extensions-quantity) умножает уже посчитанную (с надбавками) цену — для
    // компонентов, где количество вообще допустимо (doorCasing/frameExtensions), надбавок никогда нет,
    // поэтому порядок «сначала надбавки, потом количество» не имеет практического значения.
    // price.getRetailPrice()/getDealerPrice() — цена компонента до применения этих наценок; умноженная на
    // количество, она передаётся в ответе как baseRetailPrice/baseDealerPrice (см. change
    // redesign-door-configurator-flow), чтобы фронтенд мог показать её рядом с итоговой ценой компонента.
    private ComponentPriceDto componentPriceFrom(
            String componentName, ConfigurationPrice price, LeafDimensionSurcharge leafDimensionSurcharge,
            boolean applyReverseSurcharge, int quantity) {
        BigDecimal quantityMultiplier = BigDecimal.valueOf(quantity);
        BigDecimal retailPrice =
                applySequentialSurcharges(price.getRetailPrice(), leafDimensionSurcharge, applyReverseSurcharge).multiply(quantityMultiplier);
        BigDecimal dealerPrice =
                applySequentialSurcharges(price.getDealerPrice(), leafDimensionSurcharge, applyReverseSurcharge).multiply(quantityMultiplier);
        BigDecimal baseRetailPrice = price.getRetailPrice().multiply(quantityMultiplier);
        BigDecimal baseDealerPrice = price.getDealerPrice().multiply(quantityMultiplier);
        return new ComponentPriceDto(componentName, true, retailPrice, dealerPrice, baseRetailPrice, baseDealerPrice);
    }

    private BigDecimal applySequentialSurcharges(
            BigDecimal price, LeafDimensionSurcharge leafDimensionSurcharge, boolean applyReverseSurcharge) {
        BigDecimal result = applyPercentMultiplier(price, leafDimensionSurcharge.lengthMultiplier());
        result = applyPercentMultiplier(result, leafDimensionSurcharge.heightMultiplier());
        if (applyReverseSurcharge) {
            result = applyReverseSurcharge(result);
        }
        return result;
    }

    private BigDecimal applyReverseSurcharge(BigDecimal price) {
        return applyPercentMultiplier(price, REVERSE_SURCHARGE_MULTIPLIER);
    }

    private BigDecimal applyPercentMultiplier(BigDecimal price, BigDecimal multiplier) {
        if (multiplier.compareTo(BigDecimal.ONE) == 0) {
            return price;
        }
        return price.multiply(multiplier).setScale(0, RoundingMode.HALF_UP);
    }

    // Разрешает произвольные значения длины/высоты полотна (customLengthValueMm/customHeightValueMm) в наценку
    // от бизнес-правил фабрики (dimension_surcharge_rule, см. change add-dimension-surcharge-rules) — отдельно
    // по каждой оси, т.к. они применяются к цене последовательно, а не одним объединённым множителем.
    private LeafDimensionSurcharge resolveLeafDimensionSurcharge(String componentName, LeafType leafType, ComponentSelectionDto selection) {
        BigDecimal lengthMultiplier = resolveAxisSurchargeMultiplier(
                componentName, leafType, LENGTH_TYPE_CODE, selection.lengthOptionId(), selection.customLengthValueMm());
        BigDecimal heightMultiplier = resolveAxisSurchargeMultiplier(
                componentName, leafType, HEIGHT_TYPE_CODE, selection.heightOptionId(), selection.customHeightValueMm());
        return new LeafDimensionSurcharge(lengthMultiplier, heightMultiplier);
    }

    private record LeafDimensionSurcharge(BigDecimal lengthMultiplier, BigDecimal heightMultiplier) {
        static final LeafDimensionSurcharge NONE = new LeafDimensionSurcharge(BigDecimal.ONE, BigDecimal.ONE);
    }

    private BigDecimal resolveAxisSurchargeMultiplier(
            String componentName, LeafType leafType, String dimensionTypeCode, Long optionId, BigDecimal customValue) {
        if (customValue == null) {
            return BigDecimal.ONE;
        }
        if (optionId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "для компонента " + componentName
                            + " нельзя одновременно указать id каталожной опции размера и произвольное значение");
        }

        boolean matchesStandardSize = linerDimensionOptionRepository.findByLeafTypeId(leafType.getId()).stream()
                .anyMatch(option -> dimensionTypeCode.equals(option.getLinerDimensionType().getCode())
                        && option.getValue().compareTo(customValue) == 0);
        if (matchesStandardSize) {
            return BigDecimal.ONE;
        }

        LinerDimensionType dimensionType = linerDimensionTypeRepository.findByCode(dimensionTypeCode)
                .orElseThrow(() -> new IllegalStateException("liner_dimension_type с кодом " + dimensionTypeCode + " не найден"));
        DimensionSurchargeRule rule = dimensionSurchargeRuleRepository
                .findByLinerDimensionTypeIdAndValue(dimensionType.getId(), customValue)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "фабрика не производит полотно с размером " + customValue + " мм для этой оси"));
        return BigDecimal.ONE.add(rule.getSurchargePercent().divide(BigDecimal.valueOf(100)));
    }

    private void validateHeightWithinLeafRange(
            String componentName, LinerDimensionOption edgeHeightOption, BigDecimal leafHeightValue) {
        if (!HEIGHT_TYPE_CODE.equals(edgeHeightOption.getLinerDimensionType().getCode())) {
            return;
        }
        if (leafHeightValue == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "для компонента " + componentName + " выбрана высота, но высота полотна не выбрана");
        }
        BigDecimal min = edgeHeightOption.getMinValue();
        BigDecimal max = edgeHeightOption.getValue();
        boolean withinRange = (min == null || leafHeightValue.compareTo(min) >= 0) && leafHeightValue.compareTo(max) <= 0;
        if (!withinRange) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "liner_dimension_option с id=" + edgeHeightOption.getId() + " не совместима с высотой полотна");
        }
    }

    // Короб не участвует ни в одной наценке (только полотно) — baseRetailPrice/baseDealerPrice
    // всегда совпадают с итоговой ценой этого компонента (см. change redesign-door-configurator-flow).
    private ComponentPriceDto framePostPrice(String componentName, FrameType frameType, ColourOption colourOption) {
        List<FramePost> posts = framePostRepository.findByFrameTypeId(frameType.getId());
        BigDecimal postsRetailPrice = posts.stream().map(FramePost::getRetailPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal postsDealerPrice = posts.stream().map(FramePost::getDealerPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ColourOption> availableColours = colourOptionRepository.findByFrameTypeId(frameType.getId());
        if (availableColours.isEmpty()) {
            if (posts.isEmpty()) {
                return new ComponentPriceDto(componentName, false, null, null, null, null);
            }
            return new ComponentPriceDto(componentName, true, postsRetailPrice, postsDealerPrice, postsRetailPrice, postsDealerPrice);
        }

        Optional<ConfigurationPrice> matched = findMostSpecificPrice(frameType, null, null, null, colourOption);
        return matched
                .map(price -> {
                    BigDecimal retailPrice = postsRetailPrice.add(price.getRetailPrice());
                    BigDecimal dealerPrice = postsDealerPrice.add(price.getDealerPrice());
                    return new ComponentPriceDto(componentName, true, retailPrice, dealerPrice, retailPrice, dealerPrice);
                })
                .orElseGet(() -> new ComponentPriceDto(componentName, false, null, null, null, null));
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
