package com.example.furniturecalculator.repository;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.DimensionSurchargeRule;

public interface DimensionSurchargeRuleRepository extends JpaRepository<DimensionSurchargeRule, Long> {

    Optional<DimensionSurchargeRule> findByLinerDimensionTypeIdAndValueAndLeafTypeId(
            Long linerDimensionTypeId, BigDecimal value, Long leafTypeId);

    Optional<DimensionSurchargeRule> findByLinerDimensionTypeIdAndValueAndLeafTypeIsNull(
            Long linerDimensionTypeId, BigDecimal value);
}
