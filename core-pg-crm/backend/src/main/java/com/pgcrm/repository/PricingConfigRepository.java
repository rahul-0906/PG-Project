package com.pgcrm.repository;

import com.pgcrm.entity.PricingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the {@link PricingConfig} entity.
 *
 * <p>Provides lookup operations against the {@code pricing_config} table, which stores
 * per-building price overrides for named service types (meals, add-ons, laundry).
 * Each row is uniquely identified by a {@code (buildingId, priceKey)} composite key,
 * enforced by a database unique constraint.</p>
 *
 * <p><strong>Price Resolution Precedence:</strong> The {@code PricingService} first
 * queries this repository for a building-specific override. If no override row is found
 * for the requested {@code (buildingId, priceKey)} pair, the service falls back to the
 * corresponding field in {@link com.pgcrm.entity.BuildingConfig}.</p>
 *
 * <p><strong>Supported Price Keys:</strong> {@code "breakfast"}, {@code "lunch"},
 * {@code "dinner"}, {@code "omelette"}, {@code "boiled_egg"}, {@code "washing_machine"}.</p>
 *
 * @see PricingConfig
 * @see BuildingConfigRepository
 */
@Repository
public interface PricingConfigRepository extends JpaRepository<PricingConfig, String> {

    /**
     * Returns all pricing override records for a specific building.
     *
     * <p>Used by the manager/owner configuration UI to display and edit the
     * full set of price overrides currently configured for a building.</p>
     *
     * @param buildingId the UUID of the building whose price overrides to retrieve.
     * @return a {@link List} of {@link PricingConfig} records for the building;
     *         empty list if no overrides have been configured.
     */
    List<PricingConfig> findByBuildingId(String buildingId);

    /**
     * Finds a specific pricing override for a building and price key combination.
     *
     * <p>The primary lookup method used by the {@code PricingService} during the
     * price resolution flow. The {@code priceKey} is one of the supported string
     * constants (e.g., {@code "breakfast"}, {@code "washing_machine"}).</p>
     *
     * @param buildingId the UUID of the building.
     * @param priceKey   the canonical price type key string.
     * @return an {@link Optional} containing the matching {@link PricingConfig} override,
     *         or {@link Optional#empty()} if no override exists for this
     *         {@code (buildingId, priceKey)} pair (fall back to {@link com.pgcrm.entity.BuildingConfig}).
     */
    Optional<PricingConfig> findByBuildingIdAndPriceKey(String buildingId, String priceKey);
}
