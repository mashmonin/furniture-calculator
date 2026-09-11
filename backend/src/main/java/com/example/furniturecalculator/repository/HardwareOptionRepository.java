package com.example.furniturecalculator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.HardwareOption;

public interface HardwareOptionRepository extends JpaRepository<HardwareOption, Long> {

    List<HardwareOption> findByHardwareTypeId(Long hardwareTypeId);
}
