package com.example.furniturecalculator.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.furniturecalculator.dto.DecorativeElementCategoryDto;
import com.example.furniturecalculator.service.DecorativeElementCatalogService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/decorative-elements-catalog")
@RequiredArgsConstructor
public class DecorativeElementCatalogController {

    private final DecorativeElementCatalogService decorativeElementCatalogService;

    @GetMapping
    public List<DecorativeElementCategoryDto> getCatalog() {
        return decorativeElementCatalogService.getCatalog();
    }
}
