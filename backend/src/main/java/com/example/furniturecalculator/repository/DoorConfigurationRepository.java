package com.example.furniturecalculator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.furniturecalculator.domain.DoorConfiguration;

public interface DoorConfigurationRepository extends JpaRepository<DoorConfiguration, Long> {

    @Query("""
            SELECT dc FROM DoorConfiguration dc
            JOIN FETCH dc.leafType lt
            JOIN FETCH lt.collection
            LEFT JOIN FETCH dc.frameType
            LEFT JOIN FETCH dc.edgeType
            LEFT JOIN FETCH dc.doorCasingType
            LEFT JOIN FETCH dc.frameExtensionsType
            """)
    List<DoorConfiguration> findAllWithTypes();
}
