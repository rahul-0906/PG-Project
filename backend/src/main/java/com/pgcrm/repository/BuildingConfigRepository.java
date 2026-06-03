package com.pgcrm.repository;

import com.pgcrm.entity.BuildingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link BuildingConfig} entity.
 *
 * <p>Provides standard CRUD operations against the {@code building_configs} table.
 * {@link BuildingConfig} maintains a strict <strong>one-to-one</strong> relationship
 * with its parent {@link com.pgcrm.entity.Building}, sharing the same UUID primary key
 * via JPA's {@code @MapsId} semantics.</p>
 *
 * <p>Since the config is always accessed via the parent building's ID (which is the
 * same as the config's own primary key), all lookups are performed using the
 * inherited {@link JpaRepository#findById(Object)} method — no custom query methods
 * are required at this time.</p>
 *
 * <p>The {@code ConfigService} / {@code BuildingService} layer is responsible for
 * ensuring a {@link BuildingConfig} record is created atomically alongside every
 * new {@link com.pgcrm.entity.Building} registration.</p>
 *
 * @see BuildingConfig
 * @see BuildingRepository
 */
@Repository
public interface BuildingConfigRepository extends JpaRepository<BuildingConfig, String> {
}
