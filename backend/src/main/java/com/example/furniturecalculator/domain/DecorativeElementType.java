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
@Table(name = "decorative_element_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DecorativeElementType implements CatalogType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "length_mm", nullable = false)
    private BigDecimal lengthMm;

    @Column(name = "width_mm")
    private BigDecimal widthMm;

    @Column(name = "thickness_mm")
    private BigDecimal thicknessMm;

    @Column(name = "retail_price", nullable = false)
    private BigDecimal retailPrice;

    @Column(name = "dealer_price", nullable = false)
    private BigDecimal dealerPrice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decorative_element_category_id", nullable = false)
    private DecorativeElementCategory decorativeElementCategory;
}
