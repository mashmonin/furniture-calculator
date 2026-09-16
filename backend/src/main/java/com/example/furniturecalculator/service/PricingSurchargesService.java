package com.example.furniturecalculator.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.furniturecalculator.dto.DimensionSurchargeRuleDto;
import com.example.furniturecalculator.dto.GlazingSurchargeDto;
import com.example.furniturecalculator.dto.MirrorFinishSurchargeDto;
import com.example.furniturecalculator.dto.PricingSurchargesDto;
import com.example.furniturecalculator.dto.ReferenceDto;
import com.example.furniturecalculator.repository.DimensionSurchargeRuleRepository;
import com.example.furniturecalculator.repository.GlazingTypeRepository;
import com.example.furniturecalculator.repository.MirrorFinishTypeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PricingSurchargesService {

    private final DimensionSurchargeRuleRepository dimensionSurchargeRuleRepository;
    private final MirrorFinishTypeRepository mirrorFinishTypeRepository;
    private final GlazingTypeRepository glazingTypeRepository;
    private final DoorConfigurationPricingService pricingService;

    @Transactional(readOnly = true)
    public PricingSurchargesDto getPricingSurcharges() {
        return new PricingSurchargesDto(
                pricingService.reverseSurchargePercent(),
                dimensionSurchargeRuleRepository.findAll().stream()
                        .map(rule -> new DimensionSurchargeRuleDto(
                                ReferenceDto.from(rule.getLinerDimensionType()), rule.getValue(), rule.getSurchargePercent()))
                        .toList(),
                mirrorFinishTypeRepository.findAll().stream()
                        .map(type -> new MirrorFinishSurchargeDto(
                                type.getId(), type.getName(), type.getShortName(), type.getSurchargePercent()))
                        .toList(),
                glazingTypeRepository.findAll().stream()
                        .map(type -> new GlazingSurchargeDto(type.getId(), type.getName(), type.getSurchargePercent()))
                        .toList());
    }
}
