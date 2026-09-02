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

import com.example.furniturecalculator.domain.DimensionSurchargeRule;
import com.example.furniturecalculator.domain.LinerDimensionType;
import com.example.furniturecalculator.domain.MirrorFinishType;
import com.example.furniturecalculator.dto.PricingSurchargesDto;
import com.example.furniturecalculator.repository.DimensionSurchargeRuleRepository;
import com.example.furniturecalculator.repository.MirrorFinishTypeRepository;
import com.example.furniturecalculator.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class PricingSurchargesServiceTest {

    @Mock
    private DimensionSurchargeRuleRepository dimensionSurchargeRuleRepository;
    @Mock
    private MirrorFinishTypeRepository mirrorFinishTypeRepository;
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
}
