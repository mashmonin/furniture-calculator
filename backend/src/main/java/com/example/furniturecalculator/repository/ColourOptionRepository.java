package com.example.furniturecalculator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.ColourOption;

public interface ColourOptionRepository extends JpaRepository<ColourOption, Long> {

    List<ColourOption> findByLeafTypeId(Long leafTypeId);

    List<ColourOption> findByFrameTypeId(Long frameTypeId);

    List<ColourOption> findByEdgeTypeId(Long edgeTypeId);

    List<ColourOption> findByDoorCasingTypeId(Long doorCasingTypeId);

    List<ColourOption> findByFrameExtensionsTypeId(Long frameExtensionsTypeId);
}
