package com.example.furniturecalculator.dto;

import com.example.furniturecalculator.domain.PriceList;

public record PriceListDto(Long id, String code, String name) {

    public static PriceListDto from(PriceList priceList) {
        return new PriceListDto(priceList.getId(), priceList.getCode(), priceList.getName());
    }
}
