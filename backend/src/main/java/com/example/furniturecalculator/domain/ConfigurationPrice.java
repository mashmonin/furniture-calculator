package com.example.furniturecalculator.domain;

import java.math.BigDecimal;

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
@Table(name = "configuration_price")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfigurationPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "retail_price", nullable = false)
    private BigDecimal retailPrice;

    @Column(name = "dealer_price", nullable = false)
    private BigDecimal dealerPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leaf_type_id")
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "length_option_id")
    private LinerDimensionOption lengthOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "height_option_id")
    private LinerDimensionOption heightOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thickness_option_id")
    private LinerDimensionOption thicknessOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "colour_option_id")
    private ColourOption colourOption;
}
