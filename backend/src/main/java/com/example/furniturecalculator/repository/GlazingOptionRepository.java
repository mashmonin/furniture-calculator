package com.example.furniturecalculator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.GlazingOption;

public interface GlazingOptionRepository extends JpaRepository<GlazingOption, Long> {

    List<GlazingOption> findByLeafTypeId(Long leafTypeId);
}
