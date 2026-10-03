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
@Table(name = "collection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeafCollection implements CatalogType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    @Column(name = "short_name")
    private String shortName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_list_id", nullable = false)
    private PriceList priceList;

    // Допускает ли коллекция произвольные (нестандартные) значения длины/высоты полотна помимо стандартных
    // каталожных (см. change add-emal-layt-service); false — только стандартные размеры.
    @Column(name = "custom_dimensions_allowed", nullable = false)
    private boolean customDimensionsAllowed;
}
