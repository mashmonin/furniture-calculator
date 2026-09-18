package com.example.furniturecalculator.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.PogonazhSurchargeRule;

public interface PogonazhSurchargeRuleRepository extends JpaRepository<PogonazhSurchargeRule, Long> {

    Optional<PogonazhSurchargeRule> findByFrameTypeIdAndValue(Long frameTypeId, BigDecimal value);

    List<PogonazhSurchargeRule> findByFrameTypeId(Long frameTypeId);

    Optional<PogonazhSurchargeRule> findByDoorCasingTypeIdAndValue(Long doorCasingTypeId, BigDecimal value);

    Optional<PogonazhSurchargeRule> findByFrameExtensionsTypeIdAndValue(Long frameExtensionsTypeId, BigDecimal value);
}
