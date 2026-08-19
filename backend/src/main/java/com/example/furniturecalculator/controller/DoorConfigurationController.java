package com.example.furniturecalculator.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.furniturecalculator.dto.DoorConfigurationDto;
import com.example.furniturecalculator.dto.PricingRequestDto;
import com.example.furniturecalculator.dto.PricingResponseDto;
import com.example.furniturecalculator.service.DoorConfigurationCatalogService;
import com.example.furniturecalculator.service.DoorConfigurationPricingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/door-configurations")
@RequiredArgsConstructor
public class DoorConfigurationController {

    private final DoorConfigurationCatalogService catalogService;
    private final DoorConfigurationPricingService pricingService;

    @GetMapping
    public List<DoorConfigurationDto> getAllConfigurations() {
        return catalogService.getAllConfigurations();
    }

    @PostMapping("/{id}/price")
    public PricingResponseDto calculatePrice(@PathVariable Long id, @RequestBody(required = false) PricingRequestDto request) {
        return pricingService.calculate(id, request);
    }
}
