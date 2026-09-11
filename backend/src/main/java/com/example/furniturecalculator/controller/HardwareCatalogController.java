package com.example.furniturecalculator.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.furniturecalculator.dto.HardwareCategoryDto;
import com.example.furniturecalculator.service.HardwareCatalogService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/hardware-catalog")
@RequiredArgsConstructor
public class HardwareCatalogController {

    private final HardwareCatalogService hardwareCatalogService;

    @GetMapping
    public List<HardwareCategoryDto> getCatalog() {
        return hardwareCatalogService.getCatalog();
    }
}
