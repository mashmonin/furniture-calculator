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

import com.example.furniturecalculator.domain.DoorCasingType;
import com.example.furniturecalculator.domain.DoorConfiguration;
import com.example.furniturecalculator.domain.EdgeType;
import com.example.furniturecalculator.domain.FrameExtensionsType;
import com.example.furniturecalculator.domain.FramePost;
import com.example.furniturecalculator.domain.FrameType;
import com.example.furniturecalculator.domain.LeafPanelType;
import com.example.furniturecalculator.domain.LeafType;
import com.example.furniturecalculator.domain.LinerDimensionType;
import com.example.furniturecalculator.dto.DoorConfigurationDto;
import com.example.furniturecalculator.repository.ColourOptionRepository;
import com.example.furniturecalculator.repository.DoorConfigurationRepository;
import com.example.furniturecalculator.repository.FramePostRepository;
import com.example.furniturecalculator.repository.LinerDimensionOptionRepository;
import com.example.furniturecalculator.repository.MirrorFinishOptionRepository;
import com.example.furniturecalculator.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class DoorConfigurationCatalogServiceTest {

    @Mock
    private DoorConfigurationRepository doorConfigurationRepository;
    @Mock
    private LinerDimensionOptionRepository linerDimensionOptionRepository;
    @Mock
    private ColourOptionRepository colourOptionRepository;
    @Mock
    private FramePostRepository framePostRepository;
    @Mock
    private MirrorFinishOptionRepository mirrorFinishOptionRepository;

    @InjectMocks
    private DoorConfigurationCatalogService service;

    @Test
    void возвращает_пустой_список_если_конфигураций_нет() {
        when(doorConfigurationRepository.findAllWithTypes()).thenReturn(List.of());

        List<DoorConfigurationDto> result = service.getAllConfigurations();

        assertThat(result).isEmpty();
    }

    @Test
    void конфигурация_только_с_полотном_не_содержит_остальные_компоненты() {
        LeafType leafType = TestEntities.leafType(1L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, null, null, null);
        when(doorConfigurationRepository.findAllWithTypes()).thenReturn(List.of(configuration));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(colourOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(mirrorFinishOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());

        List<DoorConfigurationDto> result = service.getAllConfigurations();

        assertThat(result).hasSize(1);
        DoorConfigurationDto dto = result.get(0);
        assertThat(dto.leaf()).isNotNull();
        assertThat(dto.leaf().type().id()).isEqualTo(1L);
        assertThat(dto.leaf().collection()).isNotNull();
        assertThat(dto.leaf().collection().id()).isEqualTo(leafType.getCollection().getId());
        assertThat(dto.leaf().panelType()).isEqualTo(LeafPanelType.BLIND);
        assertThat(dto.frame()).isNull();
        assertThat(dto.edge()).isNull();
        assertThat(dto.doorCasing()).isNull();
        assertThat(dto.frameExtensions()).isNull();
    }

    @Test
    void конфигурация_со_всеми_компонентами_содержит_все_типы() {
        LeafType leafType = TestEntities.leafType(1L);
        FrameType frameType = TestEntities.frameType(2L);
        EdgeType edgeType = TestEntities.edgeType(3L);
        DoorCasingType doorCasingType = TestEntities.doorCasingType(4L);
        FrameExtensionsType frameExtensionsType = TestEntities.frameExtensionsType(5L);
        DoorConfiguration configuration =
                TestEntities.doorConfiguration(10L, leafType, frameType, edgeType, doorCasingType, frameExtensionsType);

        when(doorConfigurationRepository.findAllWithTypes()).thenReturn(List.of(configuration));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(colourOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(mirrorFinishOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(linerDimensionOptionRepository.findByFrameTypeId(2L)).thenReturn(List.of());
        when(colourOptionRepository.findByFrameTypeId(2L)).thenReturn(List.of());
        when(linerDimensionOptionRepository.findByEdgeTypeId(3L)).thenReturn(List.of());
        when(colourOptionRepository.findByEdgeTypeId(3L)).thenReturn(List.of());
        when(linerDimensionOptionRepository.findByDoorCasingTypeId(4L)).thenReturn(List.of());
        when(colourOptionRepository.findByDoorCasingTypeId(4L)).thenReturn(List.of());
        when(linerDimensionOptionRepository.findByFrameExtensionsTypeId(5L)).thenReturn(List.of());
        when(colourOptionRepository.findByFrameExtensionsTypeId(5L)).thenReturn(List.of());

        List<DoorConfigurationDto> result = service.getAllConfigurations();

        DoorConfigurationDto dto = result.get(0);
        assertThat(dto.leaf()).isNotNull();
        assertThat(dto.leaf().collection()).isNotNull();
        assertThat(dto.leaf().panelType()).isEqualTo(LeafPanelType.BLIND);
        assertThat(dto.frame()).isNotNull();
        assertThat(dto.frame().collection()).isNull();
        assertThat(dto.frame().panelType()).isNull();
        assertThat(dto.frame().posts()).isEmpty();
        assertThat(dto.edge()).isNotNull();
        assertThat(dto.doorCasing()).isNotNull();
        assertThat(dto.frameExtensions()).isNotNull();
    }

    @Test
    void короб_с_несколькими_frame_post_возвращает_обе_позиции_в_каталоге() {
        LeafType leafType = TestEntities.leafType(1L);
        FrameType frameType = TestEntities.frameType(2L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, frameType, null, null, null);
        FramePost topPost = TestEntities.framePost(100L, BigDecimal.valueOf(3549), BigDecimal.valueOf(2027), frameType);
        FramePost sidePostsKit = TestEntities.framePost(101L, BigDecimal.valueOf(7047), BigDecimal.valueOf(4027), frameType);

        when(doorConfigurationRepository.findAllWithTypes()).thenReturn(List.of(configuration));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(colourOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(mirrorFinishOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(linerDimensionOptionRepository.findByFrameTypeId(2L)).thenReturn(List.of());
        when(colourOptionRepository.findByFrameTypeId(2L)).thenReturn(List.of());
        when(framePostRepository.findByFrameTypeId(2L)).thenReturn(List.of(topPost, sidePostsKit));

        DoorConfigurationDto dto = service.getAllConfigurations().get(0);

        assertThat(dto.frame().posts()).hasSize(2);
        assertThat(dto.frame().posts()).extracting("id").containsExactlyInAnyOrder(100L, 101L);
    }

    @Test
    void опции_размеров_и_цвета_относятся_к_своему_компоненту() {
        LeafType leafType = TestEntities.leafType(1L);
        FrameType frameType = TestEntities.frameType(2L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, frameType, null, null, null);
        LinerDimensionType lengthType = TestEntities.linerDimensionType(100L);

        when(doorConfigurationRepository.findAllWithTypes()).thenReturn(List.of(configuration));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L))
                .thenReturn(List.of(TestEntities.linerDimensionOption(1000L, lengthType, BigDecimal.valueOf(600), true, leafType)));
        when(colourOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(mirrorFinishOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(linerDimensionOptionRepository.findByFrameTypeId(2L)).thenReturn(List.of());
        when(colourOptionRepository.findByFrameTypeId(2L))
                .thenReturn(List.of(TestEntities.colourOption(2000L, TestEntities.colourType(200L), frameType)));

        List<DoorConfigurationDto> result = service.getAllConfigurations();

        DoorConfigurationDto dto = result.get(0);
        assertThat(dto.leaf().dimensionOptions()).hasSize(1);
        assertThat(dto.leaf().colourOptions()).isEmpty();
        assertThat(dto.frame().dimensionOptions()).isEmpty();
        assertThat(dto.frame().colourOptions()).hasSize(1);
    }

    @Test
    void компонент_без_опций_возвращает_пустые_списки() {
        LeafType leafType = TestEntities.leafType(1L);
        EdgeType edgeType = TestEntities.edgeType(3L);
        DoorConfiguration configuration = TestEntities.doorConfiguration(10L, leafType, null, edgeType, null, null);

        when(doorConfigurationRepository.findAllWithTypes()).thenReturn(List.of(configuration));
        when(linerDimensionOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(colourOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(mirrorFinishOptionRepository.findByLeafTypeId(1L)).thenReturn(List.of());
        when(linerDimensionOptionRepository.findByEdgeTypeId(3L)).thenReturn(List.of());
        when(colourOptionRepository.findByEdgeTypeId(3L)).thenReturn(List.of());

        DoorConfigurationDto dto = service.getAllConfigurations().get(0);

        assertThat(dto.edge().dimensionOptions()).isEmpty();
        assertThat(dto.edge().colourOptions()).isEmpty();
    }
}
