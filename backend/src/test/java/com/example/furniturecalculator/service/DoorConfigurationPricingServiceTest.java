package com.example.furniturecalculator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.example.furniturecalculator.domain.ColourOption;
import com.example.furniturecalculator.domain.ColourType;
import com.example.furniturecalculator.domain.ConfigurationPrice;
import com.example.furniturecalculator.domain.DimensionSurchargeRule;
import com.example.furniturecalculator.domain.DoorCasingType;
import com.example.furniturecalculator.domain.DoorConfiguration;
import com.example.furniturecalculator.domain.EdgeType;
import com.example.furniturecalculator.domain.FrameExtensionsType;
import com.example.furniturecalculator.domain.FramePost;
import com.example.furniturecalculator.domain.FrameType;
import com.example.furniturecalculator.domain.GlazingOption;
import com.example.furniturecalculator.domain.GlazingType;
import com.example.furniturecalculator.domain.HardwareCategory;
import com.example.furniturecalculator.domain.HardwareOption;
import com.example.furniturecalculator.domain.HardwareType;
import com.example.furniturecalculator.domain.LeafType;
import com.example.furniturecalculator.domain.LinerDimensionOption;
import com.example.furniturecalculator.domain.LinerDimensionType;
import com.example.furniturecalculator.domain.MirrorFinishOption;
import com.example.furniturecalculator.domain.MirrorFinishType;
import com.example.furniturecalculator.dto.ComponentPriceDto;
import com.example.furniturecalculator.dto.ComponentSelectionDto;
import com.example.furniturecalculator.dto.HardwarePriceDto;
import com.example.furniturecalculator.dto.HardwareSelectionDto;
import com.example.furniturecalculator.dto.PricingRequestDto;
import com.example.furniturecalculator.dto.PricingResponseDto;
import com.example.furniturecalculator.repository.ColourOptionRepository;
import com.example.furniturecalculator.repository.ConfigurationPriceRepository;
import com.example.furniturecalculator.repository.DimensionSurchargeRuleRepository;
import com.example.furniturecalculator.repository.DoorConfigurationRepository;
import com.example.furniturecalculator.repository.FramePostRepository;
import com.example.furniturecalculator.repository.GlazingOptionRepository;
import com.example.furniturecalculator.repository.HardwareOptionRepository;
import com.example.furniturecalculator.repository.LeafTypeRepository;
import com.example.furniturecalculator.repository.LinerDimensionOptionRepository;
import com.example.furniturecalculator.repository.LinerDimensionTypeRepository;
import com.example.furniturecalculator.repository.MirrorFinishOptionRepository;
import com.example.furniturecalculator.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class DoorConfigurationPricingServiceTest {

    @Mock
    private DoorConfigurationRepository doorConfigurationRepository;
    @Mock
    private LinerDimensionOptionRepository linerDimensionOptionRepository;
    @Mock
    private LinerDimensionTypeRepository linerDimensionTypeRepository;
    @Mock
    private DimensionSurchargeRuleRepository dimensionSurchargeRuleRepository;
    @Mock
    private ColourOptionRepository colourOptionRepository;
    @Mock
    private ConfigurationPriceRepository configurationPriceRepository;
    @Mock
    private FramePostRepository framePostRepository;
    @Mock
    private MirrorFinishOptionRepository mirrorFinishOptionRepository;
    @Mock
    private GlazingOptionRepository glazingOptionRepository;
    @Mock
    private HardwareOptionRepository hardwareOptionRepository;
    @Mock
    private LeafTypeRepository leafTypeRepository;

    @InjectMocks
    private DoorConfigurationPricingService service;

    private final LeafType leafType = TestEntities.leafType(1L);
    private final LinerDimensionType lengthType = TestEntities.linerDimensionType(100L);
    private final ColourType colourType = TestEntities.colourType(200L);

    @Test
    void несуществующая_конфигурация_возвращает_404() {
        when(doorConfigurationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculate(999L, new PricingRequestDto(null, null, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void опция_чужого_компонента_возвращает_400() {
        FrameType frameType = TestEntities.frameType(2L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);
        LinerDimensionOption frameLength =
                TestEntities.linerDimensionOption(1000L, lengthType, BigDecimal.valueOf(600), true, frameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(frameLength));

        PricingRequestDto request =
                new PricingRequestDto(new ComponentSelectionDto(1000L, null, null, null, null, null, null, null, null), null, null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void более_специфичная_цена_побеждает_менее_специфичную() {
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);
        LinerDimensionOption length = TestEntities.linerDimensionOption(1000L, lengthType, BigDecimal.valueOf(600), true, leafType);
        ColourOption colour = TestEntities.colourOption(2000L, colourType, leafType);

        ConfigurationPrice lessSpecific = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, length, null, null, null);
        ConfigurationPrice moreSpecific = TestEntities.configurationPrice(
                2L, BigDecimal.valueOf(1200), BigDecimal.valueOf(1100), leafType, length, null, null, colour);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(length));
        when(colourOptionRepository.findById(2000L)).thenReturn(Optional.of(colour));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(lessSpecific, moreSpecific));

        PricingRequestDto request =
                new PricingRequestDto(new ComponentSelectionDto(1000L, null, null, 2000L, null, null, null, null, null), null, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.totalRetailPrice()).isEqualByComparingTo("1200");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("1100");
        assertThat(response.components()).hasSize(1);
        assertThat(response.components().get(0).priced()).isTrue();
    }

    @Test
    void строка_с_несовпадающей_осью_не_подходит() {
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);
        LinerDimensionOption length = TestEntities.linerDimensionOption(1000L, lengthType, BigDecimal.valueOf(600), true, leafType);
        ColourOption selectedColour = TestEntities.colourOption(2000L, colourType, leafType);
        ColourOption otherColour = TestEntities.colourOption(2001L, colourType, leafType);

        ConfigurationPrice mismatched = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, length, null, null, otherColour);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(length));
        when(colourOptionRepository.findById(2000L)).thenReturn(Optional.of(selectedColour));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(mismatched));

        PricingRequestDto request =
                new PricingRequestDto(new ComponentSelectionDto(1000L, null, null, 2000L, null, null, null, null, null), null, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components().get(0).priced()).isFalse();
        assertThat(response.totalRetailPrice()).isEqualByComparingTo("0");
    }

    @Test
    void частичный_расчёт_при_отсутствии_цены_компонента() {
        FrameType frameType = TestEntities.frameType(2L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, frameType, null, null, null);
        LinerDimensionOption leafLength = TestEntities.linerDimensionOption(1000L, lengthType, BigDecimal.valueOf(600), true, leafType);

        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, leafLength, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafLength));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));
        when(framePostRepository.findByFrameTypeId(2L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(1000L, null, null, null, null, null, null, null, null), ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.totalRetailPrice()).isEqualByComparingTo("1000");
        assertThat(response.components()).hasSize(2);
        assertThat(response.components().stream()
                        .filter(c -> c.component().equals("frame"))
                        .findFirst()
                        .orElseThrow()
                        .priced())
                .isFalse();
    }

    @Test
    void неоднозначное_совпадение_цены_исключает_компонент() {
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);
        LinerDimensionOption length = TestEntities.linerDimensionOption(1000L, lengthType, BigDecimal.valueOf(600), true, leafType);

        ConfigurationPrice priceA = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, length, null, null, null);
        ConfigurationPrice priceB = TestEntities.configurationPrice(
                2L, BigDecimal.valueOf(1100), BigDecimal.valueOf(950), leafType, length, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(length));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(priceA, priceB));

        PricingRequestDto request =
                new PricingRequestDto(new ComponentSelectionDto(1000L, null, null, null, null, null, null, null, null), null, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components().get(0).priced()).isFalse();
    }

    @Test
    void ни_один_компонент_не_оценён() {
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(ComponentSelectionDto.EMPTY, null, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.totalRetailPrice()).isEqualByComparingTo("0");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("0");
        assertThat(response.components()).allMatch(c -> !c.priced());
    }

    @Test
    void успешный_расчёт_по_всем_компонентам() {
        FrameType frameType = TestEntities.frameType(2L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, frameType, null, null, null);

        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);
        FramePost framePost = TestEntities.framePost(2L, BigDecimal.valueOf(500), BigDecimal.valueOf(400), frameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));
        when(framePostRepository.findByFrameTypeId(2L)).thenReturn(List.of(framePost));

        PricingRequestDto request =
                new PricingRequestDto(ComponentSelectionDto.EMPTY, ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.totalRetailPrice()).isEqualByComparingTo("1500");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("1300");
        assertThat(response.components()).hasSize(2);
        assertThat(response.components()).allMatch(ComponentPriceDto::priced);
    }

    @Test
    void стоимость_короба_складывается_из_нескольких_записей_frame_post() {
        FrameType frameType = TestEntities.frameType(2L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, frameType, null, null, null);

        FramePost topPost = TestEntities.framePost(100L, BigDecimal.valueOf(3549), BigDecimal.valueOf(2027), frameType);
        FramePost sidePostsKit = TestEntities.framePost(101L, BigDecimal.valueOf(7047), BigDecimal.valueOf(4027), frameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(framePostRepository.findByFrameTypeId(2L)).thenReturn(List.of(topPost, sidePostsKit));

        PricingRequestDto request =
                new PricingRequestDto(ComponentSelectionDto.EMPTY, ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto framePrice = response.components().stream()
                .filter(c -> c.component().equals("frame"))
                .findFirst()
                .orElseThrow();
        assertThat(framePrice.priced()).isTrue();
        assertThat(framePrice.retailPrice()).isEqualByComparingTo("10596");
        assertThat(framePrice.dealerPrice()).isEqualByComparingTo("6054");
    }

    @Test
    void короб_с_выбранным_цветом_считается_по_цене_цвета() {
        FrameType frameType = TestEntities.frameType(2L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, frameType, null, null, null);
        ColourOption colourOption = TestEntities.colourOption(2000L, colourType, frameType);
        ConfigurationPrice colourPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(16850), BigDecimal.valueOf(9903), frameType, null, null, null, colourOption);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(colourOptionRepository.findById(2000L)).thenReturn(Optional.of(colourOption));
        when(colourOptionRepository.findByFrameTypeId(2L)).thenReturn(List.of(colourOption));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameTypeId(2L)).thenReturn(List.of(colourPrice));
        when(framePostRepository.findByFrameTypeId(2L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, new ComponentSelectionDto(null, null, null, 2000L, null, null, null, null, null), null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto framePrice = response.components().stream()
                .filter(c -> c.component().equals("frame"))
                .findFirst()
                .orElseThrow();
        assertThat(framePrice.priced()).isTrue();
        assertThat(framePrice.retailPrice()).isEqualByComparingTo("16850");
        assertThat(framePrice.dealerPrice()).isEqualByComparingTo("9903");
    }

    @Test
    void короб_с_цветом_и_frame_post_складывает_обе_суммы() {
        FrameType frameType = TestEntities.frameType(2L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, frameType, null, null, null);
        ColourOption colourOption = TestEntities.colourOption(2000L, colourType, frameType);
        ConfigurationPrice colourPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(16850), BigDecimal.valueOf(9903), frameType, null, null, null, colourOption);
        FramePost framePost = TestEntities.framePost(3L, BigDecimal.valueOf(500), BigDecimal.valueOf(400), frameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(colourOptionRepository.findById(2000L)).thenReturn(Optional.of(colourOption));
        when(colourOptionRepository.findByFrameTypeId(2L)).thenReturn(List.of(colourOption));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameTypeId(2L)).thenReturn(List.of(colourPrice));
        when(framePostRepository.findByFrameTypeId(2L)).thenReturn(List.of(framePost));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, new ComponentSelectionDto(null, null, null, 2000L, null, null, null, null, null), null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto framePrice = response.components().stream()
                .filter(c -> c.component().equals("frame"))
                .findFirst()
                .orElseThrow();
        assertThat(framePrice.priced()).isTrue();
        assertThat(framePrice.retailPrice()).isEqualByComparingTo("17350");
        assertThat(framePrice.dealerPrice()).isEqualByComparingTo("10303");
    }

    @Test
    void короб_с_цветовыми_опциями_без_выбора_цвета_некалькулируем() {
        FrameType frameType = TestEntities.frameType(2L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, frameType, null, null, null);
        ColourOption colourOption = TestEntities.colourOption(2000L, colourType, frameType);
        FramePost framePost = TestEntities.framePost(3L, BigDecimal.valueOf(500), BigDecimal.valueOf(400), frameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(colourOptionRepository.findByFrameTypeId(2L)).thenReturn(List.of(colourOption));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(framePostRepository.findByFrameTypeId(2L)).thenReturn(List.of(framePost));

        PricingRequestDto request =
                new PricingRequestDto(ComponentSelectionDto.EMPTY, ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto framePrice = response.components().stream()
                .filter(c -> c.component().equals("frame"))
                .findFirst()
                .orElseThrow();
        assertThat(framePrice.priced()).isFalse();
        assertThat(response.totalRetailPrice()).isEqualByComparingTo("0");
    }

    @Test
    void короб_с_неоднозначным_совпадением_цвета_некалькулируем() {
        FrameType frameType = TestEntities.frameType(2L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, frameType, null, null, null);
        ColourOption colourOption = TestEntities.colourOption(2000L, colourType, frameType);
        ConfigurationPrice priceA = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(16850), BigDecimal.valueOf(9903), frameType, null, null, null, colourOption);
        ConfigurationPrice priceB = TestEntities.configurationPrice(
                2L, BigDecimal.valueOf(17000), BigDecimal.valueOf(10000), frameType, null, null, null, colourOption);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(colourOptionRepository.findById(2000L)).thenReturn(Optional.of(colourOption));
        when(colourOptionRepository.findByFrameTypeId(2L)).thenReturn(List.of(colourOption));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameTypeId(2L)).thenReturn(List.of(priceA, priceB));
        when(framePostRepository.findByFrameTypeId(2L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, new ComponentSelectionDto(null, null, null, 2000L, null, null, null, null, null), null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto framePrice = response.components().stream()
                .filter(c -> c.component().equals("frame"))
                .findFirst()
                .orElseThrow();
        assertThat(framePrice.priced()).isFalse();
    }

    @Test
    void высота_полотна_внутри_диапазона_кромки_расчёт_выполняется() {
        EdgeType edgeType = TestEntities.edgeType(3L);
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, edgeType, null, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2100), true, leafType);
        LinerDimensionOption edgeHeightRange = TestEntities.linerDimensionOptionRange(
                2000L, heightType, BigDecimal.valueOf(2000), BigDecimal.valueOf(2200), true, edgeType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(edgeHeightRange));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByEdgeTypeId(3L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null,
                new ComponentSelectionDto(null, 2000L, null, null, null, null, null, null, null),
                null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_вне_диапазона_кромки_возвращает_400() {
        EdgeType edgeType = TestEntities.edgeType(3L);
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, edgeType, null, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2100), true, leafType);
        LinerDimensionOption edgeHeightRange = TestEntities.linerDimensionOptionRange(
                2000L, heightType, BigDecimal.valueOf(2450), BigDecimal.valueOf(2700), true, edgeType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(edgeHeightRange));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null,
                new ComponentSelectionDto(null, 2000L, null, null, null, null, null, null, null),
                null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_кромки_выбрана_без_высоты_полотна_возвращает_400() {
        EdgeType edgeType = TestEntities.edgeType(3L);
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, edgeType, null, null);
        LinerDimensionOption edgeHeightRange = TestEntities.linerDimensionOptionRange(
                2000L, heightType, BigDecimal.valueOf(2000), BigDecimal.valueOf(2200), true, edgeType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(edgeHeightRange));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY,
                null,
                new ComponentSelectionDto(null, 2000L, null, null, null, null, null, null, null),
                null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_полотна_внутри_диапазона_короба_нео_расчёт_выполняется() {
        FrameType neoFrameType = TestEntities.frameType(3L, "FT-003");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, neoFrameType, null, null, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);
        LinerDimensionOption neoHeightRange = TestEntities.linerDimensionOptionRange(
                2000L, heightType, BigDecimal.valueOf(2170), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, neoFrameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(neoHeightRange));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, 2000L, null, null, null, null, null, null, null),
                null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_вне_диапазона_короба_нео_возвращает_400() {
        FrameType neoFrameType = TestEntities.frameType(3L, "FT-003");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, neoFrameType, null, null, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2450), true, leafType);
        LinerDimensionOption neoHeightRange = TestEntities.linerDimensionOptionRange(
                2000L, heightType, BigDecimal.valueOf(2170), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, neoFrameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(neoHeightRange));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, 2000L, null, null, null, null, null, null, null),
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_короба_нео_выбрана_без_высоты_полотна_возвращает_400() {
        FrameType neoFrameType = TestEntities.frameType(3L, "FT-003");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, neoFrameType, null, null, null);
        LinerDimensionOption neoHeightRange = TestEntities.linerDimensionOptionRange(
                2000L, heightType, BigDecimal.valueOf(2170), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, neoFrameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(neoHeightRange));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY,
                new ComponentSelectionDto(null, 2000L, null, null, null, null, null, null, null),
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_полотна_в_разрыве_между_диапазонами_короба_нео_возвращает_400() {
        FrameType neoFrameType = TestEntities.frameType(3L, "FT-003");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, neoFrameType, null, null, null);
        // 2260 не попадает ни в [2150, 2250], ни в [2300, 2300] — разрыв между диапазонами короба «НЕО».
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2260), true, leafType);
        LinerDimensionOption neoHeightRange = TestEntities.linerDimensionOptionRange(
                2000L, heightType, BigDecimal.valueOf(2400), BigDecimal.valueOf(2150), BigDecimal.valueOf(2250), true, neoFrameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(neoHeightRange));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, 2000L, null, null, null, null, null, null, null),
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_полотна_ровно_2300_проходит_диапазон_точку_короба_нео() {
        FrameType neoFrameType = TestEntities.frameType(3L, "FT-003");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, neoFrameType, null, null, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2300), true, leafType);
        // Диапазон-точка: value=2400, min_value=max_value=2300 — тот же value, что и у соседнего
        // диапазона [2150, 2250], но с непересекающимися границами (см. change link-frame-neo-height-to-leaf-height).
        LinerDimensionOption neoHeightPoint = TestEntities.linerDimensionOptionRange(
                2001L, heightType, BigDecimal.valueOf(2400), BigDecimal.valueOf(2300), BigDecimal.valueOf(2300), true, neoFrameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2001L)).thenReturn(Optional.of(neoHeightPoint));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, 2001L, null, null, null, null, null, null, null),
                null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void короб_нео_без_выбранной_высоты_возвращает_400() {
        FrameType neoFrameType = TestEntities.frameType(3L, "FT-003");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, neoFrameType, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY,
                ComponentSelectionDto.EMPTY,
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void короб_нео_без_выбранной_высоты_возвращает_400_даже_если_высота_полотна_выбрана() {
        FrameType neoFrameType = TestEntities.frameType(3L, "FT-003");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, neoFrameType, null, null, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                ComponentSelectionDto.EMPTY,
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void короб_другого_типа_без_высоты_не_является_ошибкой() {
        FrameType frameType = TestEntities.frameType(2L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, frameType, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY,
                ComponentSelectionDto.EMPTY,
                null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void проверка_диапазона_не_применяется_к_другому_типу_короба() {
        FrameType otherFrameType = TestEntities.frameType(3L);
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, otherFrameType, null, null, null);
        // Высота полотна намеренно вне диапазона [1900, 2100] — но проверка не должна применяться,
        // т.к. код типа короба не FT-003.
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(3000), true, leafType);
        LinerDimensionOption heightRange = TestEntities.linerDimensionOptionRange(
                2000L, heightType, BigDecimal.valueOf(2170), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, otherFrameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(heightRange));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, 2000L, null, null, null, null, null, null, null),
                null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_внутри_диапазона_короба_компланар_расчёт_выполняется() {
        FrameType komplanarFrameType = TestEntities.frameType(3L, "FT-002");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, komplanarFrameType, null, null, null);
        // 2600-2850 -> 3000 мм — диапазон, специфичный для короба «Компланар» (у короба «НЕО» он недоступен).
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2700), true, leafType);
        LinerDimensionOption komplanarHeightRange = TestEntities.linerDimensionOptionRange(
                2000L, heightType, BigDecimal.valueOf(3000), BigDecimal.valueOf(2600), BigDecimal.valueOf(2850), true, komplanarFrameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(komplanarHeightRange));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, 2000L, null, null, null, null, null, null, null),
                null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_вне_диапазона_короба_компланар_возвращает_400() {
        FrameType komplanarFrameType = TestEntities.frameType(3L, "FT-002");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, komplanarFrameType, null, null, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);
        LinerDimensionOption komplanarHeightRange = TestEntities.linerDimensionOptionRange(
                2000L, heightType, BigDecimal.valueOf(3000), BigDecimal.valueOf(2600), BigDecimal.valueOf(2850), true, komplanarFrameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(komplanarHeightRange));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, 2000L, null, null, null, null, null, null, null),
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_короба_компланар_выбрана_без_высоты_полотна_возвращает_400() {
        FrameType komplanarFrameType = TestEntities.frameType(3L, "FT-002");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, komplanarFrameType, null, null, null);
        LinerDimensionOption komplanarHeightRange = TestEntities.linerDimensionOptionRange(
                2000L, heightType, BigDecimal.valueOf(2170), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, komplanarFrameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(komplanarHeightRange));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY,
                new ComponentSelectionDto(null, 2000L, null, null, null, null, null, null, null),
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void короб_компланар_без_выбранной_высоты_возвращает_400() {
        FrameType komplanarFrameType = TestEntities.frameType(3L, "FT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, komplanarFrameType, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY,
                ComponentSelectionDto.EMPTY,
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_полотна_в_разрыве_между_диапазонами_короба_компланар_возвращает_400() {
        FrameType komplanarFrameType = TestEntities.frameType(3L, "FT-002");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, komplanarFrameType, null, null, null);
        // 2900 не покрывается ни диапазоном [2600, 2850] -> 3000, ни каким-либо другим — разрыв 2900-2950,
        // где короб «Компланар» недоступен.
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2900), true, leafType);
        LinerDimensionOption komplanarHeightRange = TestEntities.linerDimensionOptionRange(
                2000L, heightType, BigDecimal.valueOf(3000), BigDecimal.valueOf(2600), BigDecimal.valueOf(2850), true, komplanarFrameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(komplanarHeightRange));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, 2000L, null, null, null, null, null, null, null),
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_полотна_ровно_2300_проходит_диапазон_точку_короба_компланар() {
        FrameType komplanarFrameType = TestEntities.frameType(3L, "FT-002");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, komplanarFrameType, null, null, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2300), true, leafType);
        LinerDimensionOption komplanarHeightPoint = TestEntities.linerDimensionOptionRange(
                2001L, heightType, BigDecimal.valueOf(2400), BigDecimal.valueOf(2300), BigDecimal.valueOf(2300), true, komplanarFrameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2001L)).thenReturn(Optional.of(komplanarHeightPoint));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, 2001L, null, null, null, null, null, null, null),
                null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void правило_диапазона_короба_компланар_действует_и_для_реверсивной_конфигурации() {
        FrameType komplanarFrameType = TestEntities.frameType(3L, "FT-002");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfigurationReverse(10L, leafType, komplanarFrameType, null, null, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);
        LinerDimensionOption komplanarHeightRange = TestEntities.linerDimensionOptionRange(
                2000L, heightType, BigDecimal.valueOf(3000), BigDecimal.valueOf(2600), BigDecimal.valueOf(2850), true, komplanarFrameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(komplanarHeightRange));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, 2000L, null, null, null, null, null, null, null),
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_короба_фантом_совпадает_с_высотой_полотна_расчёт_выполняется() {
        FrameType fantomType = TestEntities.frameType(2L, "FT-001");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, fantomType, null, null, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2100), true, leafType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, null, null, null, null, BigDecimal.valueOf(2100), null, null, null),
                null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_короба_фантом_совпадает_с_произвольной_высотой_полотна_расчёт_выполняется() {
        FrameType fantomType = TestEntities.frameType(2L, "FT-001");
        LinerDimensionType leafHeightType = TestEntities.linerDimensionType(300L, "DT-002");
        // 2200 — не стандартный размер полотна, но есть правило наценки (см. миграцию 0058) — подходит
        // и для проверки, что высота короба «Фантом» зеркалирует именно РЕЗУЛЬТИРУЮЩУЮ высоту полотна,
        // а не только стандартную каталожную опцию.
        DimensionSurchargeRule rule = TestEntities.dimensionSurchargeRule(
                1L, leafHeightType, BigDecimal.valueOf(2200), BigDecimal.valueOf(20));
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, fantomType, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(linerDimensionTypeRepository.findByCode("DT-002")).thenReturn(Optional.of(leafHeightType));
        when(dimensionSurchargeRuleRepository.findByLinerDimensionTypeIdAndValue(300L, BigDecimal.valueOf(2200)))
                .thenReturn(Optional.of(rule));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, null, BigDecimal.valueOf(2200), null, null, null),
                new ComponentSelectionDto(null, null, null, null, null, BigDecimal.valueOf(2200), null, null, null),
                null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_короба_фантом_не_совпадает_с_высотой_полотна_возвращает_400() {
        FrameType fantomType = TestEntities.frameType(2L, "FT-001");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, fantomType, null, null, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2100), true, leafType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, null, null, null, null, BigDecimal.valueOf(2200), null, null, null),
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_короба_фантом_указана_без_высоты_полотна_возвращает_400() {
        FrameType fantomType = TestEntities.frameType(2L, "FT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, fantomType, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY,
                new ComponentSelectionDto(null, null, null, null, null, BigDecimal.valueOf(2100), null, null, null),
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void короб_фантом_без_указанной_высоты_возвращает_400() {
        FrameType fantomType = TestEntities.frameType(2L, "FT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, fantomType, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY,
                ComponentSelectionDto.EMPTY,
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void heightOptionId_для_короба_фантом_отклоняется_как_непринадлежащий() {
        FrameType fantomType = TestEntities.frameType(2L, "FT-001");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, fantomType, null, null, null);
        // У короба «Фантом» нет каталожных опций высоты — эта опция принадлежит полотну, а не короб.
        LinerDimensionOption foreignOption =
                TestEntities.linerDimensionOption(5000L, heightType, BigDecimal.valueOf(2100), true, leafType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(5000L)).thenReturn(Optional.of(foreignOption));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY,
                new ComponentSelectionDto(null, 5000L, null, null, null, null, null, null, null),
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void другой_короб_с_произвольной_высотой_отклоняется_400() {
        FrameType neoFrameType = TestEntities.frameType(3L, "FT-003");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, neoFrameType, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY,
                new ComponentSelectionDto(null, null, null, null, null, BigDecimal.valueOf(2100), null, null, null),
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void произвольная_длина_для_короба_фантом_отклоняется_400() {
        FrameType fantomType = TestEntities.frameType(2L, "FT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, fantomType, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY,
                new ComponentSelectionDto(null, null, null, null, BigDecimal.valueOf(2100), null, null, null, null),
                null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void цена_короба_фантом_не_зависит_от_высоты() {
        FrameType fantomType = TestEntities.frameType(2L, "FT-001");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        ColourOption colourOption = TestEntities.colourOption(2000L, colourType, fantomType);
        ConfigurationPrice colourPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(16850), BigDecimal.valueOf(9903), fantomType, null, null, null, colourOption);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, fantomType, null, null, null);
        LinerDimensionOption leafHeightA =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2100), true, leafType);
        LinerDimensionOption leafHeightB =
                TestEntities.linerDimensionOption(1001L, heightType, BigDecimal.valueOf(2000), true, leafType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeightA));
        when(linerDimensionOptionRepository.findById(1001L)).thenReturn(Optional.of(leafHeightB));
        when(colourOptionRepository.findById(2000L)).thenReturn(Optional.of(colourOption));
        when(colourOptionRepository.findByFrameTypeId(2L)).thenReturn(List.of(colourOption));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameTypeId(2L)).thenReturn(List.of(colourPrice));
        when(framePostRepository.findByFrameTypeId(2L)).thenReturn(List.of());

        PricingRequestDto requestA = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, null, null, 2000L, null, BigDecimal.valueOf(2100), null, null, null),
                null, null, null);
        PricingRequestDto requestB = new PricingRequestDto(
                new ComponentSelectionDto(null, 1001L, null, null, null, null, null, null, null),
                new ComponentSelectionDto(null, null, null, 2000L, null, BigDecimal.valueOf(2000), null, null, null),
                null, null, null);

        ComponentPriceDto frameA = service.calculate(10L, requestA).components().stream()
                .filter(c -> c.component().equals("frame"))
                .findFirst()
                .orElseThrow();
        ComponentPriceDto frameB = service.calculate(10L, requestB).components().stream()
                .filter(c -> c.component().equals("frame"))
                .findFirst()
                .orElseThrow();

        assertThat(frameA.retailPrice()).isEqualByComparingTo("16850");
        assertThat(frameA.dealerPrice()).isEqualByComparingTo("9903");
        assertThat(frameB.retailPrice()).isEqualByComparingTo(frameA.retailPrice());
        assertThat(frameB.dealerPrice()).isEqualByComparingTo(frameA.dealerPrice());
    }

    @Test
    void высота_полотна_внутри_диапазона_длины_добора_тс_расчёт_выполняется() {
        FrameExtensionsType doborTsType = TestEntities.frameExtensionsType(6L, "FET-004");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborTsType);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);
        LinerDimensionOption doborTsLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2170), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, doborTsType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborTsLengthRange));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameExtensionsTypeId(6L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_вне_диапазона_длины_добора_тс_возвращает_400() {
        FrameExtensionsType doborTsType = TestEntities.frameExtensionsType(6L, "FET-004");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborTsType);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2450), true, leafType);
        LinerDimensionOption doborTsLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2170), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, doborTsType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborTsLengthRange));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void длина_добора_тс_выбрана_без_высоты_полотна_возвращает_400() {
        FrameExtensionsType doborTsType = TestEntities.frameExtensionsType(6L, "FET-004");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborTsType);
        LinerDimensionOption doborTsLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2170), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, doborTsType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborTsLengthRange));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_полотна_в_разрыве_между_диапазонами_длины_добора_тс_возвращает_400() {
        FrameExtensionsType doborTsType = TestEntities.frameExtensionsType(6L, "FET-004");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborTsType);
        // 2260 не попадает ни в [2150, 2250], ни в [2300, 2300] — разрыв между диапазонами добора «ТС».
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2260), true, leafType);
        LinerDimensionOption doborTsLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2400), BigDecimal.valueOf(2150), BigDecimal.valueOf(2250), true, doborTsType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborTsLengthRange));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_полотна_ровно_2300_проходит_диапазон_точку_длины_добора_тс() {
        FrameExtensionsType doborTsType = TestEntities.frameExtensionsType(6L, "FET-004");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborTsType);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2300), true, leafType);
        // Диапазон-точка: value=2400, min_value=max_value=2300 — тот же value, что и у соседнего
        // диапазона [2150, 2250], но с непересекающимися границами (см. change link-dobor-ts-length-to-leaf-height).
        LinerDimensionOption doborTsLengthPoint = TestEntities.linerDimensionOptionRange(
                2001L, lengthType, BigDecimal.valueOf(2400), BigDecimal.valueOf(2300), BigDecimal.valueOf(2300), true, doborTsType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2001L)).thenReturn(Optional.of(doborTsLengthPoint));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameExtensionsTypeId(6L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2001L, null, null, null, null, null, null, null, null));

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_внутри_диапазона_длины_добора_комплан_расчёт_выполняется() {
        FrameExtensionsType doborKomplanarType = TestEntities.frameExtensionsType(40L, "FET-008");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborKomplanarType);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);
        LinerDimensionOption doborKomplanarLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2170), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, doborKomplanarType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborKomplanarLengthRange));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameExtensionsTypeId(40L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_2150_2250_добора_комплан_длина_2400_расчёт_выполняется() {
        FrameExtensionsType doborKomplanarType = TestEntities.frameExtensionsType(40L, "FET-009");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborKomplanarType);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2200), true, leafType);
        LinerDimensionOption doborKomplanarLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2400), BigDecimal.valueOf(2150), BigDecimal.valueOf(2250), true, doborKomplanarType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborKomplanarLengthRange));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameExtensionsTypeId(40L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_ровно_2300_проходит_диапазон_точку_длины_добора_комплан() {
        FrameExtensionsType doborKomplanarType = TestEntities.frameExtensionsType(40L, "FET-010");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborKomplanarType);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2300), true, leafType);
        // Диапазон-точка: value=2400, min_value=max_value=2300 — тот же value, что и у соседнего
        // диапазона [2150, 2250], но с непересекающимися границами.
        LinerDimensionOption doborKomplanarLengthPoint = TestEntities.linerDimensionOptionRange(
                2001L, lengthType, BigDecimal.valueOf(2400), BigDecimal.valueOf(2300), BigDecimal.valueOf(2300), true, doborKomplanarType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2001L)).thenReturn(Optional.of(doborKomplanarLengthPoint));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameExtensionsTypeId(40L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2001L, null, null, null, null, null, null, null, null));

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_2350_2550_добора_комплан_длина_2700_расчёт_выполняется() {
        FrameExtensionsType doborKomplanarType = TestEntities.frameExtensionsType(40L, "FET-011");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborKomplanarType);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2450), true, leafType);
        LinerDimensionOption doborKomplanarLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2700), BigDecimal.valueOf(2350), BigDecimal.valueOf(2550), true, doborKomplanarType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborKomplanarLengthRange));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameExtensionsTypeId(40L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_2600_2850_ширина_100_140_190_длина_3000_расчёт_выполняется() {
        FrameExtensionsType doborKomplanarType = TestEntities.frameExtensionsType(40L, "FET-008");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborKomplanarType);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2700), true, leafType);
        LinerDimensionOption doborKomplanarLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(3000), BigDecimal.valueOf(2600), BigDecimal.valueOf(2850), true, doborKomplanarType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborKomplanarLengthRange));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameExtensionsTypeId(40L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_2600_2850_ширина_400_600_800_длина_2950_расчёт_выполняется() {
        FrameExtensionsType doborKomplanarType = TestEntities.frameExtensionsType(41L, "FET-011");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborKomplanarType);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2700), true, leafType);
        LinerDimensionOption doborKomplanarLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2950), BigDecimal.valueOf(2600), BigDecimal.valueOf(2850), true, doborKomplanarType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborKomplanarLengthRange));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameExtensionsTypeId(41L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_2900_2950_добора_комплан_недоступна_возвращает_400() {
        // В диапазоне [2900, 2950] недоступна ни одна ширина добора «КОМПЛАНАР» — единственная
        // допустимая для этой ширины опция длины в этой зоне (из соседнего диапазона [2600, 2850])
        // эту высоту не покрывает.
        FrameExtensionsType doborKomplanarType = TestEntities.frameExtensionsType(40L, "FET-008");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborKomplanarType);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2925), true, leafType);
        LinerDimensionOption doborKomplanarLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(3000), BigDecimal.valueOf(2600), BigDecimal.valueOf(2850), true, doborKomplanarType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborKomplanarLengthRange));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_полотна_в_разрыве_между_диапазонами_длины_добора_комплан_возвращает_400() {
        FrameExtensionsType doborKomplanarType = TestEntities.frameExtensionsType(40L, "FET-009");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborKomplanarType);
        // 2260 не попадает ни в [2150, 2250], ни в [2300, 2300] — разрыв между диапазонами добора «КОМПЛАНАР».
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2260), true, leafType);
        LinerDimensionOption doborKomplanarLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2400), BigDecimal.valueOf(2150), BigDecimal.valueOf(2250), true, doborKomplanarType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborKomplanarLengthRange));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void длина_добора_комплан_выбрана_без_высоты_полотна_возвращает_400() {
        FrameExtensionsType doborKomplanarType = TestEntities.frameExtensionsType(40L, "FET-012");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborKomplanarType);
        LinerDimensionOption doborKomplanarLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2170), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, doborKomplanarType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborKomplanarLengthRange));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void добор_комплан_без_выбранной_длины_возвращает_400() {
        FrameExtensionsType doborKomplanarType = TestEntities.frameExtensionsType(40L, "FET-013");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborKomplanarType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null, ComponentSelectionDto.EMPTY);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void цена_добора_комплан_не_зависит_от_выбранной_длины() {
        FrameExtensionsType doborKomplanarType = TestEntities.frameExtensionsType(40L, "FET-008");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborKomplanarType);
        // length_option_id = null на строке цены — так же, как после миграции
        // 0075-komplanar-dobor-configuration-price-length-decouple.
        ConfigurationPrice price = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(2390), BigDecimal.valueOf(1366), doborKomplanarType, null, null, null, null);
        LinerDimensionOption leafHeightA =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);
        LinerDimensionOption doborKomplanarLengthA = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2170), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, doborKomplanarType);
        LinerDimensionOption leafHeightB =
                TestEntities.linerDimensionOption(1001L, heightType, BigDecimal.valueOf(2700), true, leafType);
        LinerDimensionOption doborKomplanarLengthB = TestEntities.linerDimensionOptionRange(
                2001L, lengthType, BigDecimal.valueOf(3000), BigDecimal.valueOf(2600), BigDecimal.valueOf(2850), true, doborKomplanarType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeightA));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborKomplanarLengthA));
        when(linerDimensionOptionRepository.findById(1001L)).thenReturn(Optional.of(leafHeightB));
        when(linerDimensionOptionRepository.findById(2001L)).thenReturn(Optional.of(doborKomplanarLengthB));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameExtensionsTypeId(40L)).thenReturn(List.of(price));

        PricingRequestDto requestA = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));
        PricingRequestDto requestB = new PricingRequestDto(
                new ComponentSelectionDto(null, 1001L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2001L, null, null, null, null, null, null, null, null));

        ComponentPriceDto frameExtensionsA = service.calculate(10L, requestA).components().stream()
                .filter(c -> c.component().equals("frameExtensions"))
                .findFirst()
                .orElseThrow();
        ComponentPriceDto frameExtensionsB = service.calculate(10L, requestB).components().stream()
                .filter(c -> c.component().equals("frameExtensions"))
                .findFirst()
                .orElseThrow();

        assertThat(frameExtensionsA.retailPrice()).isEqualByComparingTo("2390");
        assertThat(frameExtensionsA.dealerPrice()).isEqualByComparingTo("1366");
        assertThat(frameExtensionsB.retailPrice()).isEqualByComparingTo(frameExtensionsA.retailPrice());
        assertThat(frameExtensionsB.dealerPrice()).isEqualByComparingTo(frameExtensionsA.dealerPrice());
    }

    @Test
    void высота_полотна_внутри_диапазона_длины_наличника_модо_онда_расчёт_выполняется() {
        DoorCasingType modoType = TestEntities.doorCasingType(8L, "DCT-003");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, modoType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);
        LinerDimensionOption modoLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2250), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, modoType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(modoLengthRange));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(8L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_вне_диапазона_длины_наличника_модо_онда_возвращает_400() {
        DoorCasingType modoType = TestEntities.doorCasingType(8L, "DCT-003");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, modoType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2450), true, leafType);
        LinerDimensionOption modoLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2250), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, modoType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(modoLengthRange));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void длина_наличника_модо_онда_выбрана_без_высоты_полотна_возвращает_400() {
        DoorCasingType modoType = TestEntities.doorCasingType(8L, "DCT-003");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, modoType, null);
        LinerDimensionOption modoLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2250), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, modoType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(modoLengthRange));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_полотна_в_разрыве_между_диапазонами_длины_наличника_модо_онда_возвращает_400() {
        DoorCasingType modoType = TestEntities.doorCasingType(8L, "DCT-003");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, modoType, null);
        // 2260 не попадает ни в [2150, 2250], ни в [2300, 2300] — разрыв между диапазонами наличника.
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2260), true, leafType);
        LinerDimensionOption modoLengthRange = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2400), BigDecimal.valueOf(2150), BigDecimal.valueOf(2250), true, modoType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(modoLengthRange));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_полотна_ровно_2300_проходит_диапазон_точку_длины_наличника_модо_онда() {
        DoorCasingType modoType = TestEntities.doorCasingType(8L, "DCT-003");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, modoType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2300), true, leafType);
        // Диапазон-точка: value=2700, min_value=max_value=2300 — для наличников «Модо»/«Онда» точка делит
        // то же значение (2700), что и соседний диапазон [2350, 2550], а не диапазон ниже, как у добора «ТС».
        LinerDimensionOption modoLengthPoint = TestEntities.linerDimensionOptionRange(
                2001L, lengthType, BigDecimal.valueOf(2700), BigDecimal.valueOf(2300), BigDecimal.valueOf(2300), true, modoType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2001L)).thenReturn(Optional.of(modoLengthPoint));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(8L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2001L, null, null, null, null, null, null, null, null),
                null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void наличник_модо_онда_без_выбранной_длины_возвращает_400() {
        DoorCasingType modoType = TestEntities.doorCasingType(8L, "DCT-003");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, modoType, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, ComponentSelectionDto.EMPTY, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void наличник_модо_онда_без_выбранной_длины_возвращает_400_даже_если_высота_полотна_выбрана() {
        DoorCasingType modoType = TestEntities.doorCasingType(8L, "DCT-003");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, modoType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                ComponentSelectionDto.EMPTY,
                null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void другой_наличник_без_длины_не_является_ошибкой() {
        // После link-komplanar-casing-length-to-leaf-height все 9 существующих кодов door_casing_type
        // (DCT-001–DCT-009) входят в LENGTH_RANGE_DOOR_CASING_TYPE_CODES, поэтому для проверки поведения
        // вне набора используется гипотетический код, отсутствующий в каталоге.
        DoorCasingType otherType = TestEntities.doorCasingType(9L, "DCT-999");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, otherType, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(9L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, ComponentSelectionDto.EMPTY, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void цена_наличника_модо_онда_не_зависит_от_выбранной_длины() {
        DoorCasingType modoType = TestEntities.doorCasingType(8L, "DCT-003");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, modoType, null);
        // length_option_id = null на строке цены — так же, как после миграции 0071-modo-onda-configuration-price-length-decouple.
        ConfigurationPrice price = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1898), BigDecimal.valueOf(1084), modoType, null, null, null, null);
        LinerDimensionOption leafHeightA =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);
        LinerDimensionOption modoLengthA = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2250), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, modoType);
        LinerDimensionOption leafHeightB =
                TestEntities.linerDimensionOption(1001L, heightType, BigDecimal.valueOf(2400), true, leafType);
        LinerDimensionOption modoLengthB = TestEntities.linerDimensionOptionRange(
                2001L, lengthType, BigDecimal.valueOf(2700), BigDecimal.valueOf(2350), BigDecimal.valueOf(2550), true, modoType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeightA));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(modoLengthA));
        when(linerDimensionOptionRepository.findById(1001L)).thenReturn(Optional.of(leafHeightB));
        when(linerDimensionOptionRepository.findById(2001L)).thenReturn(Optional.of(modoLengthB));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(8L)).thenReturn(List.of(price));

        PricingRequestDto requestA = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);
        PricingRequestDto requestB = new PricingRequestDto(
                new ComponentSelectionDto(null, 1001L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2001L, null, null, null, null, null, null, null, null),
                null);

        ComponentPriceDto doorCasingA = service.calculate(10L, requestA).components().stream()
                .filter(c -> c.component().equals("doorCasing"))
                .findFirst()
                .orElseThrow();
        ComponentPriceDto doorCasingB = service.calculate(10L, requestB).components().stream()
                .filter(c -> c.component().equals("doorCasing"))
                .findFirst()
                .orElseThrow();

        assertThat(doorCasingA.retailPrice()).isEqualByComparingTo("1898");
        assertThat(doorCasingA.dealerPrice()).isEqualByComparingTo("1084");
        assertThat(doorCasingB.retailPrice()).isEqualByComparingTo(doorCasingA.retailPrice());
        assertThat(doorCasingB.dealerPrice()).isEqualByComparingTo(doorCasingA.dealerPrice());
    }

    @Test
    void высота_полотна_1900_2100_наличника_эво_короба_компланар_длина_2250_расчёт_выполняется() {
        DoorCasingType evoType = TestEntities.doorCasingType(20L, "DCT-001");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, evoType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);
        LinerDimensionOption evoLength = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2250), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, evoType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(evoLength));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(20L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_2150_2250_наличника_эво_короба_компланар_длина_2400_расчёт_выполняется() {
        DoorCasingType evoType = TestEntities.doorCasingType(20L, "DCT-001");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, evoType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2200), true, leafType);
        LinerDimensionOption evoLength = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2400), BigDecimal.valueOf(2150), BigDecimal.valueOf(2250), true, evoType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(evoLength));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(20L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_2150_2250_наличника_авеню_короба_компланар_длина_2700_расчёт_выполняется() {
        // Та же высота полотна, что и у «Эво» в предыдущем тесте, но у «Авеню» в этом диапазоне
        // допустима другая длина (2700, а не 2400) — разные наличники короба «Компланар» имеют
        // разные допустимые длины в одном и том же диапазоне высоты.
        DoorCasingType avenueType = TestEntities.doorCasingType(21L, "DCT-002");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, avenueType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2200), true, leafType);
        LinerDimensionOption avenueLength = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2700), BigDecimal.valueOf(2150), BigDecimal.valueOf(2250), true, avenueType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(avenueLength));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(21L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_ровно_2300_наличника_ария_короба_компланар_длина_2700_расчёт_выполняется() {
        DoorCasingType ariyaType = TestEntities.doorCasingType(25L, "DCT-008");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, ariyaType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2300), true, leafType);
        LinerDimensionOption ariyaLengthPoint = TestEntities.linerDimensionOptionRange(
                2001L, lengthType, BigDecimal.valueOf(2700), BigDecimal.valueOf(2300), BigDecimal.valueOf(2300), true, ariyaType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2001L)).thenReturn(Optional.of(ariyaLengthPoint));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(25L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2001L, null, null, null, null, null, null, null, null),
                null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_2350_2550_наличника_аура_короба_компланар_длина_2700_расчёт_выполняется() {
        DoorCasingType auraType = TestEntities.doorCasingType(23L, "DCT-006");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, auraType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2450), true, leafType);
        LinerDimensionOption auraLength = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2700), BigDecimal.valueOf(2350), BigDecimal.valueOf(2550), true, auraType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(auraLength));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(23L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_2600_2850_наличника_эво_короба_компланар_длина_3000_расчёт_выполняется() {
        DoorCasingType evoType = TestEntities.doorCasingType(20L, "DCT-001");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, evoType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2700), true, leafType);
        LinerDimensionOption evoLength = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(3000), BigDecimal.valueOf(2600), BigDecimal.valueOf(2850), true, evoType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(evoLength));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(20L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void высота_полотна_2600_2850_наличника_авеню_короба_компланар_недоступна_возвращает_400() {
        // В диапазоне [2600, 2850] у «Авеню» (в отличие от «Эво») нет опции длины — любая допустимая
        // для «Авеню» опция длины (здесь — из соседнего диапазона [2350, 2550]) не покрывает эту высоту.
        DoorCasingType avenueType = TestEntities.doorCasingType(21L, "DCT-002");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, avenueType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2700), true, leafType);
        LinerDimensionOption avenueLength = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2700), BigDecimal.valueOf(2350), BigDecimal.valueOf(2550), true, avenueType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(avenueLength));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_полотна_2900_2950_наличника_эво_короба_компланар_недоступна_возвращает_400() {
        // В диапазоне [2900, 2950] недоступен даже «Эво» — единственная допустимая для него опция
        // длины в этой зоне (из соседнего диапазона [2600, 2850]) эту высоту не покрывает.
        DoorCasingType evoType = TestEntities.doorCasingType(20L, "DCT-001");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, evoType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2925), true, leafType);
        LinerDimensionOption evoLength = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(3000), BigDecimal.valueOf(2600), BigDecimal.valueOf(2850), true, evoType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(evoLength));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void высота_полотна_в_разрыве_между_диапазонами_наличника_компланар_возвращает_400() {
        DoorCasingType avenueType = TestEntities.doorCasingType(21L, "DCT-002");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, avenueType, null);
        // 2260 не попадает ни в [2150, 2250], ни в [2300, 2300] — разрыв между диапазонами наличника.
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2260), true, leafType);
        LinerDimensionOption avenueLength = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2700), BigDecimal.valueOf(2150), BigDecimal.valueOf(2250), true, avenueType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(avenueLength));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void длина_наличника_компланар_выбрана_без_высоты_полотна_возвращает_400() {
        DoorCasingType auraType = TestEntities.doorCasingType(23L, "DCT-006");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, auraType, null);
        LinerDimensionOption auraLength = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2250), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, auraType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(auraLength));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void наличник_ария_без_выбранной_длины_возвращает_400() {
        DoorCasingType ariyaType = TestEntities.doorCasingType(25L, "DCT-008");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, ariyaType, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, ComponentSelectionDto.EMPTY, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void реверс_вариант_наличника_авеню_проверяется_по_тому_же_диапазону_что_и_обычный() {
        DoorCasingType avenueReverseType = TestEntities.doorCasingType(22L, "DCT-005");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration =
                TestEntities.doorConfigurationReverse(10L, leafType, null, null, avenueReverseType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2200), true, leafType);
        LinerDimensionOption avenueReverseLength = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2700), BigDecimal.valueOf(2150), BigDecimal.valueOf(2250), true, avenueReverseType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(avenueReverseLength));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(22L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void реверс_вариант_наличника_авеню_вне_диапазона_возвращает_400() {
        DoorCasingType avenueReverseType = TestEntities.doorCasingType(22L, "DCT-005");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration =
                TestEntities.doorConfigurationReverse(10L, leafType, null, null, avenueReverseType, null);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2700), true, leafType);
        LinerDimensionOption avenueReverseLength = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2700), BigDecimal.valueOf(2150), BigDecimal.valueOf(2250), true, avenueReverseType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(avenueReverseLength));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void цена_наличника_ария_короба_компланар_не_зависит_от_выбранной_длины() {
        DoorCasingType ariyaType = TestEntities.doorCasingType(25L, "DCT-008");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, ariyaType, null);
        // length_option_id = null на строке цены — так же, как после миграции
        // 0073-komplanar-casing-configuration-price-length-decouple.
        ConfigurationPrice price = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(4165), BigDecimal.valueOf(2379), ariyaType, null, null, null, null);
        LinerDimensionOption leafHeightA =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);
        LinerDimensionOption ariyaLengthA = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2250), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, ariyaType);
        LinerDimensionOption leafHeightB =
                TestEntities.linerDimensionOption(1001L, heightType, BigDecimal.valueOf(2450), true, leafType);
        LinerDimensionOption ariyaLengthB = TestEntities.linerDimensionOptionRange(
                2001L, lengthType, BigDecimal.valueOf(2700), BigDecimal.valueOf(2350), BigDecimal.valueOf(2550), true, ariyaType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeightA));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(ariyaLengthA));
        when(linerDimensionOptionRepository.findById(1001L)).thenReturn(Optional.of(leafHeightB));
        when(linerDimensionOptionRepository.findById(2001L)).thenReturn(Optional.of(ariyaLengthB));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(25L)).thenReturn(List.of(price));

        PricingRequestDto requestA = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null),
                null);
        PricingRequestDto requestB = new PricingRequestDto(
                new ComponentSelectionDto(null, 1001L, null, null, null, null, null, null, null),
                null, null,
                new ComponentSelectionDto(2001L, null, null, null, null, null, null, null, null),
                null);

        ComponentPriceDto doorCasingA = service.calculate(10L, requestA).components().stream()
                .filter(c -> c.component().equals("doorCasing"))
                .findFirst()
                .orElseThrow();
        ComponentPriceDto doorCasingB = service.calculate(10L, requestB).components().stream()
                .filter(c -> c.component().equals("doorCasing"))
                .findFirst()
                .orElseThrow();

        assertThat(doorCasingA.retailPrice()).isEqualByComparingTo("4165");
        assertThat(doorCasingA.dealerPrice()).isEqualByComparingTo("2379");
        assertThat(doorCasingB.retailPrice()).isEqualByComparingTo(doorCasingA.retailPrice());
        assertThat(doorCasingB.dealerPrice()).isEqualByComparingTo(doorCasingA.dealerPrice());
    }

    @Test
    void добор_тс_без_выбранной_длины_возвращает_400() {
        FrameExtensionsType doborTsType = TestEntities.frameExtensionsType(6L, "FET-004");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborTsType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null, ComponentSelectionDto.EMPTY);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void добор_тс_без_выбранной_длины_возвращает_400_даже_если_высота_полотна_выбрана() {
        FrameExtensionsType doborTsType = TestEntities.frameExtensionsType(6L, "FET-004");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborTsType);
        LinerDimensionOption leafHeight =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeight));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                ComponentSelectionDto.EMPTY);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void добор_комплан_без_длины_не_является_ошибкой() {
        // После link-komplanar-dobor-length-to-leaf-height все 10 реально достижимых через каталог кодов
        // frame_extensions_type (добор «ТС» FET-004–007 и добор «КОМПЛАНАР» FET-008–013) входят в
        // LENGTH_RANGE_FRAME_EXTENSIONS_TYPE_CODES, поэтому для проверки поведения вне набора используется
        // код категорийного заголовка (FET-001), который существует в справочнике, но ни разу не связан
        // ни с одной door_configuration и недостижим через UI.
        FrameExtensionsType otherType = TestEntities.frameExtensionsType(7L, "FET-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, otherType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameExtensionsTypeId(7L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null, ComponentSelectionDto.EMPTY);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
    }

    @Test
    void надбавка_за_реверс_применяется_к_цене_полотна_если_короб_реверсивный() {
        FrameType frameType = TestEntities.frameType(3L);
        DoorConfiguration configuration = TestEntities.doorConfigurationReverse(10L, leafType, frameType, null, null, null);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request =
                new PricingRequestDto(ComponentSelectionDto.EMPTY, ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto leaf = response.components().stream()
                .filter(c -> c.component().equals("leaf"))
                .findFirst()
                .orElseThrow();
        assertThat(leaf.priced()).isTrue();
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1100");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("990");
    }

    @Test
    void надбавка_за_реверс_округляется_до_целого() {
        FrameType frameType = TestEntities.frameType(3L);
        DoorConfiguration configuration = TestEntities.doorConfigurationReverse(10L, leafType, frameType, null, null, null);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(33264), BigDecimal.valueOf(19007), leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request =
                new PricingRequestDto(ComponentSelectionDto.EMPTY, ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto leaf = response.components().stream()
                .filter(c -> c.component().equals("leaf"))
                .findFirst()
                .orElseThrow();
        assertThat(leaf.retailPrice()).isEqualByComparingTo("36590");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("20908");
    }

    @Test
    void надбавка_за_реверс_не_применяется_для_обычного_короба() {
        FrameType frameType = TestEntities.frameType(3L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, frameType, null, null, null);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request =
                new PricingRequestDto(ComponentSelectionDto.EMPTY, ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto leaf = response.components().stream()
                .filter(c -> c.component().equals("leaf"))
                .findFirst()
                .orElseThrow();
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1000");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("900");
    }

    @Test
    void надбавка_за_реверс_не_влияет_на_цену_короба() {
        FrameType frameType = TestEntities.frameType(3L);
        DoorConfiguration configuration = TestEntities.doorConfigurationReverse(10L, leafType, frameType, null, null, null);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);
        FramePost framePost = TestEntities.framePost(2L, BigDecimal.valueOf(500), BigDecimal.valueOf(400), frameType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));
        when(framePostRepository.findByFrameTypeId(3L)).thenReturn(List.of(framePost));

        PricingRequestDto request =
                new PricingRequestDto(ComponentSelectionDto.EMPTY, ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto framePrice = response.components().stream()
                .filter(c -> c.component().equals("frame"))
                .findFirst()
                .orElseThrow();
        assertThat(framePrice.retailPrice()).isEqualByComparingTo("500");
        assertThat(framePrice.dealerPrice()).isEqualByComparingTo("400");
        assertThat(response.totalRetailPrice()).isEqualByComparingTo("1600");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("1390");
    }

    @Test
    void произвольная_длина_совпадающая_со_стандартным_размером_не_даёт_наценки() {
        LinerDimensionType leafLengthType = TestEntities.linerDimensionType(101L, "DT-001");
        LinerDimensionOption standardLength =
                TestEntities.linerDimensionOption(1001L, leafLengthType, BigDecimal.valueOf(600), true, leafType);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of(standardLength));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, BigDecimal.valueOf(600), null, null, null, null),
                ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto leaf = response.components().stream()
                .filter(c -> c.component().equals("leaf"))
                .findFirst()
                .orElseThrow();
        assertThat(leaf.priced()).isTrue();
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1000");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("900");
    }

    @Test
    void произвольная_длина_совпадающая_с_правилом_даёт_наценку() {
        LinerDimensionType leafLengthType = TestEntities.linerDimensionType(101L, "DT-001");
        DimensionSurchargeRule rule = TestEntities.dimensionSurchargeRule(
                1L, leafLengthType, BigDecimal.valueOf(950), BigDecimal.valueOf(20));
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(linerDimensionTypeRepository.findByCode("DT-001")).thenReturn(Optional.of(leafLengthType));
        when(dimensionSurchargeRuleRepository.findByLinerDimensionTypeIdAndValue(101L, BigDecimal.valueOf(950)))
                .thenReturn(Optional.of(rule));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, BigDecimal.valueOf(950), null, null, null, null),
                ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto leaf = response.components().stream()
                .filter(c -> c.component().equals("leaf"))
                .findFirst()
                .orElseThrow();
        assertThat(leaf.priced()).isTrue();
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1200");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("1080");
    }

    @Test
    void неизвестное_произвольное_значение_отклоняется_400() {
        LinerDimensionType leafLengthType = TestEntities.linerDimensionType(101L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(linerDimensionTypeRepository.findByCode("DT-001")).thenReturn(Optional.of(leafLengthType));
        when(dimensionSurchargeRuleRepository.findByLinerDimensionTypeIdAndValue(101L, BigDecimal.valueOf(999)))
                .thenReturn(Optional.empty());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, BigDecimal.valueOf(999), null, null, null, null),
                ComponentSelectionDto.EMPTY, null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void одновременно_id_опции_и_произвольное_значение_для_одной_оси_отклоняется_400() {
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(1000L, null, null, null, BigDecimal.valueOf(950), null, null, null, null),
                ComponentSelectionDto.EMPTY, null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void произвольное_значение_для_не_leaf_компонента_отклоняется_400() {
        EdgeType edgeType = TestEntities.edgeType(3L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, edgeType, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null,
                new ComponentSelectionDto(null, null, null, null, BigDecimal.valueOf(950), null, null, null, null),
                null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void наценка_за_высоту_и_надбавка_за_реверс_применяются_последовательно() {
        LinerDimensionType leafHeightType = TestEntities.linerDimensionType(102L, "DT-002");
        DimensionSurchargeRule rule = TestEntities.dimensionSurchargeRule(
                1L, leafHeightType, BigDecimal.valueOf(2200), BigDecimal.valueOf(20));
        DoorConfiguration configuration = TestEntities.doorConfigurationReverse(10L, leafType, null, null, null, null);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(linerDimensionTypeRepository.findByCode("DT-002")).thenReturn(Optional.of(leafHeightType));
        when(dimensionSurchargeRuleRepository.findByLinerDimensionTypeIdAndValue(102L, BigDecimal.valueOf(2200)))
                .thenReturn(Optional.of(rule));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, null, BigDecimal.valueOf(2200), null, null, null),
                ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto leaf = response.components().stream()
                .filter(c -> c.component().equals("leaf"))
                .findFirst()
                .orElseThrow();
        assertThat(leaf.priced()).isTrue();
        // Наценка отсутствует по длине (шаг пропускается), поэтому высота (+20%) и реверс (+10%) применяются
        // друг за другом: 1000 -> округление(1000*1.20)=1200 -> округление(1200*1.10)=1320.
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1320");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("1188");
    }

    @Test
    void наценка_за_длину_высоту_и_реверс_дают_иной_результат_чем_прежнее_единое_перемножение_коэффициентов() {
        LinerDimensionType leafLengthType = TestEntities.linerDimensionType(101L, "DT-001");
        LinerDimensionType leafHeightType = TestEntities.linerDimensionType(102L, "DT-002");
        DimensionSurchargeRule lengthRule = TestEntities.dimensionSurchargeRule(
                1L, leafLengthType, BigDecimal.valueOf(950), BigDecimal.valueOf(20));
        DimensionSurchargeRule heightRule = TestEntities.dimensionSurchargeRule(
                2L, leafHeightType, BigDecimal.valueOf(1900), BigDecimal.valueOf(30));
        DoorConfiguration configuration = TestEntities.doorConfigurationReverse(10L, leafType, null, null, null, null);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1007), BigDecimal.valueOf(907), leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(linerDimensionTypeRepository.findByCode("DT-001")).thenReturn(Optional.of(leafLengthType));
        when(linerDimensionTypeRepository.findByCode("DT-002")).thenReturn(Optional.of(leafHeightType));
        when(dimensionSurchargeRuleRepository.findByLinerDimensionTypeIdAndValue(101L, BigDecimal.valueOf(950)))
                .thenReturn(Optional.of(lengthRule));
        when(dimensionSurchargeRuleRepository.findByLinerDimensionTypeIdAndValue(102L, BigDecimal.valueOf(1900)))
                .thenReturn(Optional.of(heightRule));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, BigDecimal.valueOf(950), BigDecimal.valueOf(1900), null, null, null),
                ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto leaf = response.components().stream()
                .filter(c -> c.component().equals("leaf"))
                .findFirst()
                .orElseThrow();
        assertThat(leaf.priced()).isTrue();
        // Последовательно, с округлением после каждого шага:
        // retail: 1007 -> округление(1007*1.20)=1208 -> округление(1208*1.30)=1570 -> округление(1570*1.10)=1727.
        // dealer:  907 -> округление(907*1.20)=1088 -> округление(1088*1.30)=1414 -> округление(1414*1.10)=1555.
        // Прежнее единое перемножение коэффициентов (без промежуточного округления) дало бы другой результат:
        // retail: округление(1007*1.20*1.30)=1571 -> округление(1571*1.10)=1728;
        // dealer: округление(907*1.20*1.30)=1415 -> округление(1415*1.10)=1557.
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1727");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("1555");
    }

    @Test
    void кромка_без_выбранной_высоты_не_проверяется() {
        EdgeType edgeType = TestEntities.edgeType(3L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, edgeType, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByEdgeTypeId(3L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, ComponentSelectionDto.EMPTY, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.components()).hasSize(2);
        assertThat(response.components()).allMatch(c -> !c.priced());
    }

    @Test
    void количество_не_указано_цена_наличника_как_за_одну_единицу() {
        DoorCasingType doorCasingType = TestEntities.doorCasingType(5L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, doorCasingType, null);
        ConfigurationPrice price = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(500), BigDecimal.valueOf(400), doorCasingType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(5L)).thenReturn(List.of(price));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, ComponentSelectionDto.EMPTY, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto doorCasing = response.components().stream()
                .filter(c -> c.component().equals("doorCasing"))
                .findFirst()
                .orElseThrow();
        assertThat(doorCasing.priced()).isTrue();
        assertThat(doorCasing.retailPrice()).isEqualByComparingTo("500");
        assertThat(doorCasing.dealerPrice()).isEqualByComparingTo("400");
        assertThat(doorCasing.baseRetailPrice()).isEqualByComparingTo("500");
        assertThat(doorCasing.baseDealerPrice()).isEqualByComparingTo("400");
    }

    @Test
    void количество_умножает_цену_наличника() {
        DoorCasingType doorCasingType = TestEntities.doorCasingType(5L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, doorCasingType, null);
        ConfigurationPrice price = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(500), BigDecimal.valueOf(400), doorCasingType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(5L)).thenReturn(List.of(price));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null,
                new ComponentSelectionDto(null, null, null, null, null, null, 3, null, null), null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto doorCasing = response.components().stream()
                .filter(c -> c.component().equals("doorCasing"))
                .findFirst()
                .orElseThrow();
        assertThat(doorCasing.retailPrice()).isEqualByComparingTo("1500");
        assertThat(doorCasing.dealerPrice()).isEqualByComparingTo("1200");
        assertThat(doorCasing.baseRetailPrice()).isEqualByComparingTo("1500");
        assertThat(doorCasing.baseDealerPrice()).isEqualByComparingTo("1200");
    }

    @Test
    void количество_умножает_цену_добора() {
        FrameExtensionsType frameExtensionsType = TestEntities.frameExtensionsType(6L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, frameExtensionsType);
        ConfigurationPrice price = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(300), BigDecimal.valueOf(250), frameExtensionsType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameExtensionsTypeId(6L)).thenReturn(List.of(price));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null,
                new ComponentSelectionDto(null, null, null, null, null, null, 2, null, null));

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto frameExtensions = response.components().stream()
                .filter(c -> c.component().equals("frameExtensions"))
                .findFirst()
                .orElseThrow();
        assertThat(frameExtensions.retailPrice()).isEqualByComparingTo("600");
        assertThat(frameExtensions.dealerPrice()).isEqualByComparingTo("500");
    }

    @Test
    void цена_добора_тс_не_зависит_от_выбранной_длины() {
        FrameExtensionsType doborTsType = TestEntities.frameExtensionsType(6L, "FET-004");
        LinerDimensionType heightType = TestEntities.linerDimensionType(300L, "DT-002");
        LinerDimensionType lengthType = TestEntities.linerDimensionType(400L, "DT-001");
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, doborTsType);
        // length_option_id = null на строке цены — так же, как после миграции 0067-dobor-ts-configuration-price-length-decouple.
        ConfigurationPrice price = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(300), BigDecimal.valueOf(250), doborTsType, null, null, null, null);
        LinerDimensionOption leafHeightA =
                TestEntities.linerDimensionOption(1000L, heightType, BigDecimal.valueOf(2000), true, leafType);
        LinerDimensionOption doborTsLengthA = TestEntities.linerDimensionOptionRange(
                2000L, lengthType, BigDecimal.valueOf(2170), BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), true, doborTsType);
        LinerDimensionOption leafHeightB =
                TestEntities.linerDimensionOption(1001L, heightType, BigDecimal.valueOf(2400), true, leafType);
        LinerDimensionOption doborTsLengthB = TestEntities.linerDimensionOptionRange(
                2001L, lengthType, BigDecimal.valueOf(2700), BigDecimal.valueOf(2350), BigDecimal.valueOf(2550), true, doborTsType);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(leafHeightA));
        when(linerDimensionOptionRepository.findById(2000L)).thenReturn(Optional.of(doborTsLengthA));
        when(linerDimensionOptionRepository.findById(1001L)).thenReturn(Optional.of(leafHeightB));
        when(linerDimensionOptionRepository.findById(2001L)).thenReturn(Optional.of(doborTsLengthB));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByFrameExtensionsTypeId(6L)).thenReturn(List.of(price));

        PricingRequestDto requestA = new PricingRequestDto(
                new ComponentSelectionDto(null, 1000L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2000L, null, null, null, null, null, null, null, null));
        PricingRequestDto requestB = new PricingRequestDto(
                new ComponentSelectionDto(null, 1001L, null, null, null, null, null, null, null),
                null, null, null,
                new ComponentSelectionDto(2001L, null, null, null, null, null, null, null, null));

        ComponentPriceDto frameExtensionsA = service.calculate(10L, requestA).components().stream()
                .filter(c -> c.component().equals("frameExtensions"))
                .findFirst()
                .orElseThrow();
        ComponentPriceDto frameExtensionsB = service.calculate(10L, requestB).components().stream()
                .filter(c -> c.component().equals("frameExtensions"))
                .findFirst()
                .orElseThrow();

        assertThat(frameExtensionsA.retailPrice()).isEqualByComparingTo("300");
        assertThat(frameExtensionsA.dealerPrice()).isEqualByComparingTo("250");
        assertThat(frameExtensionsB.retailPrice()).isEqualByComparingTo(frameExtensionsA.retailPrice());
        assertThat(frameExtensionsB.dealerPrice()).isEqualByComparingTo(frameExtensionsA.dealerPrice());
    }

    @Test
    void количество_для_полотна_отклоняется_400() {
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, null, null, 1, null, null), null, null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void количество_для_короба_отклоняется_400() {
        FrameType frameType = TestEntities.frameType(3L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, frameType, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, new ComponentSelectionDto(null, null, null, null, null, null, 1, null, null), null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void неположительное_количество_отклоняется_400() {
        DoorCasingType doorCasingType = TestEntities.doorCasingType(5L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, doorCasingType, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null,
                new ComponentSelectionDto(null, null, null, null, null, null, 0, null, null), null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void количество_указано_но_цена_не_найдена_компонент_остаётся_некалькулируемым() {
        DoorCasingType doorCasingType = TestEntities.doorCasingType(5L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, doorCasingType, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(configurationPriceRepository.findByDoorCasingTypeId(5L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null,
                new ComponentSelectionDto(null, null, null, null, null, null, 2, null, null), null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto doorCasing = response.components().stream()
                .filter(c -> c.component().equals("doorCasing"))
                .findFirst()
                .orElseThrow();
        assertThat(doorCasing.priced()).isFalse();
    }

    @Test
    void наценка_за_исполнение_зеркала_применяется_между_высотой_и_реверсом() {
        LinerDimensionType leafHeightType = TestEntities.linerDimensionType(102L, "DT-002");
        DimensionSurchargeRule heightRule = TestEntities.dimensionSurchargeRule(
                1L, leafHeightType, BigDecimal.valueOf(2200), BigDecimal.valueOf(30));
        MirrorFinishType mirrorFinishType = TestEntities.mirrorFinishType(1L, BigDecimal.valueOf(40));
        MirrorFinishOption mirrorFinishOption = TestEntities.mirrorFinishOption(500L, mirrorFinishType, leafType);
        DoorConfiguration configuration = TestEntities.doorConfigurationReverse(10L, leafType, null, null, null, null);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1011), BigDecimal.valueOf(911), leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(linerDimensionTypeRepository.findByCode("DT-002")).thenReturn(Optional.of(leafHeightType));
        when(dimensionSurchargeRuleRepository.findByLinerDimensionTypeIdAndValue(102L, BigDecimal.valueOf(2200)))
                .thenReturn(Optional.of(heightRule));
        when(mirrorFinishOptionRepository.findByMirrorFinishTypeIdAndLeafTypeId(1L, 1L))
                .thenReturn(Optional.of(mirrorFinishOption));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, null, BigDecimal.valueOf(2200), null, 1L, null),
                ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto leaf = response.components().stream()
                .filter(c -> c.component().equals("leaf"))
                .findFirst()
                .orElseThrow();
        assertThat(leaf.priced()).isTrue();
        // Порядок: высота (+30%) → зеркало (+40%) → реверс (+10%), округление после каждого шага:
        // retail: 1011 -> округление(1011*1.30)=1314 -> округление(1314*1.40)=1840 -> округление(1840*1.10)=2024.
        // dealer:  911 -> округление(911*1.30)=1184 -> округление(1184*1.40)=1658 -> округление(1658*1.10)=1824.
        // Обратный порядок (высота → реверс → зеркало) дал бы другой результат из-за промежуточного округления:
        // retail: округление(1314*1.10)=1445 -> округление(1445*1.40)=2023 (не 2024);
        // dealer: округление(1184*1.10)=1302 -> округление(1302*1.40)=1823 (не 1824).
        assertThat(leaf.retailPrice()).isEqualByComparingTo("2024");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("1824");
    }

    @Test
    void без_исполнения_зеркала_шаг_наценки_пропускается() {
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request =
                new PricingRequestDto(ComponentSelectionDto.EMPTY, ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto leaf = response.components().stream()
                .filter(c -> c.component().equals("leaf"))
                .findFirst()
                .orElseThrow();
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1000");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("900");
    }

    @Test
    void исполнение_зеркала_для_компонента_отличного_от_leaf_отклоняется_400() {
        EdgeType edgeType = TestEntities.edgeType(3L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, edgeType, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null,
                new ComponentSelectionDto(null, null, null, null, null, null, null, 1L, null), null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void неизвестное_или_недопустимое_для_полотна_исполнение_зеркала_отклоняется_400() {
        // Один и тот же запрос (findByMirrorFinishTypeIdAndLeafTypeId ничего не находит для этой пары) покрывает
        // оба случая из specs — «id не существует вовсе» и «существует, но для другого leaf_type»: строка
        // mirror_finish_option в обоих случаях отсутствует именно для этой пары (type, leaf), различать их
        // отдельным запросом незачем (см. change add-mirror-finish-leaf-option).
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(mirrorFinishOptionRepository.findByMirrorFinishTypeIdAndLeafTypeId(1L, 1L)).thenReturn(Optional.empty());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, null, null, null, 1L, null),
                ComponentSelectionDto.EMPTY, null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void наценка_за_вид_остекления_применяется_после_высоты_и_до_реверса() {
        LinerDimensionType leafHeightType = TestEntities.linerDimensionType(102L, "DT-002");
        DimensionSurchargeRule heightRule = TestEntities.dimensionSurchargeRule(
                1L, leafHeightType, BigDecimal.valueOf(2200), BigDecimal.valueOf(30));
        GlazingType glazingType = TestEntities.glazingType(1L, BigDecimal.valueOf(5));
        GlazingOption glazingOption = TestEntities.glazingOption(600L, glazingType, leafType);
        DoorConfiguration configuration = TestEntities.doorConfigurationReverse(10L, leafType, null, null, null, null);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1011), BigDecimal.valueOf(911), leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(linerDimensionTypeRepository.findByCode("DT-002")).thenReturn(Optional.of(leafHeightType));
        when(dimensionSurchargeRuleRepository.findByLinerDimensionTypeIdAndValue(102L, BigDecimal.valueOf(2200)))
                .thenReturn(Optional.of(heightRule));
        when(glazingOptionRepository.findByGlazingTypeIdAndLeafTypeId(1L, 1L)).thenReturn(Optional.of(glazingOption));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, null, BigDecimal.valueOf(2200), null, null, 1L),
                ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto leaf = response.components().stream()
                .filter(c -> c.component().equals("leaf"))
                .findFirst()
                .orElseThrow();
        assertThat(leaf.priced()).isTrue();
        // Порядок: высота (+30%) → остекление (+5%) → реверс (+10%), округление после каждого шага:
        // retail: 1011 -> округление(1011*1.30)=1314 -> округление(1314*1.05)=1380 -> округление(1380*1.10)=1518.
        // dealer:  911 -> округление(911*1.30)=1184 -> округление(1184*1.05)=1243 -> округление(1243*1.10)=1367.
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1518");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("1367");
    }

    @Test
    void базовое_прозрачное_остекление_не_добавляет_наценку() {
        GlazingType glazingType = TestEntities.glazingType(1L, BigDecimal.ZERO);
        GlazingOption glazingOption = TestEntities.glazingOption(600L, glazingType, leafType);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(glazingOptionRepository.findByGlazingTypeIdAndLeafTypeId(1L, 1L)).thenReturn(Optional.of(glazingOption));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, null, null, null, null, 1L),
                ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto leaf = response.components().stream()
                .filter(c -> c.component().equals("leaf"))
                .findFirst()
                .orElseThrow();
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1000");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("900");
    }

    @Test
    void вид_остекления_для_компонента_отличного_от_leaf_отклоняется_400() {
        EdgeType edgeType = TestEntities.edgeType(3L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, edgeType, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null,
                new ComponentSelectionDto(null, null, null, null, null, null, null, null, 1L), null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void неизвестный_или_недопустимый_для_полотна_вид_остекления_отклоняется_400() {
        // Как и у исполнения зеркала (findByGlazingTypeIdAndLeafTypeId ничего не находит для этой пары),
        // один и тот же запрос покрывает оба случая из specs — id не существует вовсе, и id существует, но
        // для другого leaf_type (см. change add-glazing-price-surcharge).
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(glazingOptionRepository.findByGlazingTypeIdAndLeafTypeId(1L, 1L)).thenReturn(Optional.empty());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, null, null, null, null, 1L),
                ComponentSelectionDto.EMPTY, null, null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void одна_позиция_фурнитуры_добавляется_к_итогу() {
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);
        HardwareCategory category = TestEntities.hardwareCategory(300L);
        HardwareType type = TestEntities.hardwareType(301L, category);
        HardwareOption option =
                TestEntities.hardwareOption(302L, "хром", BigDecimal.valueOf(1000), BigDecimal.valueOf(700), type);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(hardwareOptionRepository.findById(302L)).thenReturn(Optional.of(option));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null, null, List.of(new HardwareSelectionDto(302L, 2)));

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.totalRetailPrice()).isEqualByComparingTo("2000");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("1400");
        assertThat(response.hardware()).hasSize(1);
        HardwarePriceDto hardwarePrice = response.hardware().get(0);
        assertThat(hardwarePrice.quantity()).isEqualTo(2);
        assertThat(hardwarePrice.colourName()).isEqualTo("хром");
        assertThat(hardwarePrice.retailPrice()).isEqualByComparingTo("2000");
        assertThat(hardwarePrice.dealerPrice()).isEqualByComparingTo("1400");
    }

    @Test
    void количество_фурнитуры_по_умолчанию_равно_1() {
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);
        HardwareCategory category = TestEntities.hardwareCategory(300L);
        HardwareType type = TestEntities.hardwareType(301L, category);
        HardwareOption option =
                TestEntities.hardwareOption(302L, "хром", BigDecimal.valueOf(1000), BigDecimal.valueOf(700), type);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(hardwareOptionRepository.findById(302L)).thenReturn(Optional.of(option));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null, null, List.of(new HardwareSelectionDto(302L, null)));

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.hardware()).hasSize(1);
        assertThat(response.hardware().get(0).quantity()).isEqualTo(1);
        assertThat(response.totalRetailPrice()).isEqualByComparingTo("1000");
    }

    @Test
    void несколько_позиций_фурнитуры_с_одинаковым_вариантом_суммируются_независимо() {
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);
        HardwareCategory category = TestEntities.hardwareCategory(300L);
        HardwareType type = TestEntities.hardwareType(301L, category);
        HardwareOption option =
                TestEntities.hardwareOption(302L, "хром", BigDecimal.valueOf(1000), BigDecimal.valueOf(700), type);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(hardwareOptionRepository.findById(302L)).thenReturn(Optional.of(option));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null, null,
                List.of(new HardwareSelectionDto(302L, 1), new HardwareSelectionDto(302L, 3)));

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.hardware()).hasSize(2);
        assertThat(response.totalRetailPrice()).isEqualByComparingTo("4000");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("2800");
    }

    @Test
    void несуществующий_вариант_фурнитуры_возвращает_400() {
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(hardwareOptionRepository.findById(999L)).thenReturn(Optional.empty());

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null, null, List.of(new HardwareSelectionDto(999L, 1)));

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void нулевое_или_отрицательное_количество_фурнитуры_недопустимо() {
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);
        HardwareCategory category = TestEntities.hardwareCategory(300L);
        HardwareType type = TestEntities.hardwareType(301L, category);
        HardwareOption option =
                TestEntities.hardwareOption(302L, "хром", BigDecimal.valueOf(1000), BigDecimal.valueOf(700), type);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(hardwareOptionRepository.findById(302L)).thenReturn(Optional.of(option));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null, null, List.of(new HardwareSelectionDto(302L, 0)));

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void фурнитура_не_получает_надбавку_за_реверс_в_отличие_от_полотна() {
        FrameType frameType = TestEntities.frameType(3L);
        DoorConfiguration configuration = TestEntities.doorConfigurationReverse(10L, leafType, frameType, null, null, null);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);
        HardwareCategory category = TestEntities.hardwareCategory(300L);
        HardwareType type = TestEntities.hardwareType(301L, category);
        HardwareOption option =
                TestEntities.hardwareOption(302L, "хром", BigDecimal.valueOf(1000), BigDecimal.valueOf(700), type);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));
        when(hardwareOptionRepository.findById(302L)).thenReturn(Optional.of(option));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, ComponentSelectionDto.EMPTY, null, null, null,
                List.of(new HardwareSelectionDto(302L, 1)));

        PricingResponseDto response = service.calculate(10L, request);

        ComponentPriceDto leaf = response.components().stream()
                .filter(c -> c.component().equals("leaf"))
                .findFirst()
                .orElseThrow();
        // Полотно получает надбавку за реверс (1000 -> 1100), фурнитура — нет (остаётся 1000).
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1100");
        assertThat(response.hardware().get(0).retailPrice()).isEqualByComparingTo("1000");
    }

    @Test
    void расчёт_отдельного_полотна_возвращает_найденную_цену() {
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);

        when(leafTypeRepository.findById(1L)).thenReturn(Optional.of(leafType));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingResponseDto response = service.calculateForLeaf(1L, new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null, null));

        assertThat(response.components()).hasSize(1);
        ComponentPriceDto leaf = response.components().get(0);
        assertThat(leaf.component()).isEqualTo("leaf");
        assertThat(leaf.priced()).isTrue();
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1000");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("900");
        assertThat(response.totalRetailPrice()).isEqualByComparingTo("1000");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("900");
    }

    @Test
    void расчёт_отдельного_полотна_без_найденной_цены() {
        when(leafTypeRepository.findById(1L)).thenReturn(Optional.of(leafType));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        PricingResponseDto response = service.calculateForLeaf(1L, new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null, null));

        assertThat(response.components()).hasSize(1);
        assertThat(response.components().get(0).priced()).isFalse();
        assertThat(response.totalRetailPrice()).isEqualByComparingTo("0");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("0");
    }

    @Test
    void расчёт_отдельного_полотна_применяет_надбавку_за_нестандартный_размер() {
        LinerDimensionType leafHeightType = TestEntities.linerDimensionType(102L, "DT-002");
        DimensionSurchargeRule heightRule = TestEntities.dimensionSurchargeRule(
                1L, leafHeightType, BigDecimal.valueOf(2200), BigDecimal.valueOf(30));
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);

        when(leafTypeRepository.findById(1L)).thenReturn(Optional.of(leafType));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(linerDimensionTypeRepository.findByCode("DT-002")).thenReturn(Optional.of(leafHeightType));
        when(dimensionSurchargeRuleRepository.findByLinerDimensionTypeIdAndValue(102L, BigDecimal.valueOf(2200)))
                .thenReturn(Optional.of(heightRule));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, null, BigDecimal.valueOf(2200), null, null, null),
                null, null, null, null);

        PricingResponseDto response = service.calculateForLeaf(1L, request);

        ComponentPriceDto leaf = response.components().get(0);
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1300");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("1170");
    }

    @Test
    void расчёт_отдельного_полотна_без_флага_isReverse_надбавка_за_реверс_не_применяется() {
        MirrorFinishType mirrorFinishType = TestEntities.mirrorFinishType(1L, BigDecimal.valueOf(40));
        MirrorFinishOption mirrorFinishOption = TestEntities.mirrorFinishOption(500L, mirrorFinishType, leafType);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);

        when(leafTypeRepository.findById(1L)).thenReturn(Optional.of(leafType));
        when(mirrorFinishOptionRepository.findByMirrorFinishTypeIdAndLeafTypeId(1L, 1L))
                .thenReturn(Optional.of(mirrorFinishOption));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, null, null, null, 1L, null),
                null, null, null, null);

        PricingResponseDto response = service.calculateForLeaf(1L, request);

        ComponentPriceDto leaf = response.components().get(0);
        // Только надбавка за зеркало (+40%): 1000 -> 1400, 900 -> 1260. Признак isReverse не передан
        // (см. change add-standalone-leaf-pricing) — отсутствие поля равносильно false.
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1400");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("1260");
    }

    @Test
    void расчёт_отдельного_полотна_с_isReverse_true_применяет_надбавку_за_реверс() {
        MirrorFinishType mirrorFinishType = TestEntities.mirrorFinishType(1L, BigDecimal.valueOf(40));
        MirrorFinishOption mirrorFinishOption = TestEntities.mirrorFinishOption(500L, mirrorFinishType, leafType);
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);

        when(leafTypeRepository.findById(1L)).thenReturn(Optional.of(leafType));
        when(mirrorFinishOptionRepository.findByMirrorFinishTypeIdAndLeafTypeId(1L, 1L))
                .thenReturn(Optional.of(mirrorFinishOption));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(null, null, null, null, null, null, null, 1L, null),
                null, null, null, null, null, true);

        PricingResponseDto response = service.calculateForLeaf(1L, request);

        ComponentPriceDto leaf = response.components().get(0);
        // Зеркало (+40%) → реверс (+10%), округление после каждого шага (см. аналогичный тест для
        // calculate()): retail: 1000 -> округление(1000*1.40)=1400 -> округление(1400*1.10)=1540;
        // dealer: 900 -> округление(900*1.40)=1260 -> округление(1260*1.10)=1386.
        assertThat(leaf.retailPrice()).isEqualByComparingTo("1540");
        assertThat(leaf.dealerPrice()).isEqualByComparingTo("1386");
    }

    @Test
    void расчёт_отдельного_полотна_суммирует_фурнитуру() {
        ConfigurationPrice leafPrice = TestEntities.configurationPrice(
                1L, BigDecimal.valueOf(1000), BigDecimal.valueOf(900), leafType, null, null, null, null);
        HardwareCategory category = TestEntities.hardwareCategory(300L);
        HardwareType type = TestEntities.hardwareType(301L, category);
        HardwareOption option =
                TestEntities.hardwareOption(302L, "хром", BigDecimal.valueOf(1000), BigDecimal.valueOf(700), type);

        when(leafTypeRepository.findById(1L)).thenReturn(Optional.of(leafType));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));
        when(hardwareOptionRepository.findById(302L)).thenReturn(Optional.of(option));

        PricingRequestDto request = new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null, null, List.of(new HardwareSelectionDto(302L, 1)));

        PricingResponseDto response = service.calculateForLeaf(1L, request);

        assertThat(response.hardware()).hasSize(1);
        assertThat(response.totalRetailPrice()).isEqualByComparingTo("2000");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("1600");
    }

    @Test
    void расчёт_отдельного_полотна_несуществующий_тип_возвращает_404() {
        when(leafTypeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculateForLeaf(999L, new PricingRequestDto(
                ComponentSelectionDto.EMPTY, null, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void расчёт_отдельного_полотна_опция_чужого_компонента_возвращает_400() {
        FrameType frameType = TestEntities.frameType(2L);
        LinerDimensionOption frameLength =
                TestEntities.linerDimensionOption(1000L, lengthType, BigDecimal.valueOf(600), true, frameType);

        when(leafTypeRepository.findById(1L)).thenReturn(Optional.of(leafType));
        when(linerDimensionOptionRepository.findById(1000L)).thenReturn(Optional.of(frameLength));

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(1000L, null, null, null, null, null, null, null, null),
                null, null, null, null);

        assertThatThrownBy(() -> service.calculateForLeaf(1L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }
}
