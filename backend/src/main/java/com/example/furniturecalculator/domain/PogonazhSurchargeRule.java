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
@Table(name = "pogonazh_surcharge_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PogonazhSurchargeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "frame_type_id")
    private FrameType frameType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "door_casing_type_id")
    private DoorCasingType doorCasingType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "frame_extensions_type_id")
    private FrameExtensionsType frameExtensionsType;

    @Column(nullable = false)
    private BigDecimal value;

    @Column(name = "surcharge_percent", nullable = false)
    private BigDecimal surchargePercent;
}
