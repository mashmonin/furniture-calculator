package com.example.furniturecalculator.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.FrameType;

public interface FrameTypeRepository extends JpaRepository<FrameType, Long> {
}
