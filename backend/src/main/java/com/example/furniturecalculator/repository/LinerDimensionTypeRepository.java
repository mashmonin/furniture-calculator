package com.example.furniturecalculator.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.LinerDimensionType;

public interface LinerDimensionTypeRepository extends JpaRepository<LinerDimensionType, Long> {

    Optional<LinerDimensionType> findByCode(String code);
}
