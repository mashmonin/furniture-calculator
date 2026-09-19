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
import com.example.furniturecalculator.domain.GlazingOption;
import com.example.furniturecalculator.domain.HardwareOption;
import com.example.furniturecalculator.domain.LeafType;
import com.example.furniturecalculator.domain.LinerDimensionOption;
import com.example.furniturecalculator.domain.LinerDimensionType;
import com.example.furniturecalculator.domain.MirrorFinishOption;
import com.example.furniturecalculator.domain.PogonazhSurchargeRule;
import com.example.furniturecalculator.dto.ComponentPriceDto;
import com.example.furniturecalculator.dto.ComponentSelectionDto;
import com.example.furniturecalculator.dto.FrameGroupPricingRequestDto;
import com.example.furniturecalculator.dto.FrameGroupPricingResponseDto;
import com.example.furniturecalculator.dto.HardwarePriceDto;
import com.example.furniturecalculator.dto.HardwarePricingRequestDto;
import com.example.furniturecalculator.dto.HardwarePricingResponseDto;
import com.example.furniturecalculator.dto.HardwareSelectionDto;
import com.example.furniturecalculator.dto.PricingRequestDto;
import com.example.furniturecalculator.dto.PricingResponseDto;
import com.example.furniturecalculator.dto.ReferenceDto;
import com.example.furniturecalculator.dto.SpecificationExportRequestDto;
import com.example.furniturecalculator.repository.ColourOptionRepository;
import com.example.furniturecalculator.repository.ConfigurationPriceRepository;
import com.example.furniturecalculator.repository.DimensionSurchargeRuleRepository;
import com.example.furniturecalculator.repository.DoorCasingTypeRepository;
import com.example.furniturecalculator.repository.DoorConfigurationRepository;
import com.example.furniturecalculator.repository.EdgeTypeRepository;
import com.example.furniturecalculator.repository.FrameExtensionsTypeRepository;
import com.example.furniturecalculator.repository.FramePostRepository;
import com.example.furniturecalculator.repository.FrameTypeRepository;
import com.example.furniturecalculator.repository.GlazingOptionRepository;
import com.example.furniturecalculator.repository.HardwareOptionRepository;
import com.example.furniturecalculator.repository.LeafTypeRepository;
import com.example.furniturecalculator.repository.LinerDimensionOptionRepository;
import com.example.furniturecalculator.repository.LinerDimensionTypeRepository;
import com.example.furniturecalculator.repository.MirrorFinishOptionRepository;
import com.example.furniturecalculator.repository.PogonazhSurchargeRuleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoorConfigurationPricingService {

    // Коды типов размера из справочника liner_dimension_type (см. db.changelog 0004) — стабильные бизнес-ключи.
    private static final String LENGTH_TYPE_CODE = "DT-001";
    private static final String HEIGHT_TYPE_CODE = "DT-002";

    // Каскадная наценка за промежуточные значения сетки 50мм высоты полотна (см. change
    // add-leaf-height-cascade-surcharge-50mm-grid) — 1900мм наименьшая заведённая точка, потолка нет;
    // MAX_CASCADE_STEPS — не бизнес-ограничение, а защита от неограниченного цикла запросов к репозиторию
    // при аномально большом клиентском значении (200 шагов ~ +10 метров от 1900мм).
    private static final BigDecimal HEIGHT_GRID_FLOOR = BigDecimal.valueOf(1900);
    private static final BigDecimal HEIGHT_GRID_STEP = BigDecimal.valueOf(50);
    private static final BigDecimal CASCADE_STEP_PERCENT = BigDecimal.valueOf(20);
    private static final int MAX_CASCADE_STEPS = 200;

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
    private final PogonazhSurchargeRuleRepository pogonazhSurchargeRuleRepository;
    private final ColourOptionRepository colourOptionRepository;
    private final ConfigurationPriceRepository configurationPriceRepository;
    private final FramePostRepository framePostRepository;
    private final MirrorFinishOptionRepository mirrorFinishOptionRepository;
    private final GlazingOptionRepository glazingOptionRepository;
    private final HardwareOptionRepository hardwareOptionRepository;
    private final LeafTypeRepository leafTypeRepository;
    private final EdgeTypeRepository edgeTypeRepository;
    private final FrameTypeRepository frameTypeRepository;
    private final DoorCasingTypeRepository doorCasingTypeRepository;
    private final FrameExtensionsTypeRepository frameExtensionsTypeRepository;

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

        return finalizeResponse(components, request);
    }

    // Расчёт стоимости одного полотна (leaf_type), опционально вместе с кромкой (см. change
    // add-staged-pricing-endpoints), в отрыве от door_configuration — например, пока каскад выбора на
    // фронтенде ещё не определил конкретную согласованную конфигурацию с коробом и др. (см. change
    // add-standalone-leaf-pricing). Переиспользует тот же подбор цены и те же надбавки за размер/исполнение
    // зеркала, что и для leaf-компонента внутри calculate(); надбавка за реверс не применяется — она
    // свойство короба/портала конкретной door_configuration, а не самого полотна.
    @Transactional(readOnly = true)
    public PricingResponseDto calculateForLeaf(Long leafTypeId, PricingRequestDto request) {
        LeafType leafType = leafTypeRepository.findById(leafTypeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "leaf_type с id=" + leafTypeId + " не найден"));

        ComponentSelectionDto leafSelection = selectionOf(request, PricingRequestDto::leaf);
        // Высота полотна нужна не только для наценки за размер leaf (внутри addComponentIfPresent), но и
        // для проверки диапазона кромки, если она выбрана — вычисляется тем же способом, что и в calculate()
        // (см. change add-staged-pricing-endpoints).
        LinerDimensionOption leafHeightOption = validatedDimensionOption("leaf", leafType, leafSelection.heightOptionId());
        BigDecimal leafHeightValue = leafHeightOption != null ? leafHeightOption.getValue() : leafSelection.customHeightValueMm();
        // У отдельного полотна нет door_configuration.is_reverse — клиент передаёт признак реверса
        // явно (см. change add-standalone-leaf-pricing); отсутствие поля равносильно false.
        boolean applyReverseSurcharge = request != null && Boolean.TRUE.equals(request.isReverse());
        List<ComponentPriceDto> components = new ArrayList<>();
        addComponentIfPresent(components, "leaf", leafType, leafSelection, leafHeightValue, applyReverseSurcharge);

        Long edgeTypeId = request != null ? request.edgeTypeId() : null;
        if (edgeTypeId != null) {
            // Как и у mirror_finish_type/glazing_type, «id не существует» и «существует, но недопустим для
            // этого полотна» не различаются — единая проверка существования пары даёт единый ответ 400
            // (см. change add-staged-pricing-endpoints).
            if (!doorConfigurationRepository.existsByLeafTypeIdAndEdgeTypeId(leafTypeId, edgeTypeId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "вид кромки с id=" + edgeTypeId + " недопустим для этого полотна");
            }
            EdgeType edgeType = edgeTypeRepository.findById(edgeTypeId).orElseThrow();
            addComponentIfPresent(components, "edge", edgeType, selectionOf(request, PricingRequestDto::edge), leafHeightValue, false);
        }

        return finalizeResponse(components, request);
    }

    // Расчёт стоимости короба и, опционально, наличника/добора независимо от полотна, кромки и
    // фурнитуры (см. change add-staged-pricing-endpoints) — переиспользует тот же addComponentIfPresent,
    // что и calculate()/calculateForLeaf(); leafHeightValue берётся из запроса явно, а не вычисляется из
    // опций полотна (у этого эндпоинта их нет).
    @Transactional(readOnly = true)
    public FrameGroupPricingResponseDto calculateForFrameGroup(Long frameTypeId, FrameGroupPricingRequestDto request) {
        FrameType frameType = frameTypeRepository.findById(frameTypeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "frame_type с id=" + frameTypeId + " не найден"));

        BigDecimal leafHeightValue = request != null ? request.leafHeightValue() : null;
        ComponentSelectionDto frameSelection =
                request != null && request.frame() != null ? request.frame() : ComponentSelectionDto.EMPTY;

        List<ComponentPriceDto> components = new ArrayList<>();
        addComponentIfPresent(components, "frame", frameType, frameSelection, leafHeightValue, false);

        Long doorCasingTypeId = request != null ? request.doorCasingTypeId() : null;
        if (doorCasingTypeId != null) {
            // Единая проверка существования пары, как у leaf/edge (см. calculateForLeaf) — не различает
            // «id не существует» и «недопустим для этого короба».
            if (!doorConfigurationRepository.existsByFrameTypeIdAndDoorCasingTypeId(frameTypeId, doorCasingTypeId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "наличник с id=" + doorCasingTypeId + " недопустим для этого короба");
            }
            DoorCasingType doorCasingType = doorCasingTypeRepository.findById(doorCasingTypeId).orElseThrow();
            ComponentSelectionDto doorCasingSelection =
                    request.doorCasing() != null ? request.doorCasing() : ComponentSelectionDto.EMPTY;
            addComponentIfPresent(components, "doorCasing", doorCasingType, doorCasingSelection, leafHeightValue, false);
        }

        Long frameExtensionsTypeId = request != null ? request.frameExtensionsTypeId() : null;
        if (frameExtensionsTypeId != null) {
            if (!doorConfigurationRepository.existsByFrameTypeIdAndFrameExtensionsTypeId(frameTypeId, frameExtensionsTypeId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "добор с id=" + frameExtensionsTypeId + " недопустим для этого короба");
            }
            FrameExtensionsType frameExtensionsType = frameExtensionsTypeRepository.findById(frameExtensionsTypeId).orElseThrow();
            ComponentSelectionDto frameExtensionsSelection =
                    request.frameExtensions() != null ? request.frameExtensions() : ComponentSelectionDto.EMPTY;
            addComponentIfPresent(components, "frameExtensions", frameExtensionsType, frameExtensionsSelection, leafHeightValue, false);
        }

        return new FrameGroupPricingResponseDto(sumRetail(components), sumDealer(components), components);
    }

    // Расчёт стоимости произвольного списка позиций фурнитуры независимо от door_configuration и её
    // компонентов (см. change add-staged-pricing-endpoints) — переиспользует тот же priceHardwareSelections,
    // что и finalizeResponse().
    @Transactional(readOnly = true)
    public HardwarePricingResponseDto calculateHardware(HardwarePricingRequestDto request) {
        List<HardwarePriceDto> hardware = priceHardwareSelections(
                request != null && request.hardware() != null ? request.hardware() : List.of());
        BigDecimal totalRetail = hardware.stream().map(HardwarePriceDto::retailPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDealer = hardware.stream().map(HardwarePriceDto::dealerPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HardwarePricingResponseDto(totalRetail, totalDealer, hardware);
    }

    // Полный резолв конфигурации для выгрузки спецификации (см. change add-specification-export) —
    // переиспользует тот же addComponentIfPresent (теперь возвращающий ResolvedComponent, см. change
    // add-specification-export) и тот же priceHardwareSelections, что и три этапных эндпоинта расчёта,
    // вместо повторной реализации валидации/подбора цены отдельным путём. leaf обязателен (проверяется
    // раньше — в контроллере уровня DTO/валидации не выполняется, см. SpecificationExportController);
    // здесь просто 404, если leaf_type не указан или не найден — та же семантика, что у calculateForLeaf.
    @Transactional(readOnly = true)
    SpecificationComponents resolveSpecificationComponents(SpecificationExportRequestDto request) {
        Long leafTypeId = request.leafTypeId();
        if (leafTypeId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "leaf_type не указан");
        }
        LeafType leafType = leafTypeRepository.findById(leafTypeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "leaf_type с id=" + leafTypeId + " не найден"));

        ComponentSelectionDto leafSelection = request.leaf() != null ? request.leaf() : ComponentSelectionDto.EMPTY;
        LinerDimensionOption leafHeightOption = validatedDimensionOption("leaf", leafType, leafSelection.heightOptionId());
        BigDecimal leafHeightValue = leafHeightOption != null ? leafHeightOption.getValue() : leafSelection.customHeightValueMm();
        boolean applyReverseSurcharge = Boolean.TRUE.equals(request.isReverse());

        List<ComponentPriceDto> scratch = new ArrayList<>();
        ResolvedComponent leafResolved = addComponentIfPresent(scratch, "leaf", leafType, leafSelection, leafHeightValue, applyReverseSurcharge);

        ResolvedComponent edgeResolved = null;
        Long edgeTypeId = request.edgeTypeId();
        if (edgeTypeId != null) {
            if (!doorConfigurationRepository.existsByLeafTypeIdAndEdgeTypeId(leafTypeId, edgeTypeId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "вид кромки с id=" + edgeTypeId + " недопустим для этого полотна");
            }
            EdgeType edgeType = edgeTypeRepository.findById(edgeTypeId).orElseThrow();
            ComponentSelectionDto edgeSelection = request.edge() != null ? request.edge() : ComponentSelectionDto.EMPTY;
            edgeResolved = addComponentIfPresent(scratch, "edge", edgeType, edgeSelection, leafHeightValue, false);
        }

        ResolvedComponent frameResolved = null;
        Long frameTypeId = request.frameTypeId();
        if (frameTypeId != null) {
            FrameType frameType = frameTypeRepository.findById(frameTypeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "frame_type с id=" + frameTypeId + " не найден"));
            ComponentSelectionDto frameSelection = request.frame() != null ? request.frame() : ComponentSelectionDto.EMPTY;
            frameResolved = addComponentIfPresent(scratch, "frame", frameType, frameSelection, request.leafHeightValue(), false);
        }

        ResolvedComponent doorCasingResolved = null;
        Long doorCasingTypeId = request.doorCasingTypeId();
        if (doorCasingTypeId != null) {
            if (frameTypeId == null || !doorConfigurationRepository.existsByFrameTypeIdAndDoorCasingTypeId(frameTypeId, doorCasingTypeId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "наличник с id=" + doorCasingTypeId + " недопустим для этого короба");
            }
            DoorCasingType doorCasingType = doorCasingTypeRepository.findById(doorCasingTypeId).orElseThrow();
            ComponentSelectionDto doorCasingSelection = request.doorCasing() != null ? request.doorCasing() : ComponentSelectionDto.EMPTY;
            doorCasingResolved = addComponentIfPresent(scratch, "doorCasing", doorCasingType, doorCasingSelection, request.leafHeightValue(), false);
        }

        ResolvedComponent frameExtensionsResolved = null;
        Long frameExtensionsTypeId = request.frameExtensionsTypeId();
        if (frameExtensionsTypeId != null) {
            if (frameTypeId == null
                    || !doorConfigurationRepository.existsByFrameTypeIdAndFrameExtensionsTypeId(frameTypeId, frameExtensionsTypeId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "добор с id=" + frameExtensionsTypeId + " недопустим для этого короба");
            }
            FrameExtensionsType frameExtensionsType = frameExtensionsTypeRepository.findById(frameExtensionsTypeId).orElseThrow();
            ComponentSelectionDto frameExtensionsSelection =
                    request.frameExtensions() != null ? request.frameExtensions() : ComponentSelectionDto.EMPTY;
            frameExtensionsResolved =
                    addComponentIfPresent(scratch, "frameExtensions", frameExtensionsType, frameExtensionsSelection, request.leafHeightValue(), false);
        }

        List<HardwarePriceDto> hardware = priceHardwareSelections(request.hardware() != null ? request.hardware() : List.of());

        return new SpecificationComponents(
                leafResolved, leafHeightValue, edgeResolved, frameResolved, doorCasingResolved, frameExtensionsResolved, hardware);
    }

    // Итоговые суммы и фурнитура не зависят от того, найдены ли компоненты через door_configuration
    // или напрямую по leaf_type — общий хвост для calculate() и calculateForLeaf() (см. change
    // add-standalone-leaf-pricing, design.md).
    private PricingResponseDto finalizeResponse(List<ComponentPriceDto> components, PricingRequestDto request) {
        // Фурнитура не привязана к door_configuration и не участвует в надбавках компонентов —
        // прибавляется к итогу последним слагаемым (см. change add-hardware-catalog, design.md).
        List<HardwarePriceDto> hardware = priceHardwareSelections(
                request != null && request.hardware() != null ? request.hardware() : List.of());
        BigDecimal hardwareRetailTotal = hardware.stream()
                .map(HardwarePriceDto::retailPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal hardwareDealerTotal = hardware.stream()
                .map(HardwarePriceDto::dealerPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PricingResponseDto(
                sumRetail(components).add(hardwareRetailTotal), sumDealer(components).add(hardwareDealerTotal), components, hardware);
    }

    // Общие суммы разбивки по компонентам — переиспользуются finalizeResponse() (calculate()/
    // calculateForLeaf()) и calculateForFrameGroup() (см. change add-staged-pricing-endpoints).
    private static BigDecimal sumRetail(List<ComponentPriceDto> components) {
        return components.stream()
                .filter(ComponentPriceDto::priced)
                .map(ComponentPriceDto::retailPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumDealer(List<ComponentPriceDto> components) {
        return components.stream()
                .filter(ComponentPriceDto::priced)
                .map(ComponentPriceDto::dealerPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Позиции не объединяются: один и тот же hardware_option может повторяться несколькими независимыми
    // строками (см. change add-hardware-catalog, design.md — «Список позиций фурнитуры и формула суммы»).
    private List<HardwarePriceDto> priceHardwareSelections(List<HardwareSelectionDto> selections) {
        return selections.stream().map(this::priceHardwareSelection).toList();
    }

    private HardwarePriceDto priceHardwareSelection(HardwareSelectionDto selection) {
        HardwareOption option = hardwareOptionRepository.findById(selection.hardwareOptionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "hardware_option с id=" + selection.hardwareOptionId() + " не найден"));
        Integer quantity = selection.quantity();
        if (quantity != null && quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "количество для позиции фурнитуры должно быть положительным числом");
        }
        int resolvedQuantity = quantity != null ? quantity : 1;
        BigDecimal quantityMultiplier = BigDecimal.valueOf(resolvedQuantity);
        return new HardwarePriceDto(
                ReferenceDto.from(option.getHardwareType().getHardwareCategory()),
                ReferenceDto.from(option.getHardwareType()),
                option.getColourName(),
                resolvedQuantity,
                option.getRetailPrice().multiply(quantityMultiplier),
                option.getDealerPrice().multiply(quantityMultiplier));
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

    // Возвращает не только добавленный в components ComponentPriceDto (как и раньше — единственное, что
    // используют calculate()/calculateForLeaf()/calculateForFrameGroup()), но и объекты, использованные для
    // его резолва — нужны только выгрузке спецификации (см. resolveSpecificationComponents, change
    // add-specification-export), чтобы не резолвить их заново отдельным путём и не дублировать эту
    // валидацию/подбор цены. Существующие три вызывающих места продолжают вызывать этот метод как
    // выражение-оператор, не читая возврат, — их поведение не меняется.
    private ResolvedComponent addComponentIfPresent(
            List<ComponentPriceDto> components, String componentName, CatalogType type, ComponentSelectionDto selection,
            BigDecimal leafHeightValue, boolean applyReverseSurcharge) {
        if (type == null) {
            return null;
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
        OptionSurcharge mirrorFinish = resolveMirrorFinishMultiplier(componentName, type, selection.mirrorFinishTypeId());
        OptionSurcharge glazing = resolveGlazingMultiplier(componentName, type, selection.glazingTypeId());

        if (type instanceof FrameType frameType) {
            ColourOption colourOption = validatedColourOption(componentName, frameType, selection.colourOptionId());
            // Высота короба сама по себе не влияет на цену (framePostPrice её не использует) — валидируется
            // и, где применимо, сохраняется в возврате (см. change add-specification-export — колонка
            // «Измерения» в выгрузке спецификации). Для коробов из HEIGHT_MIRROR_FRAME_TYPE_CODES (см. change
            // mirror-fantom-frame-height-to-leaf-height) высота не выбирается из каталога — её вообще нет
            // (validatedDimensionOption ниже отклонит любой переданный heightOptionId как непринадлежащий),
            // а обязана в точности совпадать с высотой полотна — поэтому для выгрузки берём leafHeightValue
            // напрямую (frameHeightMmOverride), а не каталожную опцию. Для коробов из HEIGHT_RANGE_FRAME_TYPE_CODES —
            // прежняя диапазонная проверка обязательности и совместимости, высота — обычная каталожная опция.
            LinerDimensionOption frameHeightOption = null;
            BigDecimal frameHeightMmOverride = null;
            if (HEIGHT_MIRROR_FRAME_TYPE_CODES.contains(frameType.getCode())) {
                validatedDimensionOption(componentName, frameType, selection.heightOptionId());
                requireHeightMirrorsLeaf(componentName, selection.customHeightValueMm(), leafHeightValue);
                frameHeightMmOverride = leafHeightValue;
            } else {
                // (см. change link-frame-neo-height-to-leaf-height, link-komplanar-height-to-leaf-height).
                if (HEIGHT_RANGE_FRAME_TYPE_CODES.contains(frameType.getCode()) && selection.heightOptionId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "для этого короба необходимо выбрать высоту");
                }
                frameHeightOption = validatedDimensionOption(componentName, frameType, selection.heightOptionId());
                if (frameHeightOption != null && HEIGHT_RANGE_FRAME_TYPE_CODES.contains(frameType.getCode())) {
                    validateHeightWithinLeafRange(componentName, HEIGHT_TYPE_CODE, frameHeightOption, leafHeightValue);
                }
            }
            BigDecimal frameHeightValue = frameHeightOption != null ? frameHeightOption.getValue() : frameHeightMmOverride;
            BigDecimal pogonazhMultiplier = resolvePogonazhSurchargeMultiplier(frameType, frameHeightValue);
            ComponentPriceDto framePrice = applyPogonazhSurcharge(
                    framePostPrice(componentName, frameType, colourOption), pogonazhMultiplier);
            components.add(framePrice);
            List<FramePost> framePosts = framePostRepository.findByFrameTypeId(frameType.getId());
            return new ResolvedComponent(type, framePrice, null, frameHeightOption, null, colourOption, framePosts, 1,
                    List.of(), List.of(), frameHeightMmOverride);
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

        BigDecimal lengthValue = lengthOption != null ? lengthOption.getValue() : null;
        BigDecimal pogonazhMultiplier = switch (type) {
            case DoorCasingType doorCasingType -> resolvePogonazhSurchargeMultiplier(doorCasingType, lengthValue);
            case FrameExtensionsType frameExtensionsType -> resolvePogonazhSurchargeMultiplier(frameExtensionsType, lengthValue);
            default -> BigDecimal.ONE;
        };

        Optional<ConfigurationPrice> matched = findMostSpecificPrice(type, lengthOption, heightOption, thicknessOption, colourOption);
        ComponentPriceDto price = matched
                .map(p -> componentPriceFrom(componentName, p, leafDimensionSurcharge, mirrorFinish.multiplier(),
                        glazing.multiplier(), applyReverseSurcharge, pogonazhMultiplier, quantity))
                .orElseGet(() -> new ComponentPriceDto(componentName, false, null, null, null, null));
        components.add(price);
        List<LeafPriceSurcharge> surcharges = type instanceof LeafType
                ? leafPriceSurcharges(leafDimensionSurcharge, mirrorFinish.multiplier(), glazing.multiplier(), applyReverseSurcharge)
                : List.of();
        List<String> selectedOptions = type instanceof LeafType ? leafSelectedOptions(mirrorFinish, glazing) : List.of();
        return new ResolvedComponent(
                type, price, lengthOption, heightOption, thicknessOption, colourOption, null, quantity, surcharges,
                selectedOptions, null);
    }

    // Наименования выбранных опций полотна (исполнение зеркала/вид остекления) для строки «выбранные опции»
    // под полотном в выгрузке спецификации (см. change add-specification-export) — тип открывания туда не
    // входит: это атрибут всей конфигурации (isReverse из запроса), а не опция, резолвимая здесь для
    // конкретного компонента, поэтому строится отдельно на уровне SpecificationExportService.
    private List<String> leafSelectedOptions(OptionSurcharge mirrorFinish, OptionSurcharge glazing) {
        List<String> options = new ArrayList<>();
        if (mirrorFinish.selectedLabel() != null) {
            options.add(mirrorFinish.selectedLabel());
        }
        if (glazing.selectedLabel() != null) {
            options.add(glazing.selectedLabel());
        }
        return options;
    }

    // Разбивка надбавок, применённых к цене полотна — те же формулировки, что и в App.tsx,
    // computeSurchargeBreakdown (см. LeafPriceSurcharge), но выведенные из уже посчитанных здесь множителей,
    // а не пересчитанные отдельно: multiplier == 1 означает, что соответствующая надбавка не применена
    // (в том числе для glazingMultiplier у «Прозрачного» остекления, surcharge_percent которого — 0), поэтому
    // отдельная проверка на этот случай не нужна — как и на совпадение произвольного размера со стандартным
    // (resolveAxisSurchargeMultiplier уже возвращает ONE и в этом случае).
    private List<LeafPriceSurcharge> leafPriceSurcharges(
            LeafDimensionSurcharge leafDimensionSurcharge, BigDecimal mirrorFinishMultiplier,
            BigDecimal glazingMultiplier, boolean applyReverseSurcharge) {
        List<LeafPriceSurcharge> surcharges = new ArrayList<>();
        addSurchargeIfApplied(surcharges, "За нестандартную ширину", leafDimensionSurcharge.lengthMultiplier());
        addSurchargeIfApplied(surcharges, "За нестандартную высоту", leafDimensionSurcharge.heightMultiplier());
        addSurchargeIfApplied(surcharges, "За исполнение зеркала", mirrorFinishMultiplier);
        addSurchargeIfApplied(surcharges, "За вид остекления", glazingMultiplier);
        if (applyReverseSurcharge) {
            addSurchargeIfApplied(surcharges, "За реверс", REVERSE_SURCHARGE_MULTIPLIER);
        }
        return surcharges;
    }

    private void addSurchargeIfApplied(List<LeafPriceSurcharge> surcharges, String label, BigDecimal multiplier) {
        if (multiplier.compareTo(BigDecimal.ONE) != 0) {
            BigDecimal percent = multiplier.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100));
            surcharges.add(new LeafPriceSurcharge(label, percent));
        }
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
    private OptionSurcharge resolveMirrorFinishMultiplier(String componentName, CatalogType type, Long mirrorFinishTypeId) {
        if (mirrorFinishTypeId == null) {
            return OptionSurcharge.NONE;
        }
        if (!(type instanceof LeafType leafType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "исполнение зеркала допустимо только для компонента leaf, а не для " + componentName);
        }
        MirrorFinishOption option = mirrorFinishOptionRepository
                .findByMirrorFinishTypeIdAndLeafTypeId(mirrorFinishTypeId, leafType.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "исполнение зеркала с id=" + mirrorFinishTypeId + " недопустимо для этого полотна"));
        BigDecimal multiplier =
                BigDecimal.ONE.add(option.getMirrorFinishType().getSurchargePercent().divide(BigDecimal.valueOf(100)));
        return new OptionSurcharge(multiplier, "Исполнение зеркала: " + option.getMirrorFinishType().getName());
    }

    // Вид остекления допустим только для leaf (см. change add-glazing-price-surcharge), по тому же принципу, что и
    // resolveMirrorFinishMultiplier: запрос ссылается на glazing_type.id (глобальный, тот же id, что и в каталоге
    // и в ответе GET /api/pricing-surcharges), а не на glazing_option.id. Для «Прозрачное» (surcharge_percent = 0)
    // множитель естественно равен BigDecimal.ONE — отдельной ветки для него не требуется; selectedLabel при этом
    // всё равно заполняется (см. change add-specification-export — строка «выбранной опции» под полотном
    // показывает сам факт выбора вида остекления, а не только те виды, что дают надбавку).
    private OptionSurcharge resolveGlazingMultiplier(String componentName, CatalogType type, Long glazingTypeId) {
        if (glazingTypeId == null) {
            return OptionSurcharge.NONE;
        }
        if (!(type instanceof LeafType leafType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "вид остекления допустим только для компонента leaf, а не для " + componentName);
        }
        GlazingOption option = glazingOptionRepository
                .findByGlazingTypeIdAndLeafTypeId(glazingTypeId, leafType.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "вид остекления с id=" + glazingTypeId + " недопустим для этого полотна"));
        BigDecimal multiplier =
                BigDecimal.ONE.add(option.getGlazingType().getSurchargePercent().divide(BigDecimal.valueOf(100)));
        return new OptionSurcharge(multiplier, "Вид остекления: " + option.getGlazingType().getName());
    }

    // Множитель надбавки вместе с наименованием выбранной опции (если она предполагает выбор — исполнение
    // зеркала/вид остекления) — нужно и для расчёта цены (multiplier), и для строки «выбранные опции» под
    // полотном в выгрузке спецификации (selectedLabel, см. change add-specification-export), чтобы не резолвить
    // MirrorFinishOption/GlazingOption заново отдельным путём.
    private record OptionSurcharge(BigDecimal multiplier, String selectedLabel) {
        static final OptionSurcharge NONE = new OptionSurcharge(BigDecimal.ONE, null);
    }

    // Надбавки применяются строго последовательно, с округлением после каждого шага (длина → высота → зеркало →
    // остекление → реверс), а не единым перемножением коэффициентов — так итоговая цена зависит от порядка шагов,
    // как того требует бизнес-логика (см. change add-dimension-surcharge-rules, решение об отказе от коммутативной
    // композиции). Количество (см. change add-casing-extensions-quantity) умножает уже посчитанную (с надбавками)
    // цену — для компонентов, где количество вообще допустимо (doorCasing/frameExtensions), надбавок никогда нет,
    // поэтому порядок «сначала надбавки, потом количество» не имеет практического значения.
    // price.getRetailPrice()/getDealerPrice() — цена компонента до применения этих наценок; умноженная на
    // количество, она передаётся в ответе как baseRetailPrice/baseDealerPrice (см. change
    // redesign-door-configurator-flow), чтобы фронтенд мог показать её рядом с итоговой ценой компонента.
    private ComponentPriceDto componentPriceFrom(
            String componentName, ConfigurationPrice price, LeafDimensionSurcharge leafDimensionSurcharge,
            BigDecimal mirrorFinishMultiplier, BigDecimal glazingMultiplier, boolean applyReverseSurcharge,
            BigDecimal pogonazhSurchargeMultiplier, int quantity) {
        BigDecimal quantityMultiplier = BigDecimal.valueOf(quantity);
        BigDecimal retailPrice = applySequentialSurcharges(price.getRetailPrice(), leafDimensionSurcharge,
                mirrorFinishMultiplier, glazingMultiplier, applyReverseSurcharge, pogonazhSurchargeMultiplier)
                .multiply(quantityMultiplier);
        BigDecimal dealerPrice = applySequentialSurcharges(price.getDealerPrice(), leafDimensionSurcharge,
                mirrorFinishMultiplier, glazingMultiplier, applyReverseSurcharge, pogonazhSurchargeMultiplier)
                .multiply(quantityMultiplier);
        BigDecimal baseRetailPrice = price.getRetailPrice().multiply(quantityMultiplier);
        BigDecimal baseDealerPrice = price.getDealerPrice().multiply(quantityMultiplier);
        return new ComponentPriceDto(componentName, true, retailPrice, dealerPrice, baseRetailPrice, baseDealerPrice);
    }

    // Порядок шагов: длина → высота → исполнение зеркала → вид остекления → реверс (см. change
    // add-mirror-finish-leaf-option, add-glazing-price-surcharge) — остекление встаёт строго между зеркалом и
    // реверсом, тем же принципом округления после каждого шага. Зеркало и остекление физически взаимоисключающи
    // для одного и того же leaf_type (см. миграция 0090-5-remove-mirror-finish-for-glazed-models), поэтому на
    // практике не применяются одновременно — порядок между ними фиксирован на будущее, для предсказуемости.
    private BigDecimal applySequentialSurcharges(
            BigDecimal price, LeafDimensionSurcharge leafDimensionSurcharge, BigDecimal mirrorFinishMultiplier,
            BigDecimal glazingMultiplier, boolean applyReverseSurcharge, BigDecimal pogonazhSurchargeMultiplier) {
        BigDecimal result = applyPercentMultiplier(price, leafDimensionSurcharge.lengthMultiplier());
        result = applyPercentMultiplier(result, leafDimensionSurcharge.heightMultiplier());
        result = applyPercentMultiplier(result, mirrorFinishMultiplier);
        result = applyPercentMultiplier(result, glazingMultiplier);
        if (applyReverseSurcharge) {
            result = applyReverseSurcharge(result);
        }
        // Наценка за нестандартную длину/высоту погонажа (короб/наличник/добор, см. change
        // add-pogonazh-length-surcharge) — для leaf этот множитель всегда ONE (наценка на leaf не
        // распространяется), поэтому её место в общей последовательности не влияет на существующее
        // поведение leaf.
        result = applyPercentMultiplier(result, pogonazhSurchargeMultiplier);
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
        // Правило, привязанное к конкретной модели полотна (leaf_type), имеет приоритет перед общим —
        // см. change add-leaf-height-2800-2900-except-sibir-03. Отсутствие строки для этой модели
        // (в отличие от отсутствия строки вообще) означает, что значение для неё недопустимо, даже если
        // общее правило с тем же value существует для других моделей.
        Optional<DimensionSurchargeRule> exactRule = dimensionSurchargeRuleRepository
                .findByLinerDimensionTypeIdAndValueAndLeafTypeId(dimensionType.getId(), customValue, leafType.getId())
                .or(() -> dimensionSurchargeRuleRepository
                        .findByLinerDimensionTypeIdAndValueAndLeafTypeIsNull(dimensionType.getId(), customValue));
        if (exactRule.isPresent() && !exactRule.get().isUnavailable()) {
            return BigDecimal.ONE.add(exactRule.get().getSurchargePercent().divide(BigDecimal.valueOf(100)));
        }
        // Каскад (см. change add-leaf-height-cascade-surcharge-50mm-grid) — только для оси высоты, и только
        // если для запрошенного значения нет вообще никакой строки (ни обычной, ни блокирующей): если строка
        // есть, но она блокирующая (exactRule.isPresent() && unavailable), значение недопустимо напрямую,
        // каскад не пробуется.
        if (exactRule.isEmpty() && HEIGHT_TYPE_CODE.equals(dimensionTypeCode)) {
            Optional<BigDecimal> cascadePercent = resolveHeightCascadeSurchargePercent(leafType, dimensionType, customValue);
            if (cascadePercent.isPresent()) {
                return BigDecimal.ONE.add(cascadePercent.get().divide(BigDecimal.valueOf(100)));
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "фабрика не производит полотно с размером " + customValue + " мм для этой оси");
    }

    // Каскадный спуск по сетке 50мм высоты полотна от 1900мм — см. change
    // add-leaf-height-cascade-surcharge-50mm-grid. Возвращает пустой Optional, если значение вне сетки,
    // ниже HEIGHT_GRID_FLOOR, спуск упёрся в блокирующую строку, либо сетка кончилась без находки.
    private Optional<BigDecimal> resolveHeightCascadeSurchargePercent(
            LeafType leafType, LinerDimensionType dimensionType, BigDecimal value) {
        if (value.compareTo(HEIGHT_GRID_FLOOR) < 0
                || value.subtract(HEIGHT_GRID_FLOOR).remainder(HEIGHT_GRID_STEP).compareTo(BigDecimal.ZERO) != 0) {
            return Optional.empty();
        }
        BigDecimal probe = value.subtract(HEIGHT_GRID_STEP);
        int steps = 1;
        while (probe.compareTo(HEIGHT_GRID_FLOOR) >= 0 && steps <= MAX_CASCADE_STEPS) {
            BigDecimal probeValue = probe;
            Optional<DimensionSurchargeRule> found = dimensionSurchargeRuleRepository
                    .findByLinerDimensionTypeIdAndValueAndLeafTypeId(dimensionType.getId(), probeValue, leafType.getId())
                    .or(() -> dimensionSurchargeRuleRepository
                            .findByLinerDimensionTypeIdAndValueAndLeafTypeIsNull(dimensionType.getId(), probeValue));
            if (found.isPresent()) {
                if (found.get().isUnavailable()) {
                    return Optional.empty();
                }
                return Optional.of(found.get().getSurchargePercent().add(CASCADE_STEP_PERCENT.multiply(BigDecimal.valueOf(steps))));
            }
            probe = probe.subtract(HEIGHT_GRID_STEP);
            steps++;
        }
        return Optional.empty();
    }

    // Наценка за нестандартную длину/высоту погонажа (короб/наличник/добор, см. change
    // add-pogonazh-length-surcharge) — отсутствие строки pogonazh_surcharge_rule для (владелец, value)
    // означает базовое значение (множитель ONE), а не ошибку: в отличие от dimension_surcharge_rule для
    // leaf, значение здесь уже провалидировано другим путём (каталожная опция короба/наличника/добора,
    // либо высота полотна для короба «Фантом») и не может оказаться физически недопустимым.
    private BigDecimal resolvePogonazhSurchargeMultiplier(FrameType frameType, BigDecimal heightValue) {
        if (heightValue == null) {
            return BigDecimal.ONE;
        }
        Optional<PogonazhSurchargeRule> pointRule =
                pogonazhSurchargeRuleRepository.findByFrameTypeIdAndValue(frameType.getId(), heightValue);
        if (pointRule.isPresent()) {
            return pogonazhSurchargeMultiplier(pointRule.get());
        }
        // Диапазонные правила (value NULL, min_value_exclusive/max_value_inclusive) — см. change
        // add-pogonazh-surcharge-70-100-percent-tiers; на практике встречаются только у короба
        // «Фантом», чья высота — свободное число, а не выбор из каталога.
        return pogonazhSurchargeRuleRepository.findByFrameTypeId(frameType.getId()).stream()
                .filter(rule -> rule.getValue() == null)
                .filter(rule -> rule.getMinValueExclusive() == null
                        || heightValue.compareTo(rule.getMinValueExclusive()) > 0)
                .filter(rule -> rule.getMaxValueInclusive() == null
                        || heightValue.compareTo(rule.getMaxValueInclusive()) <= 0)
                .findFirst()
                .map(this::pogonazhSurchargeMultiplier)
                .orElse(BigDecimal.ONE);
    }

    private BigDecimal resolvePogonazhSurchargeMultiplier(DoorCasingType doorCasingType, BigDecimal lengthValue) {
        if (lengthValue == null) {
            return BigDecimal.ONE;
        }
        return pogonazhSurchargeRuleRepository.findByDoorCasingTypeIdAndValue(doorCasingType.getId(), lengthValue)
                .map(this::pogonazhSurchargeMultiplier)
                .orElse(BigDecimal.ONE);
    }

    private BigDecimal resolvePogonazhSurchargeMultiplier(FrameExtensionsType frameExtensionsType, BigDecimal lengthValue) {
        if (lengthValue == null) {
            return BigDecimal.ONE;
        }
        return pogonazhSurchargeRuleRepository.findByFrameExtensionsTypeIdAndValue(frameExtensionsType.getId(), lengthValue)
                .map(this::pogonazhSurchargeMultiplier)
                .orElse(BigDecimal.ONE);
    }

    private BigDecimal pogonazhSurchargeMultiplier(PogonazhSurchargeRule rule) {
        return BigDecimal.ONE.add(rule.getSurchargePercent().divide(BigDecimal.valueOf(100)));
    }

    // Короб — единственный компонент, чья цена не собирается через componentPriceFrom/
    // applySequentialSurcharges (см. framePostPrice) — наценку за погонаж применяем к нему отдельно,
    // тем же способом (applyPercentMultiplier), после того как базовая цена уже найдена. baseRetailPrice/
    // baseDealerPrice переданного price здесь всегда равны его retailPrice/dealerPrice (короб до этой
    // наценки не нёс других надбавок), поэтому их можно использовать как «цену без наценки за погонаж».
    private ComponentPriceDto applyPogonazhSurcharge(ComponentPriceDto price, BigDecimal multiplier) {
        if (!price.priced() || multiplier.compareTo(BigDecimal.ONE) == 0) {
            return price;
        }
        BigDecimal retailPrice = applyPercentMultiplier(price.retailPrice(), multiplier);
        BigDecimal dealerPrice = applyPercentMultiplier(price.dealerPrice(), multiplier);
        return new ComponentPriceDto(
                price.component(), true, retailPrice, dealerPrice, price.baseRetailPrice(), price.baseDealerPrice());
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
