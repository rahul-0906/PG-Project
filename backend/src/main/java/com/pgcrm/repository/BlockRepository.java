package com.pgcrm.repository;

import com.pgcrm.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@link Block} entity.
 *
 * <p>Provides lookup operations against the {@code blocks} table to support the
 * building hierarchy management views and room/bed picker components.
 * A {@link Block} is a named group of rooms on a floor (e.g., "Block A", "Girls' Wing")
 * within the spatial hierarchy: {@code Building → Floor → Block → Room → Bed}.</p>
 *
 * @see Block
 * @see FloorRepository
 * @see RoomRepository
 */
@Repository
public interface BlockRepository extends JpaRepository<Block, String> {

    /**
     * Returns all blocks belonging to the specified floor.
     *
     * <p>Used when rendering the floor detail view to list all blocks
     * and their child rooms. Corresponds to the direct parent-child
     * {@code Floor → Block} relationship.</p>
     *
     * @param floorId the UUID of the parent {@link com.pgcrm.entity.Floor}.
     * @return a {@link List} of {@link Block} entities on the given floor;
     *         empty list if none exist.
     */
    List<Block> findByFloorId(String floorId);

    /**
     * Returns all blocks within a specific building, traversing the
     * {@code Block → Floor → Building} association path.
     *
     * <p>Uses Spring Data's derived query naming convention with the underscore
     * separator ({@code _}) to navigate the {@code floor.building.id} path.
     * Used by the building management view and EB bill recording form to list
     * all blocks available in a building.</p>
     *
     * @param buildingId the UUID of the target {@link com.pgcrm.entity.Building}.
     * @return a {@link List} of {@link Block} entities in the given building;
     *         empty list if none exist.
     */
    List<Block> findByFloor_Building_Id(String buildingId);
}
