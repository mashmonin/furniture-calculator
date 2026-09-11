package com.example.furniturecalculator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.LeafType;

public interface LeafTypeRepository extends JpaRepository<LeafType, Long> {
}
