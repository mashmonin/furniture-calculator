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
import com.example.furniturecalculator.domain.DoorConfiguration;
import com.example.furniturecalculator.domain.EdgeType;
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
                new PricingRequestDto(new ComponentSelectionDto(1000L, null, null, null, null, null), null, null, null, null);

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
                new PricingRequestDto(new ComponentSelectionDto(1000L, null, null, 2000L, null, null), null, null, null, null);

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
                new PricingRequestDto(new ComponentSelectionDto(1000L, null, null, 2000L, null, null), null, null, null, null);

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
                new ComponentSelectionDto(1000L, null, null, null, null, null), ComponentSelectionDto.EMPTY, null, null, null);

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
                new PricingRequestDto(new ComponentSelectionDto(1000L, null, null, null, null, null), null, null, null, null);

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
                ComponentSelectionDto.EMPTY, new ComponentSelectionDto(null, null, null, 2000L, null, null), null, null, null);

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
                ComponentSelectionDto.EMPTY, new ComponentSelectionDto(null, null, null, 2000L, null, null), null, null, null);

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
                ComponentSelectionDto.EMPTY, new ComponentSelectionDto(null, null, null, 2000L, null, null), null, null, null);

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
                new ComponentSelectionDto(null, 1000L, null, null, null, null),
                null,
                new ComponentSelectionDto(null, 2000L, null, null, null, null),
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
                new ComponentSelectionDto(null, 1000L, null, null, null, null),
                null,
                new ComponentSelectionDto(null, 2000L, null, null, null, null),
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
                new ComponentSelectionDto(null, 2000L, null, null, null, null),
                null, null);

        assertThatThrownBy(() -> service.calculate(10L, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
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
                new ComponentSelectionDto(null, null, null, null, BigDecimal.valueOf(600), null),
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
                new ComponentSelectionDto(null, null, null, null, BigDecimal.valueOf(950), null),
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
                new ComponentSelectionDto(null, null, null, null, BigDecimal.valueOf(999), null),
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
                new ComponentSelectionDto(1000L, null, null, null, BigDecimal.valueOf(950), null),
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
                new ComponentSelectionDto(null, null, null, null, BigDecimal.valueOf(950), null),
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
                new ComponentSelectionDto(null, null, null, null, null, BigDecimal.valueOf(2200)),
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
                new ComponentSelectionDto(null, null, null, null, BigDecimal.valueOf(950), BigDecimal.valueOf(1900)),
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
}
