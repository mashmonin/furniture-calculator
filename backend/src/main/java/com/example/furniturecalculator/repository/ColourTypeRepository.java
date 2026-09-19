package com.example.furniturecalculator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.ColourType;

public interface ColourTypeRepository extends JpaRepository<ColourType, Long> {
}
