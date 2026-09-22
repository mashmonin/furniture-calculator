package com.example.furniturecalculator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "door_configuration")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DoorConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leaf_type_id", nullable = false)
    private LeafType leafType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "frame_type_id")
    private FrameType frameType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edge_type_id")
    private EdgeType edgeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "door_casing_type_id")
    private DoorCasingType doorCasingType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "frame_extensions_type_id")
    private FrameExtensionsType frameExtensionsType;

    @Column(name = "is_reverse", nullable = false)
    private boolean reverse;

    // Четверть по периметру полотна — самостоятельная конструктивная опция, независимая от is_reverse
    // (см. change add-leaf-quarter-attribute). До этого change вид кромки жёстко соответствовал is_reverse;
    // теперь он соответствует этому атрибуту (см. DoorConfigurationCatalogService).
    @Column(name = "has_quarter", nullable = false)
    private boolean hasQuarter;
}
