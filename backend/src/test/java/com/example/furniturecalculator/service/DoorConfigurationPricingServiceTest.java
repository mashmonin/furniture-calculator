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
import com.example.furniturecalculator.domain.DoorConfiguration;
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
import com.example.furniturecalculator.repository.DoorConfigurationRepository;
import com.example.furniturecalculator.repository.LinerDimensionOptionRepository;
import com.example.furniturecalculator.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class DoorConfigurationPricingServiceTest {

    @Mock
    private DoorConfigurationRepository doorConfigurationRepository;
    @Mock
    private LinerDimensionOptionRepository linerDimensionOptionRepository;
    @Mock
    private ColourOptionRepository colourOptionRepository;
    @Mock
    private ConfigurationPriceRepository configurationPriceRepository;

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
                new PricingRequestDto(new ComponentSelectionDto(1000L, null, null, null), null, null, null, null);

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
                new PricingRequestDto(new ComponentSelectionDto(1000L, null, null, 2000L), null, null, null, null);

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
                new PricingRequestDto(new ComponentSelectionDto(1000L, null, null, 2000L), null, null, null, null);

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
        when(configurationPriceRepository.findByFrameTypeId(2L)).thenReturn(List.of());

        PricingRequestDto request = new PricingRequestDto(
                new ComponentSelectionDto(1000L, null, null, null), ComponentSelectionDto.EMPTY, null, null, null);

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
                new PricingRequestDto(new ComponentSelectionDto(1000L, null, null, null), null, null, null, null);

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
        ConfigurationPrice framePrice = TestEntities.configurationPrice(
                2L, BigDecimal.valueOf(500), BigDecimal.valueOf(400), frameType, null, null, null, null);

        when(doorConfigurationRepository.findById(10L)).thenReturn(Optional.of(configuration));
        when(configurationPriceRepository.findByLeafTypeId(1L)).thenReturn(List.of(leafPrice));
        when(configurationPriceRepository.findByFrameTypeId(2L)).thenReturn(List.of(framePrice));

        PricingRequestDto request =
                new PricingRequestDto(ComponentSelectionDto.EMPTY, ComponentSelectionDto.EMPTY, null, null, null);

        PricingResponseDto response = service.calculate(10L, request);

        assertThat(response.totalRetailPrice()).isEqualByComparingTo("1500");
        assertThat(response.totalDealerPrice()).isEqualByComparingTo("1300");
        assertThat(response.components()).hasSize(2);
        assertThat(response.components()).allMatch(ComponentPriceDto::priced);
    }
}
