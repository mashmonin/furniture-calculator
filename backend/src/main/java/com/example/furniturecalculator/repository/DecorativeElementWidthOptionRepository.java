package com.example.furniturecalculator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.DecorativeElementWidthOption;

public interface DecorativeElementWidthOptionRepository extends JpaRepository<DecorativeElementWidthOption, Long> {

    List<DecorativeElementWidthOption> findByDecorativeElementTypeId(Long decorativeElementTypeId);
}
