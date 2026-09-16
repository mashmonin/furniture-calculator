package com.example.furniturecalculator.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.furniturecalculator.dto.FrameGroupPricingRequestDto;
import com.example.furniturecalculator.dto.FrameGroupPricingResponseDto;
import com.example.furniturecalculator.service.DoorConfigurationPricingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/frame-types")
@RequiredArgsConstructor
public class FrameGroupPricingController {

    private final DoorConfigurationPricingService pricingService;

    @PostMapping("/{id}/price")
    public FrameGroupPricingResponseDto calculatePrice(
            @PathVariable Long id, @RequestBody(required = false) FrameGroupPricingRequestDto request) {
        return pricingService.calculateForFrameGroup(id, request);
    }
}
