package com.example.furniturecalculator.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.furniturecalculator.domain.ConfigurationPrice;

public interface ConfigurationPriceRepository extends JpaRepository<ConfigurationPrice, Long> {

    List<ConfigurationPrice> findByLeafTypeId(Long leafTypeId);

    List<ConfigurationPrice> findByFrameTypeId(Long frameTypeId);

    List<ConfigurationPrice> findByEdgeTypeId(Long edgeTypeId);

    List<ConfigurationPrice> findByDoorCasingTypeId(Long doorCasingTypeId);

    List<ConfigurationPrice> findByFrameExtensionsTypeId(Long frameExtensionsTypeId);
}
