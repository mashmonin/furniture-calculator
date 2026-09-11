package com.example.furniturecalculator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.HardwareCategory;

public interface HardwareCategoryRepository extends JpaRepository<HardwareCategory, Long> {
}
