package com.example.furniturecalculator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.DoorCasingType;

public interface DoorCasingTypeRepository extends JpaRepository<DoorCasingType, Long> {
}
