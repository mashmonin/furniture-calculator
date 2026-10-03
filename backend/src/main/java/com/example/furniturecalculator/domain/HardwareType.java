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
@Table(name = "hardware_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HardwareType implements CatalogType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String unit;

    @Column(name = "short_name")
    private String shortName;

    // Бренд позиции (AGB, ARMADILLO, PUNTO, FUARO) — необязательный: у фурнитуры прайс-листа «Эмаль и шпон»
    // не задан (см. change add-emal-layt-hardware).
    private String brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hardware_category_id", nullable = false)
    private HardwareCategory hardwareCategory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_list_id", nullable = false)
    private PriceList priceList;
}
