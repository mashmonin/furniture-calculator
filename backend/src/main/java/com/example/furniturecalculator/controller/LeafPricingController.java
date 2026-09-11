package com.example.furniturecalculator.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.furniturecalculator.dto.PricingRequestDto;
import com.example.furniturecalculator.dto.PricingResponseDto;
import com.example.furniturecalculator.service.DoorConfigurationPricingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/leaf-types")
@RequiredArgsConstructor
public class LeafPricingController {

    private final DoorConfigurationPricingService pricingService;

    @PostMapping("/{id}/price")
    public PricingResponseDto calculatePrice(@PathVariable Long id, @RequestBody(required = false) PricingRequestDto request) {
        return pricingService.calculateForLeaf(id, request);
    }
}
