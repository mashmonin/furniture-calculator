package com.example.furniturecalculator.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.furniturecalculator.dto.HardwarePricingRequestDto;
import com.example.furniturecalculator.dto.HardwarePricingResponseDto;
import com.example.furniturecalculator.service.DoorConfigurationPricingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/hardware")
@RequiredArgsConstructor
public class HardwarePricingController {

    private final DoorConfigurationPricingService pricingService;

    @PostMapping("/price")
    public HardwarePricingResponseDto calculatePrice(@RequestBody(required = false) HardwarePricingRequestDto request) {
        return pricingService.calculateHardware(request);
    }
}
