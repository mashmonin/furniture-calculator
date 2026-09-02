package com.example.furniturecalculator.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.furniturecalculator.dto.PricingSurchargesDto;
import com.example.furniturecalculator.service.PricingSurchargesService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pricing-surcharges")
@RequiredArgsConstructor
public class PricingSurchargesController {

    private final PricingSurchargesService pricingSurchargesService;

    @GetMapping
    public PricingSurchargesDto getPricingSurcharges() {
        return pricingSurchargesService.getPricingSurcharges();
    }
}
