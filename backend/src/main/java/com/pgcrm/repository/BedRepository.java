package com.pgcrm.repository;

import com.pgcrm.entity.Bed;
import com.pgcrm.entity.enums.BedStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@link Bed} entity.
 *
 * <p>Provides query operations against the {@code beds} table for bed management,
 * occupancy reporting, and guest check-in workflows. Several methods use
 * {@code LEFT JOIN FETCH} to eagerly load the full spatial hierarchy
 * ({@code Bed → Room → Floor → Block}) in a single SQL query, preventing the
 * N+1 query problem that would otherwise occur when traversing this chain in
 * application code.</p>
 *
 * <p><strong>Fetch Strategy:</strong> Methods suffixed with {@code WithRoomDetails}
 * or containing {@code FETCH} joins return fully initialised entity graphs safe for
 * use outside of a Hibernate session. Methods without fetch joins (e.g.,
 * {@link #findByRoomId(String)}, {@link #findByStatus(BedStatus)}) return proxied
 * entities with lazily-loaded associations and must be called within a
 * {@code @Transactional} context to avoid {@code LazyInitializationException}.</p>
 *
 * @see Bed
 * @see BedStatus
 */
@Repository
public interface BedRepository extends JpaRepository<Bed, String> {

    /**
     * Returns all beds belonging to the specified room.
     *
     * @param roomId the UUID of the parent {@link com.pgcrm.entity.Room}.
     * @return a {@link List} of {@link Bed} entities in the given room;
     *         empty list if none exist.
     */
    List<Bed> findByRoomId(String roomId);

    /**
     * Returns all beds with the specified occupancy status.
     *
     * @param status the {@link BedStatus} to filter by (e.g., {@code VACANT},
     *               {@code OCCUPIED}, {@code MAINTENANCE}).
     * @return a {@link List} of {@link Bed} entities matching the status;
     *         empty list if none exist.
     */
    List<Bed> findByStatus(BedStatus status);

    /**
     * Returns the total number of beds across all buildings with status {@code VACANT}.
     *
     * <p>Used by the Owner overview dashboard to display the global vacancy count.
     * The string literal {@code 'VACANT'} in the JPQL corresponds to the
     * {@link BedStatus#VACANT} enum constant via Hibernate's enum string mapping.</p>
     *
     * @return the count of all vacant beds in the system.
     */
    @Query("SELECT COUNT(b) FROM Bed b WHERE b.status = 'VACANT'")
    long countVacant();

    /**
     * Returns the total number of beds across all buildings.
     *
     * <p>Used with {@link #countVacant()} to compute the global occupancy rate
     * displayed on the Owner overview dashboard.</p>
     *
     * @return the total bed count in the system.
     */
    @Query("SELECT COUNT(b) FROM Bed b")
    long countTotal();

    /**
     * Returns all vacant beds with their full spatial hierarchy eagerly fetched.
     *
     * <p>The {@code LEFT JOIN FETCH} chain initialises {@code Bed → Room → Floor}
     * and {@code Room → Block} associations in a single SQL query, making the
     * returned entities safe for use in bed-picker dropdowns and check-in forms
     * outside of an active Hibernate session.</p>
     *
     * @return a {@link List} of vacant {@link Bed} entities with full room details,
     *         ordered alphabetically by {@code bedLabel}.
     */
    @Query("""
            SELECT b FROM Bed b
            LEFT JOIN FETCH b.room r
            LEFT JOIN FETCH r.floor f
            LEFT JOIN FETCH r.block
            WHERE b.status = 'VACANT'
            ORDER BY b.bedLabel
            """)
    List<Bed> findVacant();

    /**
     * Returns all vacant beds within a specific building, with full spatial hierarchy fetched.
     *
     * <p>Used during manager-scoped check-in to populate the bed-picker dropdown
     * filtered to the manager's assigned building. The building is identified by
     * traversing the {@code Bed → Room → Floor → Building} association path.</p>
     *
     * @param buildingId the UUID of the building to scope the query to.
     * @return a {@link List} of vacant {@link Bed} entities within the given building,
     *         ordered alphabetically by {@code bedLabel}.
     */
    @Query("""
            SELECT b FROM Bed b
            LEFT JOIN FETCH b.room r
            LEFT JOIN FETCH r.floor f
            LEFT JOIN FETCH r.block
            WHERE b.room.floor.building.id = :buildingId
              AND b.status = 'VACANT'
            ORDER BY b.bedLabel
            """)
    List<Bed> findVacantByBuildingId(@Param("buildingId") String buildingId);

    /**
     * Returns all beds within a specific building, with full spatial hierarchy fetched.
     *
     * <p>Used by the building management view to render the complete bed inventory
     * for a given building, regardless of occupancy status.</p>
     *
     * @param buildingId the UUID of the building to scope the query to.
     * @return a {@link List} of all {@link Bed} entities within the given building,
     *         ordered alphabetically by {@code bedLabel}.
     */
    @Query("""
            SELECT b FROM Bed b
            LEFT JOIN FETCH b.room r
            LEFT JOIN FETCH r.floor f
            LEFT JOIN FETCH r.block
            WHERE b.room.floor.building.id = :buildingId
            ORDER BY b.bedLabel
            """)
    List<Bed> findAllByBuildingId(@Param("buildingId") String buildingId);

    /**
     * Returns all beds in the system with their full spatial hierarchy eagerly fetched.
     *
     * <p>Used by the Platform Admin (PG Owner) for a global bed inventory view.
     * The {@code LEFT JOIN FETCH} chain prevents N+1 queries when rendering the
     * complete multi-building bed list.</p>
     *
     * @return a {@link List} of all {@link Bed} entities in the system,
     *         ordered alphabetically by {@code bedLabel}.
     */
    @Query("""
            SELECT b FROM Bed b
            LEFT JOIN FETCH b.room r
            LEFT JOIN FETCH r.floor f
            LEFT JOIN FETCH r.block
            ORDER BY b.bedLabel
            """)
    List<Bed> findAllWithRoomDetails();

    /**
     * Returns the count of vacant beds within a specific building.
     *
     * <p>Used by the manager dashboard's occupancy summary widget to display
     * building-scoped vacancy statistics without loading full entity data.</p>
     *
     * @param buildingId the UUID of the building to count vacant beds in.
     * @return the number of vacant beds in the specified building.
     */
    @Query("""
            SELECT COUNT(b) FROM Bed b
            WHERE b.room.floor.building.id = :buildingId
              AND b.status = 'VACANT'
            """)
    long countVacantByBuildingId(@Param("buildingId") String buildingId);

    /**
     * Returns the total bed count within a specific building.
     *
     * <p>Used with {@link #countVacantByBuildingId(String)} to compute the
     * building-scoped occupancy rate on the manager dashboard.</p>
     *
     * @param buildingId the UUID of the building to count total beds in.
     * @return the total number of beds in the specified building.
     */
    @Query("""
            SELECT COUNT(b) FROM Bed b
            WHERE b.room.floor.building.id = :buildingId
            """)
    long countTotalByBuildingId(@Param("buildingId") String buildingId);
}
