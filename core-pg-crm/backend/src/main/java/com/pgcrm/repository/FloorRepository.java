package com.pgcrm.repository;

import com.pgcrm.entity.Floor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@link Floor} entity.
 *
 * <p>Provides query operations against the {@code floors} table to support the
 * building layout management views and the room/bed picker cascade selectors.
 * A {@link Floor} is the second level in the PG CRM spatial hierarchy:
 * {@code Building → Floor → [Block →] Room → Bed}.</p>
 *
 * <p>Floor-level queries are most frequently executed when the frontend renders
 * the floor cascade picker (Building → Floor → Block → Room → Bed dropdowns)
 * during check-in, bed-switch, and room management workflows.</p>
 *
 * @see Floor
 * @see BuildingRepository
 * @see BlockRepository
 * @see RoomRepository
 */
@Repository
public interface FloorRepository extends JpaRepository<Floor, String> {

    /**
     * Returns all floors belonging to the specified building, ordered by the
     * default repository sort (no explicit ordering guarantee).
     *
     * <p>Used by the building detail view, the floor cascade picker in the check-in form,
     * and the kitchen food count aggregation query to enumerate floors within a building.
     * Callers may sort the result by {@code floorNumber} ascending if display order matters.</p>
     *
     * @param buildingId the UUID of the parent {@link com.pgcrm.entity.Building}.
     * @return a {@link List} of {@link Floor} entities belonging to the given building;
     *         empty list if no floors have been created yet.
     */
    List<Floor> findByBuildingId(String buildingId);
}
