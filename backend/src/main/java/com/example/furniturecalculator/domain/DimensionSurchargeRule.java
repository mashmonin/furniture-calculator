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
@Table(name = "dimension_surcharge_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DimensionSurchargeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "liner_dimension_type_id", nullable = false)
    private LinerDimensionType linerDimensionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leaf_type_id")
    private LeafType leafType;

    @Column(nullable = false)
    private BigDecimal value;

    @Column(name = "surcharge_percent", nullable = false)
    private BigDecimal surchargePercent;
}
