package com.example.furniturecalculator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.furniturecalculator.domain.ColourType;
import com.example.furniturecalculator.domain.DimensionSurchargeRule;
import com.example.furniturecalculator.domain.FrameType;
import com.example.furniturecalculator.domain.LinerDimensionType;
import com.example.furniturecalculator.domain.MirrorFinishType;
import com.example.furniturecalculator.domain.PogonazhSurchargeRule;
import com.example.furniturecalculator.dto.PricingSurchargesDto;
import com.example.furniturecalculator.repository.ColourTypeRepository;
import com.example.furniturecalculator.repository.DimensionSurchargeRuleRepository;
import com.example.furniturecalculator.repository.GlazingTypeRepository;
import com.example.furniturecalculator.repository.MirrorFinishTypeRepository;
import com.example.furniturecalculator.repository.PogonazhSurchargeRuleRepository;
import com.example.furniturecalculator.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class PricingSurchargesServiceTest {

    @Mock
    private DimensionSurchargeRuleRepository dimensionSurchargeRuleRepository;
    @Mock
    private MirrorFinishTypeRepository mirrorFinishTypeRepository;
    @Mock
    private GlazingTypeRepository glazingTypeRepository;
    @Mock
    private ColourTypeRepository colourTypeRepository;
    @Mock
    private PogonazhSurchargeRuleRepository pogonazhSurchargeRuleRepository;
    @Mock
    private DoorConfigurationPricingService pricingService;

    @InjectMocks
    private PricingSurchargesService service;

    @Test
    void пустые_справочники_дают_пустые_списки_и_процент_реверса() {
        when(dimensionSurchargeRuleRepository.findAll()).thenReturn(List.of());
        when(mirrorFinishTypeRepository.findAll()).thenReturn(List.of());
        when(pricingService.reverseSurchargePercent()).thenReturn(BigDecimal.TEN);

        PricingSurchargesDto result = service.getPricingSurcharges();

        assertThat(result.dimensionSurchargeRules()).isEmpty();
        assertThat(result.mirrorFinishSurcharges()).isEmpty();
        assertThat(result.reverseSurchargePercent()).isEqualByComparingTo("10");
    }

    @Test
    void непустой_справочник_наценок_возвращает_все_строки_с_правильными_полями() {
        LinerDimensionType lengthType = TestEntities.linerDimensionType(101L, "DT-001");
        DimensionSurchargeRule rule = TestEntities.dimensionSurchargeRule(1L, lengthType, BigDecimal.valueOf(950), BigDecimal.valueOf(20));
        when(dimensionSurchargeRuleRepository.findAll()).thenReturn(List.of(rule));
        when(mirrorFinishTypeRepository.findAll()).thenReturn(List.of());
        when(pricingService.reverseSurchargePercent()).thenReturn(BigDecimal.TEN);

        PricingSurchargesDto result = service.getPricingSurcharges();

        assertThat(result.dimensionSurchargeRules()).hasSize(1);
        assertThat(result.dimensionSurchargeRules().get(0).dimensionType().code()).isEqualTo("DT-001");
        assertThat(result.dimensionSurchargeRules().get(0).value()).isEqualByComparingTo("950");
        assertThat(result.dimensionSurchargeRules().get(0).surchargePercent()).isEqualByComparingTo("20");
        assertThat(result.reverseSurchargePercent()).isEqualByComparingTo("10");
    }

    @Test
    void непустой_справочник_исполнений_зеркала_возвращает_все_строки_с_правильными_полями() {
        MirrorFinishType mirrorFinishType = TestEntities.mirrorFinishType(1L, BigDecimal.valueOf(30));
        when(dimensionSurchargeRuleRepository.findAll()).thenReturn(List.of());
        when(mirrorFinishTypeRepository.findAll()).thenReturn(List.of(mirrorFinishType));
        when(pricingService.reverseSurchargePercent()).thenReturn(BigDecimal.TEN);

        PricingSurchargesDto result = service.getPricingSurcharges();

        assertThat(result.mirrorFinishSurcharges()).hasSize(1);
        assertThat(result.mirrorFinishSurcharges().get(0).id()).isEqualTo(1L);
        assertThat(result.mirrorFinishSurcharges().get(0).surchargePercent()).isEqualByComparingTo("30");
    }

    @Test
    void непустой_справочник_цветов_возвращает_все_строки_с_правильными_полями() {
        ColourType colourType = TestEntities.colourType(1L, BigDecimal.valueOf(20));
        when(dimensionSurchargeRuleRepository.findAll()).thenReturn(List.of());
        when(mirrorFinishTypeRepository.findAll()).thenReturn(List.of());
        when(colourTypeRepository.findAll()).thenReturn(List.of(colourType));
        when(pricingService.reverseSurchargePercent()).thenReturn(BigDecimal.TEN);

        PricingSurchargesDto result = service.getPricingSurcharges();

        assertThat(result.colourSurcharges()).hasSize(1);
        assertThat(result.colourSurcharges().get(0).id()).isEqualTo(1L);
        assertThat(result.colourSurcharges().get(0).surchargePercent()).isEqualByComparingTo("20");
    }

    @Test
    void процент_надбавки_за_двустороннюю_покраску_совпадает_со_значением_из_pricing_service() {
        when(dimensionSurchargeRuleRepository.findAll()).thenReturn(List.of());
        when(mirrorFinishTypeRepository.findAll()).thenReturn(List.of());
        when(pricingService.reverseSurchargePercent()).thenReturn(BigDecimal.TEN);
        when(pricingService.doubleSidedPaintingSurchargePercent()).thenReturn(BigDecimal.valueOf(50));

        PricingSurchargesDto result = service.getPricingSurcharges();

        assertThat(result.doubleSidedPaintingSurchargePercent()).isEqualByComparingTo("50");
    }

    @Test
    void непустой_справочник_наценок_за_погонаж_возвращает_все_строки_с_правильными_полями() {
        FrameType frameType = TestEntities.frameType(3L, "FT-003");
        PogonazhSurchargeRule rule =
                TestEntities.pogonazhSurchargeRule(1L, frameType, null, null, BigDecimal.valueOf(2400), BigDecimal.valueOf(30));
        when(dimensionSurchargeRuleRepository.findAll()).thenReturn(List.of());
        when(mirrorFinishTypeRepository.findAll()).thenReturn(List.of());
        when(pogonazhSurchargeRuleRepository.findAll()).thenReturn(List.of(rule));
        when(pricingService.reverseSurchargePercent()).thenReturn(BigDecimal.TEN);

        PricingSurchargesDto result = service.getPricingSurcharges();

        assertThat(result.pogonazhSurchargeRules()).hasSize(1);
        assertThat(result.pogonazhSurchargeRules().get(0).ownerType()).isEqualTo("frame");
        assertThat(result.pogonazhSurchargeRules().get(0).ownerId()).isEqualTo(3L);
        assertThat(result.pogonazhSurchargeRules().get(0).value()).isEqualByComparingTo("2400");
        assertThat(result.pogonazhSurchargeRules().get(0).surchargePercent()).isEqualByComparingTo("30");
    }

    @Test
    void пустой_справочник_наценок_за_погонаж_не_влияет_на_остальные_поля() {
        when(dimensionSurchargeRuleRepository.findAll()).thenReturn(List.of());
        when(mirrorFinishTypeRepository.findAll()).thenReturn(List.of());
        when(pogonazhSurchargeRuleRepository.findAll()).thenReturn(List.of());
        when(pricingService.reverseSurchargePercent()).thenReturn(BigDecimal.TEN);

        PricingSurchargesDto result = service.getPricingSurcharges();

        assertThat(result.pogonazhSurchargeRules()).isEmpty();
        assertThat(result.reverseSurchargePercent()).isEqualByComparingTo("10");
    }
}
