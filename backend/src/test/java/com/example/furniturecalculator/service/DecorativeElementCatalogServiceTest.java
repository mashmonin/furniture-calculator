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

import com.example.furniturecalculator.domain.DecorativeElementCategory;
import com.example.furniturecalculator.domain.DecorativeElementType;
import com.example.furniturecalculator.dto.DecorativeElementCategoryDto;
import com.example.furniturecalculator.repository.DecorativeElementCategoryRepository;
import com.example.furniturecalculator.repository.DecorativeElementTypeRepository;
import com.example.furniturecalculator.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class DecorativeElementCatalogServiceTest {

    @Mock
    private DecorativeElementCategoryRepository decorativeElementCategoryRepository;
    @Mock
    private DecorativeElementTypeRepository decorativeElementTypeRepository;

    @InjectMocks
    private DecorativeElementCatalogService service;

    @Test
    void пустой_каталог_возвращает_пустой_список() {
        when(decorativeElementCategoryRepository.findAll()).thenReturn(List.of());
        when(decorativeElementTypeRepository.findAll()).thenReturn(List.of());

        List<DecorativeElementCategoryDto> result = service.getCatalog();

        assertThat(result).isEmpty();
    }

    @Test
    void каталог_возвращает_полную_иерархию_с_длиной_и_ценами() {
        DecorativeElementCategory category = TestEntities.decorativeElementCategory(1L);
        DecorativeElementType typeA = TestEntities.decorativeElementType(
                2L, BigDecimal.valueOf(2400), BigDecimal.valueOf(2611), BigDecimal.valueOf(1492), category);
        DecorativeElementType typeB = TestEntities.decorativeElementType(
                3L, BigDecimal.valueOf(2400), BigDecimal.valueOf(2611), BigDecimal.valueOf(1492), category);

        when(decorativeElementCategoryRepository.findAll()).thenReturn(List.of(category));
        when(decorativeElementTypeRepository.findAll()).thenReturn(List.of(typeA, typeB));

        List<DecorativeElementCategoryDto> result = service.getCatalog();

        assertThat(result).hasSize(1);
        DecorativeElementCategoryDto categoryDto = result.get(0);
        assertThat(categoryDto.category().id()).isEqualTo(1L);
        assertThat(categoryDto.types()).hasSize(2);
        assertThat(categoryDto.types().get(0).lengthMm()).isEqualByComparingTo("2400");
        assertThat(categoryDto.types().get(0).retailPrice()).isEqualByComparingTo("2611");
        assertThat(categoryDto.types().get(0).dealerPrice()).isEqualByComparingTo("1492");
    }

    @Test
    void категория_без_типов_возвращает_пустой_список_типов() {
        DecorativeElementCategory category = TestEntities.decorativeElementCategory(1L);

        when(decorativeElementCategoryRepository.findAll()).thenReturn(List.of(category));
        when(decorativeElementTypeRepository.findAll()).thenReturn(List.of());

        List<DecorativeElementCategoryDto> result = service.getCatalog();

        assertThat(result.get(0).types()).isEmpty();
    }
}
