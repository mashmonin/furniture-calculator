package com.example.furniturecalculator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.DecorativeElementOption;

public interface DecorativeElementOptionRepository extends JpaRepository<DecorativeElementOption, Long> {

    List<DecorativeElementOption> findByLeafTypeId(Long leafTypeId);
}
