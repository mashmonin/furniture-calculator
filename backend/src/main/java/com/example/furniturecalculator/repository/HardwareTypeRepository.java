package com.example.furniturecalculator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.HardwareType;

public interface HardwareTypeRepository extends JpaRepository<HardwareType, Long> {

    List<HardwareType> findByHardwareCategoryId(Long hardwareCategoryId);
}
