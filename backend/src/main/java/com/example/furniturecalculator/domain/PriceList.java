package com.example.furniturecalculator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "price_list")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    // Название файла прайс-листа (без префикса «Прайс-лист:» — подпись добавляется в интерфейсе).
    @Column(nullable = false)
    private String name;
}
