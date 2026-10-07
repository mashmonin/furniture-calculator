package com.example.furniturecalculator.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import com.example.furniturecalculator.domain.HardwareCategory;
import com.example.furniturecalculator.domain.HardwareOption;
import com.example.furniturecalculator.domain.HardwareType;
import com.example.furniturecalculator.dto.HardwareCategoryDto;
import com.example.furniturecalculator.dto.HardwareOptionDto;
import com.example.furniturecalculator.dto.HardwareTypeDto;
import com.example.furniturecalculator.dto.PriceListDto;
import com.example.furniturecalculator.dto.ReferenceDto;
import com.example.furniturecalculator.repository.HardwareCategoryRepository;
import com.example.furniturecalculator.repository.HardwareOptionRepository;
import com.example.furniturecalculator.repository.HardwareTypeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HardwareCatalogService {

    private final HardwareCategoryRepository hardwareCategoryRepository;
    private final HardwareTypeRepository hardwareTypeRepository;
    private final HardwareOptionRepository hardwareOptionRepository;
    private final HardwareImageCatalog hardwareImageCatalog;

    @Transactional(readOnly = true)
    public List<HardwareCategoryDto> getCatalog() {
        Map<Long, List<HardwareType>> typesByCategoryId = hardwareTypeRepository.findAll().stream()
                .collect(Collectors.groupingBy(type -> type.getHardwareCategory().getId()));
        Map<Long, List<HardwareOption>> optionsByTypeId = hardwareOptionRepository.findAll().stream()
                .collect(Collectors.groupingBy(option -> option.getHardwareType().getId()));

        return hardwareCategoryRepository.findAll().stream()
                .map(category -> toDto(category, typesByCategoryId.getOrDefault(category.getId(), List.of()), optionsByTypeId))
                .toList();
    }

    private HardwareCategoryDto toDto(
            HardwareCategory category, List<HardwareType> types, Map<Long, List<HardwareOption>> optionsByTypeId) {
        List<HardwareTypeDto> typeDtos = types.stream()
                .map(type -> toDto(type, optionsByTypeId.getOrDefault(type.getId(), List.of())))
                .toList();
        return new HardwareCategoryDto(ReferenceDto.from(category), typeDtos);
    }

    private HardwareTypeDto toDto(HardwareType type, List<HardwareOption> options) {
        List<HardwareOptionDto> optionDtos = options.stream().map(this::toDto).toList();
        return new HardwareTypeDto(
                ReferenceDto.from(type), type.getUnit(), type.getBrand(), PriceListDto.from(type.getPriceList()), optionDtos);
    }

    private HardwareOptionDto toDto(HardwareOption option) {
        String imageUrl = hardwareImageCatalog.findFileName(option.getArticle())
                .map(fileName -> "/api/hardware-images/" + UriUtils.encodePathSegment(fileName, StandardCharsets.UTF_8))
                .orElse(null);
        return new HardwareOptionDto(
                option.getId(), option.getColourName(), option.getRetailPrice(), option.getDealerPrice(), option.getArticle(), imageUrl);
    }
}
