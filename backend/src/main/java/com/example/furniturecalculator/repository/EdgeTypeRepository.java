package com.example.furniturecalculator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.EdgeType;

public interface EdgeTypeRepository extends JpaRepository<EdgeType, Long> {
}
