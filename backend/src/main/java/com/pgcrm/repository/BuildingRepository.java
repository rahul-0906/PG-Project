package com.pgcrm.repository;

import com.pgcrm.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the {@link Building} entity.
 *
 * <p>Provides lookup operations against the {@code buildings} table to support
 * building management workflows for the Platform Admin (PG Owner). The
 * {@link Building} entity is the root aggregate of the PG CRM spatial hierarchy:
 * {@code Building → Floor → Block → Room → Bed}.</p>
 *
 * <p>The {@code name} field carries a database-level unique constraint (case-insensitive
 * at the application layer via the {@code IgnoreCase} variants below) to prevent
 * accidental duplicate building registrations that would cause data isolation issues.</p>
 *
 * @see Building
 * @see FloorRepository
 */
@Repository
public interface BuildingRepository extends JpaRepository<Building, String> {

    /**
     * Checks whether a building with the given name already exists,
     * using a case-insensitive comparison.
     *
     * <p>Used by the {@code BuildingService} as a pre-flight duplicate check
     * before creating a new building, producing a clean application-level error
     * rather than a raw database constraint violation.</p>
     *
     * @param name the building name to check for existence.
     * @return {@code true} if a building with this name (any case) already exists;
     *         {@code false} otherwise.
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Looks up a building by name using a case-insensitive comparison.
     *
     * <p>Used for building resolution by name in scenarios where the caller has
     * a human-provided name string rather than a UUID (e.g., CLI seed scripts,
     * configuration bootstrapping).</p>
     *
     * @param name the building name to search for.
     * @return an {@link Optional} containing the matching {@link Building},
     *         or {@link Optional#empty()} if no match is found.
     */
    Optional<Building> findByNameIgnoreCase(String name);
}
