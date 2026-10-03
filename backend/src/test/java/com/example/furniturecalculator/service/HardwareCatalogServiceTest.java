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

import com.example.furniturecalculator.domain.HardwareCategory;
import com.example.furniturecalculator.domain.HardwareOption;
import com.example.furniturecalculator.domain.HardwareType;
import com.example.furniturecalculator.dto.HardwareCategoryDto;
import com.example.furniturecalculator.repository.HardwareCategoryRepository;
import com.example.furniturecalculator.repository.HardwareOptionRepository;
import com.example.furniturecalculator.repository.HardwareTypeRepository;
import com.example.furniturecalculator.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class HardwareCatalogServiceTest {

    @Mock
    private HardwareCategoryRepository hardwareCategoryRepository;
    @Mock
    private HardwareTypeRepository hardwareTypeRepository;
    @Mock
    private HardwareOptionRepository hardwareOptionRepository;

    @InjectMocks
    private HardwareCatalogService service;

    @Test
    void пустой_каталог_возвращает_пустой_список() {
        when(hardwareCategoryRepository.findAll()).thenReturn(List.of());
        when(hardwareTypeRepository.findAll()).thenReturn(List.of());
        when(hardwareOptionRepository.findAll()).thenReturn(List.of());

        List<HardwareCategoryDto> result = service.getCatalog();

        assertThat(result).isEmpty();
    }

    @Test
    void каталог_возвращает_полную_иерархию() {
        HardwareCategory category = TestEntities.hardwareCategory(1L);
        HardwareType type = TestEntities.hardwareType(2L, category);
        HardwareOption optionA =
                TestEntities.hardwareOption(3L, "хром", BigDecimal.valueOf(100), BigDecimal.valueOf(80), type);
        HardwareOption optionB =
                TestEntities.hardwareOption(4L, "черный", BigDecimal.valueOf(110), BigDecimal.valueOf(90), type);

        when(hardwareCategoryRepository.findAll()).thenReturn(List.of(category));
        when(hardwareTypeRepository.findAll()).thenReturn(List.of(type));
        when(hardwareOptionRepository.findAll()).thenReturn(List.of(optionA, optionB));

        List<HardwareCategoryDto> result = service.getCatalog();

        assertThat(result).hasSize(1);
        HardwareCategoryDto categoryDto = result.get(0);
        assertThat(categoryDto.category().id()).isEqualTo(1L);
        assertThat(categoryDto.types()).hasSize(1);
        assertThat(categoryDto.types().get(0).type().id()).isEqualTo(2L);
        assertThat(categoryDto.types().get(0).unit()).isEqualTo("шт");
        assertThat(categoryDto.types().get(0).priceList().code()).isEqualTo("PL-001");
        assertThat(categoryDto.types().get(0).priceList().name()).isEqualTo("hausdoors_emal_i_shpon_rf_07_09_2026");
        assertThat(categoryDto.types().get(0).options()).hasSize(2);
        assertThat(categoryDto.types().get(0).options())
                .extracting("colourName")
                .containsExactlyInAnyOrder("хром", "черный");
    }

    @Test
    void тип_с_единственным_цветовым_вариантом_не_завершается_ошибкой() {
        HardwareCategory category = TestEntities.hardwareCategory(1L);
        HardwareType type = TestEntities.hardwareType(2L, category);
        HardwareOption option =
                TestEntities.hardwareOption(3L, "хром", BigDecimal.valueOf(100), BigDecimal.valueOf(80), type);

        when(hardwareCategoryRepository.findAll()).thenReturn(List.of(category));
        when(hardwareTypeRepository.findAll()).thenReturn(List.of(type));
        when(hardwareOptionRepository.findAll()).thenReturn(List.of(option));

        List<HardwareCategoryDto> result = service.getCatalog();

        assertThat(result.get(0).types().get(0).options()).hasSize(1);
    }
}
