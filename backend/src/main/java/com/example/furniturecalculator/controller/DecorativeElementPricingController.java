package com.example.furniturecalculator.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.furniturecalculator.dto.DecorativeElementPricingRequestDto;
import com.example.furniturecalculator.dto.DecorativeElementPricingResponseDto;
import com.example.furniturecalculator.service.DoorConfigurationPricingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/decorative-elements")
@RequiredArgsConstructor
public class DecorativeElementPricingController {

    private final DoorConfigurationPricingService pricingService;

    @PostMapping("/price")
    public DecorativeElementPricingResponseDto calculatePrice(
            @RequestBody(required = false) DecorativeElementPricingRequestDto request) {
        return pricingService.calculateDecorativeElements(request);
    }
}
