package com.pgcrm.repository;

import com.pgcrm.entity.PricingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PricingConfigRepository extends JpaRepository<PricingConfig, String> {

    List<PricingConfig> findByBuildingId(String buildingId);

    Optional<PricingConfig> findByBuildingIdAndPriceKey(String buildingId, String priceKey);
}
