package com.example.furniturecalculator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.DecorativeElementType;

public interface DecorativeElementTypeRepository extends JpaRepository<DecorativeElementType, Long> {

    List<DecorativeElementType> findByDecorativeElementCategoryId(Long decorativeElementCategoryId);
}
