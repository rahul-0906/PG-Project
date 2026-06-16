package com.pgcrm.repository;

import com.pgcrm.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@link Room} entity.
 *
 * <p>Provides query operations against the {@code rooms} table to support building
 * layout management and room/bed picker cascade selectors. Rooms exist in two modes
 * within the spatial hierarchy:</p>
 * <ul>
 *   <li><strong>Block-organised:</strong> {@code Building → Floor → Block → Room → Bed}.
 *       Accessed via {@link #findByBlockId(String)}.</li>
 *   <li><strong>Standalone:</strong> {@code Building → Floor → Room → Bed} (no block).
 *       Accessed via {@link #findByFloorId(String)}.</li>
 * </ul>
 *
 * @see Room
 * @see BlockRepository
 * @see FloorRepository
 * @see BedRepository
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, String> {

    /**
     * Returns all rooms belonging to the specified block.
     *
     * <p>Used when rendering the block detail view and the room cascade selector
     * in check-in forms where the guest selects a block before choosing a room.</p>
     *
     * @param blockId the UUID of the parent {@link com.pgcrm.entity.Block}.
     * @return a {@link List} of {@link Room} entities in the given block;
     *         empty list if none exist.
     */
    List<Room> findByBlockId(String blockId);

    /**
     * Returns all rooms directly assigned to a floor (standalone rooms without a block).
     *
     * <p>Used when rendering the floor detail view for layouts that do not use
     * block-level subdivisions. Also used to list all rooms (block-organised and standalone)
     * on a floor when both modes are mixed.</p>
     *
     * @param floorId the UUID of the parent {@link com.pgcrm.entity.Floor}.
     * @return a {@link List} of {@link Room} entities on the given floor;
     *         empty list if none exist.
     */
    List<Room> findByFloorId(String floorId);

    /**
     * Returns all rooms within a specific building, traversing the
     * {@code Room → Floor → Building} association path.
     *
     * <p>Uses Spring Data's derived query naming convention with the underscore
     * separator ({@code _}) to navigate the {@code floor.building.id} path.
     * Used by building-level management views and rent configuration screens to
     * list every room in a building regardless of floor or block assignment.</p>
     *
     * @param buildingId the UUID of the target {@link com.pgcrm.entity.Building}.
     * @return a {@link List} of all {@link Room} entities in the given building;
     *         empty list if none exist.
     */
    List<Room> findByFloor_Building_Id(String buildingId);
}
