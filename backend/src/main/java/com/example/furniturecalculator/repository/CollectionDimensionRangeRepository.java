package com.example.furniturecalculator.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.CollectionDimensionRange;

public interface CollectionDimensionRangeRepository extends JpaRepository<CollectionDimensionRange, Long> {

    Optional<CollectionDimensionRange> findByCollectionIdAndLinerDimensionTypeId(Long collectionId, Long linerDimensionTypeId);

    List<CollectionDimensionRange> findByCollectionId(Long collectionId);
}
