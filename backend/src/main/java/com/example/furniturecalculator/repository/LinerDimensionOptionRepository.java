package com.example.furniturecalculator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.LinerDimensionOption;

public interface LinerDimensionOptionRepository extends JpaRepository<LinerDimensionOption, Long> {

    List<LinerDimensionOption> findByLeafTypeId(Long leafTypeId);

    List<LinerDimensionOption> findByFrameTypeId(Long frameTypeId);

    List<LinerDimensionOption> findByEdgeTypeId(Long edgeTypeId);

    List<LinerDimensionOption> findByDoorCasingTypeId(Long doorCasingTypeId);

    List<LinerDimensionOption> findByFrameExtensionsTypeId(Long frameExtensionsTypeId);
}
