package com.example.furniturecalculator.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
import com.example.furniturecalculator.domain.MirrorFinishOption;
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
import com.example.furniturecalculator.repository.MirrorFinishOptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoorConfigurationPricingService {

    // Коды типов размера из справочника liner_dimension_type (см. db.changelog 0004) — стабильные бизнес-ключи.
    private static final String LENGTH_TYPE_CODE = "DT-001";
    private static final String HEIGHT_TYPE_CODE = "DT-002";

    // Коды frame_type (см. db.changelog 0004), для которых высота короба ограничена диапазоном высоты
    // полотна: «НЕО» (см. change link-frame-neo-height-to-leaf-height) и «Компланар»
    // (см. change link-komplanar-height-to-leaf-height). Остальные типы короба (например, «Фантом»
    // FT-001) этому правилу не подчиняются.
    private static final Set<String> HEIGHT_RANGE_FRAME_TYPE_CODES = Set.of("FT-002", "FT-003");

    // Коды frame_type, для которых высота короба не выбирается из каталога, а всегда в точности равна
    // высоте полотна той же конфигурации (см. change mirror-fantom-frame-height-to-leaf-height) — короб
    // «Фантом» (FT-001) физически не имеет каталожных liner_dimension_option ни на одной оси. В отличие
    // от HEIGHT_RANGE_FRAME_TYPE_CODES, здесь нет диапазона: значение должно совпадать с высотой полотна
    // в точности.
    private static final Set<String> HEIGHT_MIRROR_FRAME_TYPE_CODES = Set.of("FT-001");

    // Коды frame_extensions_type добора, для которых длина ограничена диапазоном высоты полотна —
    // добор «ТС» (см. change link-dobor-ts-length-to-leaf-height) и добор «КОМПЛАНАР» (все 6 ширин,
    // см. change link-komplanar-dobor-length-to-leaf-height) — по тому же принципу, что и высота короба
    // из HEIGHT_RANGE_FRAME_TYPE_CODES, но на оси «Длина». Это все 10 реально достижимых через каталог
    // кодов frame_extensions_type — коды FET-001–FET-003 (категорийные заголовки) ни разу не связаны
    // ни с одной door_configuration и недостижимы через UI.
    private static final Set<String> LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES = Set.of(
            "FET-004", "FET-005", "FET-006", "FET-007",
            "FET-008", "FET-009", "FET-010", "FET-011", "FET-012", "FET-013");

    // Коды door_casing_type наличников, для которых длина ограничена диапазоном высоты полотна —
    // «Модо»/«Онда» (см. change link-modo-onda-casing-length-to-leaf-height) и наличники короба
    // «Компланар»: «Эво», «Авеню»/«Авеню-реверс», «Аура»/«Аура-реверс», «Ария»/«Ария-реверс»
    // (см. change link-komplanar-casing-length-to-leaf-height) — по тому же принципу, что и
    // LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES. Это все 9 существующих кодов door_casing_type —
    // любой новый наличник, добавленный в будущем, потребует явного решения, входить ли в этот набор.
    private static final Set<String> LENGTH_RANGE_DOOR_CASING_TYPE_CODES = Set.of(
            "DCT-001", "DCT-002", "DCT-003", "DCT-004", "DCT-005", "DCT-006", "DCT-007", "DCT-008", "DCT-009");

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
    private final MirrorFinishOptionRepository mirrorFinishOptionRepository;

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

        if (selection.customLengthValueMm() != null && !(type instanceof LeafType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "произвольное значение размера допустимо только для компонента leaf, а не для " + componentName);
        }
        // Произвольное значение высоты допустимо также для короба «Фантом» (см.
        // HEIGHT_MIRROR_FRAME_TYPE_CODES) — только для высоты, не для длины (проверка выше).
        boolean customHeightAllowedForFrame =
                type instanceof FrameType frameType && HEIGHT_MIRROR_FRAME_TYPE_CODES.contains(frameType.getCode());
        if (selection.customHeightValueMm() != null && !(type instanceof LeafType) && !customHeightAllowedForFrame) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "произвольное значение размера допустимо только для компонента leaf, а не для " + componentName);
        }

        int quantity = resolveQuantity(componentName, type, selection.quantity());
        BigDecimal mirrorFinishMultiplier = resolveMirrorFinishMultiplier(componentName, type, selection.mirrorFinishTypeId());

        if (type instanceof FrameType frameType) {
            ColourOption colourOption = validatedColourOption(componentName, frameType, selection.colourOptionId());
            // Высота короба сама по себе не влияет на цену (framePostPrice её не использует) — здесь она
            // только валидируется. Для коробов из HEIGHT_MIRROR_FRAME_TYPE_CODES (см. change
            // mirror-fantom-frame-height-to-leaf-height) высота не выбирается из каталога — её вообще нет
            // (validatedDimensionOption ниже отклонит любой переданный heightOptionId как непринадлежащий),
            // а обязана в точности совпадать с высотой полотна. Для коробов из HEIGHT_RANGE_FRAME_TYPE_CODES —
            // прежняя диапазонная проверка обязательности и совместимости.
            if (HEIGHT_MIRROR_FRAME_TYPE_CODES.contains(frameType.getCode())) {
                validatedDimensionOption(componentName, frameType, selection.heightOptionId());
                requireHeightMirrorsLeaf(componentName, selection.customHeightValueMm(), leafHeightValue);
            } else {
                // (см. change link-frame-neo-height-to-leaf-height, link-komplanar-height-to-leaf-height).
                if (HEIGHT_RANGE_FRAME_TYPE_CODES.contains(frameType.getCode()) && selection.heightOptionId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "для этого короба необходимо выбрать высоту");
                }
                LinerDimensionOption heightOption = validatedDimensionOption(componentName, frameType, selection.heightOptionId());
                if (heightOption != null && HEIGHT_RANGE_FRAME_TYPE_CODES.contains(frameType.getCode())) {
                    validateHeightWithinLeafRange(componentName, HEIGHT_TYPE_CODE, heightOption, leafHeightValue);
                }
            }
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
            validateHeightWithinLeafRange(componentName, HEIGHT_TYPE_CODE, heightOption, leafHeightValue);
        }

        // Длина добора «ТС»/наличников «Модо»/«Онда» сама по себе не влияет на цену (см. миграции 0067/0071,
        // length_option_id обнулён в configuration_price для этих кодов) — здесь она только валидируется на
        // принадлежность и, для кодов из соответствующего набора, на совместимость с высотой полотна. Для
        // таких компонентов длина обязательна: без этого требования отсутствие lengthOptionId тихо пропускало
        // бы проверку диапазона (см. change link-dobor-ts-length-to-leaf-height, link-modo-onda-casing-length-to-leaf-height,
        // по аналогии с обязательностью высоты для короба «НЕО»/«Компланар»).
        if (type instanceof FrameExtensionsType frameExtensionsType) {
            requireLengthWithinLeafRange(componentName, frameExtensionsType.getCode(), LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES,
                    "для этого добора необходимо выбрать длину", selection, lengthOption, leafHeightValue);
        }
        if (type instanceof DoorCasingType doorCasingType) {
            requireLengthWithinLeafRange(componentName, doorCasingType.getCode(), LENGTH_RANGE_DOOR_CASING_TYPE_CODES,
                    "для этого наличника необходимо выбрать длину", selection, lengthOption, leafHeightValue);
        }

        Optional<ConfigurationPrice> matched = findMostSpecificPrice(type, lengthOption, heightOption, thicknessOption, colourOption);
        components.add(matched
                .map(price -> componentPriceFrom(
                        componentName, price, leafDimensionSurcharge, mirrorFinishMultiplier, applyReverseSurcharge, quantity))
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

    // Исполнение зеркала допустимо только для leaf (см. change add-mirror-finish-leaf-option). Запрос ссылается
    // на mirror_finish_type.id (глобальный) — то же пространство id, что каталог и GET /api/pricing-surcharges,
    // а не на владение (mirror_finish_option.id), в отличие от colour_option/liner_dimension_option: наценка
    // не зависит от конкретной строки владения, только от типа, поэтому владение достаточно проверить, не выдавая
    // его id клиенту отдельно.
    private BigDecimal resolveMirrorFinishMultiplier(String componentName, CatalogType type, Long mirrorFinishTypeId) {
        if (mirrorFinishTypeId == null) {
            return BigDecimal.ONE;
        }
        if (!(type instanceof LeafType leafType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "исполнение зеркала допустимо только для компонента leaf, а не для " + componentName);
        }
        MirrorFinishOption option = mirrorFinishOptionRepository
                .findByMirrorFinishTypeIdAndLeafTypeId(mirrorFinishTypeId, leafType.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "исполнение зеркала с id=" + mirrorFinishTypeId + " недопустимо для этого полотна"));
        return BigDecimal.ONE.add(option.getMirrorFinishType().getSurchargePercent().divide(BigDecimal.valueOf(100)));
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
            BigDecimal mirrorFinishMultiplier, boolean applyReverseSurcharge, int quantity) {
        BigDecimal quantityMultiplier = BigDecimal.valueOf(quantity);
        BigDecimal retailPrice = applySequentialSurcharges(
                price.getRetailPrice(), leafDimensionSurcharge, mirrorFinishMultiplier, applyReverseSurcharge)
                .multiply(quantityMultiplier);
        BigDecimal dealerPrice = applySequentialSurcharges(
                price.getDealerPrice(), leafDimensionSurcharge, mirrorFinishMultiplier, applyReverseSurcharge)
                .multiply(quantityMultiplier);
        BigDecimal baseRetailPrice = price.getRetailPrice().multiply(quantityMultiplier);
        BigDecimal baseDealerPrice = price.getDealerPrice().multiply(quantityMultiplier);
        return new ComponentPriceDto(componentName, true, retailPrice, dealerPrice, baseRetailPrice, baseDealerPrice);
    }

    // Порядок шагов: длина → высота → исполнение зеркала → реверс (см. change add-mirror-finish-leaf-option) —
    // зеркало встаёт строго между высотой и реверсом, тем же принципом округления после каждого шага.
    private BigDecimal applySequentialSurcharges(
            BigDecimal price, LeafDimensionSurcharge leafDimensionSurcharge, BigDecimal mirrorFinishMultiplier,
            boolean applyReverseSurcharge) {
        BigDecimal result = applyPercentMultiplier(price, leafDimensionSurcharge.lengthMultiplier());
        result = applyPercentMultiplier(result, leafDimensionSurcharge.heightMultiplier());
        result = applyPercentMultiplier(result, mirrorFinishMultiplier);
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

    // expectedDimensionTypeCode — ось размера, для которой вызывающая сторона ожидает эту проверку
    // (HEIGHT_TYPE_CODE для кромки/короба, LENGTH_TYPE_CODE для добора «ТС», см. change
    // link-dobor-ts-length-to-leaf-height) — если у переданной опции другая ось, проверка не выполняется.
    private void validateHeightWithinLeafRange(
            String componentName, String expectedDimensionTypeCode, LinerDimensionOption dimensionOption,
            BigDecimal leafHeightValue) {
        if (!expectedDimensionTypeCode.equals(dimensionOption.getLinerDimensionType().getCode())) {
            return;
        }
        if (leafHeightValue == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "для компонента " + componentName + " выбран размер, ограниченный высотой полотна, но высота полотна не выбрана");
        }
        BigDecimal min = dimensionOption.getMinValue();
        // Верхняя граница диапазона — max_value, если задан явно (короб «НЕО», добор «ТС», см. change
        // link-frame-neo-height-to-leaf-height, link-dobor-ts-length-to-leaf-height), иначе value (кромка,
        // где физическая высота опции исторически совпадает с верхней границей диапазона).
        BigDecimal max = dimensionOption.getMaxValue() != null ? dimensionOption.getMaxValue() : dimensionOption.getValue();
        boolean withinRange = (min == null || leafHeightValue.compareTo(min) >= 0) && leafHeightValue.compareTo(max) <= 0;
        if (!withinRange) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "liner_dimension_option с id=" + dimensionOption.getId() + " не совместима с высотой полотна");
        }
    }

    // Общий паттерн «длина обязательна + совместима с диапазоном высоты полотна» для владельцев из общей
    // (не-FrameType) ветки addComponentIfPresent — сейчас добор «ТС» и наличники «Модо»/«Онда» (см. change
    // link-modo-onda-casing-length-to-leaf-height, вынесено из блока, изначально писавшегося только для
    // добора «ТС» в link-dobor-ts-length-to-leaf-height). lengthRangeCodes/missingLengthMessage параметризуют
    // то немногое, что отличается между владельцами; сама проверка (обязательность + диапазон) идентична.
    private void requireLengthWithinLeafRange(
            String componentName, String ownerCode, Set<String> lengthRangeCodes, String missingLengthMessage,
            ComponentSelectionDto selection, LinerDimensionOption lengthOption, BigDecimal leafHeightValue) {
        if (!lengthRangeCodes.contains(ownerCode)) {
            return;
        }
        if (selection.lengthOptionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, missingLengthMessage);
        }
        if (lengthOption != null) {
            validateHeightWithinLeafRange(componentName, LENGTH_TYPE_CODE, lengthOption, leafHeightValue);
        }
    }

    // Высота короба «Фантом» (HEIGHT_MIRROR_FRAME_TYPE_CODES) не выбирается из диапазона каталожных
    // опций, как у «НЕО»/«Компланар», а обязана в точности совпадать с высотой полотна (см. change
    // mirror-fantom-frame-height-to-leaf-height) — числовое равенство, а не проверка диапазона.
    private void requireHeightMirrorsLeaf(String componentName, BigDecimal customHeightValueMm, BigDecimal leafHeightValue) {
        if (customHeightValueMm == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "для этого короба необходимо выбрать высоту");
        }
        if (leafHeightValue == null || customHeightValueMm.compareTo(leafHeightValue) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "высота короба должна совпадать с высотой полотна");
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
