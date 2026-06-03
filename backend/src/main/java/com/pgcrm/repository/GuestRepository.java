package com.pgcrm.repository;

import com.pgcrm.entity.Guest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the {@link Guest} entity.
 *
 * <p>Provides query operations against the {@code guests} table for the guest management,
 * billing, EB share allocation, and reporting workflows. This is the most frequently
 * queried repository in the application due to the central role of the
 * {@link Guest} aggregate in all business operations.</p>
 *
 * <p><strong>Soft-Delete Transparency:</strong> The {@link Guest} entity is annotated
 * with Hibernate's {@code @SQLRestriction("is_deleted = false")}, which means all
 * queries through this repository automatically exclude soft-deleted records.
 * The {@link #findByActiveTrue()} and similar methods additionally filter on the
 * {@code active} flag to distinguish checked-in guests from guests who have completed
 * checkout but whose records are still retained for historical reference.</p>
 *
 * <p><strong>Fetch Strategy:</strong> All custom {@code @Query} methods in this
 * repository use {@code LEFT JOIN FETCH} to eagerly load the full association graph:
 * {@code Guest → User}, {@code Guest → Bed → Room → Floor → Block}.
 * This prevents N+1 query problems when the calling service accesses location or
 * user details for multiple guests in a single request. Methods without fetch joins
 * must be called within a {@code @Transactional} service method to prevent
 * {@code LazyInitializationException}.</p>
 *
 * @see Guest
 * @see com.pgcrm.entity.enums.KycStatus
 */
@Repository
public interface GuestRepository extends JpaRepository<Guest, String> {

    /**
     * Returns all currently active (checked-in) guests with their full association
     * graph eagerly fetched.
     *
     * <p>This is the primary query for the manager dashboard's active guest list.
     * The {@code LEFT JOIN FETCH} chain ensures no secondary queries are issued when
     * the service iterates over the returned list to build {@link com.pgcrm.dto.GuestResponse}
     * projections. Excludes soft-deleted and checked-out ({@code active = false}) guests.</p>
     *
     * @return a {@link List} of all active {@link Guest} entities with full location
     *         and user details loaded; empty list if no active guests exist.
     */
    @Query("""
            SELECT g FROM Guest g
            LEFT JOIN FETCH g.user
            LEFT JOIN FETCH g.bed b
            LEFT JOIN FETCH b.room r
            LEFT JOIN FETCH r.floor f
            LEFT JOIN FETCH r.block
            WHERE g.active = true
            """)
    List<Guest> findByActiveTrue();

    /**
     * Finds a guest by their linked {@link com.pgcrm.entity.User} ID, with the full
     * association graph eagerly fetched.
     *
     * <p>Used by the guest portal after JWT authentication to load the requesting
     * guest's profile. The JWT contains the {@code userId} claim; this query resolves
     * it to the corresponding {@link Guest} entity in a single SQL statement.</p>
     *
     * @param userId the UUID of the linked {@link com.pgcrm.entity.User} account.
     * @return an {@link Optional} containing the matching {@link Guest} with full
     *         association graph, or {@link Optional#empty()} if no guest is linked
     *         to the given user.
     */
    @Query("""
            SELECT g FROM Guest g
            LEFT JOIN FETCH g.user
            LEFT JOIN FETCH g.bed b
            LEFT JOIN FETCH b.room r
            LEFT JOIN FETCH r.floor f
            LEFT JOIN FETCH r.block
            WHERE g.user.id = :userId
            """)
    Optional<Guest> findByUserId(@Param("userId") String userId);

    /**
     * Returns all guests who were active within a specific block during a given billing period.
     *
     * <p>Used exclusively by the {@code EbService} to determine which guests should
     * receive an electricity bill share for a given block and billing period. The
     * date-range overlap logic ({@code checkInDate <= periodEnd AND (actualCheckOutDate IS NULL OR actualCheckOutDate >= periodStart)})
     * correctly handles guests who checked in mid-period or checked out before period end.</p>
     *
     * <p>The block is identified via {@code b.room.block.id}, traversing the
     * {@code Bed → Room → Block} path in the entity graph.</p>
     *
     * @param blockId     the UUID of the {@link com.pgcrm.entity.Block} to scope to.
     * @param periodStart the start date of the billing period (inclusive).
     * @param periodEnd   the end date of the billing period (inclusive).
     * @return a {@link List} of active {@link Guest} entities in the block during the
     *         period, with full association graph; empty list if no such guests exist.
     */
    @Query("""
            SELECT g FROM Guest g
            LEFT JOIN FETCH g.user
            LEFT JOIN FETCH g.bed b
            LEFT JOIN FETCH b.room r
            LEFT JOIN FETCH r.floor f
            LEFT JOIN FETCH r.block
            WHERE g.active = true
              AND b.room.block.id = :blockId
              AND g.checkInDate <= :periodEnd
              AND (g.actualCheckOutDate IS NULL OR g.actualCheckOutDate >= :periodStart)
            """)
    List<Guest> findActiveGuestsInBlock(
            @Param("blockId")     String blockId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd")   LocalDate periodEnd);

    /**
     * Returns the total count of currently active (checked-in) guests across all buildings.
     *
     * <p>Used by the Owner overview dashboard's KPI widget to display the global
     * active occupancy count without loading full entity data.</p>
     *
     * @return the number of active guests in the system.
     */
    @Query("SELECT COUNT(g) FROM Guest g WHERE g.active = true")
    long countActive();

    /**
     * Returns all currently active guests within a specific building, with their
     * full association graph eagerly fetched.
     *
     * <p>Used by manager-scoped endpoints (where the JWT carries a {@code branchId})
     * to restrict the guest list to only the buildings the manager is authorised to view.
     * The building is resolved via the {@code Bed → Room → Floor → Building} path.</p>
     *
     * @param buildingId the UUID of the building to scope the query to.
     * @return a {@link List} of active {@link Guest} entities in the specified building
     *         with full location and user details; empty list if none exist.
     */
    @Query("""
            SELECT g FROM Guest g
            LEFT JOIN FETCH g.user
            LEFT JOIN FETCH g.bed b
            LEFT JOIN FETCH b.room r
            LEFT JOIN FETCH r.floor f
            LEFT JOIN FETCH r.block
            WHERE g.active = true
              AND b.room.floor.building.id = :buildingId
            """)
    List<Guest> findActiveGuestsByBuildingId(@Param("buildingId") String buildingId);

    // ── Reporting ─────────────────────────────────────────────────────────────

    /**
     * Returns the number of guests who checked in during a specific calendar month and year.
     *
     * <p>Used by the Owner and manager reporting dashboards to compute the monthly
     * check-in trend for the occupancy chart. Uses JPQL's {@code YEAR()} and
     * {@code MONTH()} functions to extract the date components.</p>
     *
     * @param year  the 4-digit calendar year (e.g., {@code 2026}).
     * @param month the calendar month (1 = January … 12 = December).
     * @return the count of guests whose {@code checkInDate} falls within the given month/year.
     */
    @Query("""
            SELECT COUNT(g) FROM Guest g
            WHERE YEAR(g.checkInDate) = :year
              AND MONTH(g.checkInDate) = :month
            """)
    long countCheckInsByMonthYear(
            @Param("year")  int year,
            @Param("month") int month);

    /**
     * Returns the number of guests who checked out during a specific calendar month and year.
     *
     * <p>Used alongside {@link #countCheckInsByMonthYear(int, int)} to compute monthly
     * check-in vs. check-out trend data for the reporting dashboard. Only counts guests
     * with a non-null {@code actualCheckOutDate} to exclude guests who have issued
     * notice but not yet vacated.</p>
     *
     * @param year  the 4-digit calendar year (e.g., {@code 2026}).
     * @param month the calendar month (1 = January … 12 = December).
     * @return the count of guests whose {@code actualCheckOutDate} falls within the given month/year.
     */
    @Query("""
            SELECT COUNT(g) FROM Guest g
            WHERE g.actualCheckOutDate IS NOT NULL
              AND YEAR(g.actualCheckOutDate) = :year
              AND MONTH(g.actualCheckOutDate) = :month
            """)
    long countCheckOutsByMonthYear(
            @Param("year")  int year,
            @Param("month") int month);

    // ── Payment Reminder Scheduler ────────────────────────────────────────────

    /**
     * Returns all currently active guests with their full association graph fetched.
     *
     * <p>Used specifically by the {@code PaymentReminderScheduler} to iterate over all
     * active guests and dispatch overdue invoice reminder notifications. This variant
     * (without a building filter) is used in the global scheduled job, while
     * {@link #findActiveGuestsByBuildingId(String)} is used for manager-scoped flows.</p>
     *
     * @return a {@link List} of all active {@link Guest} entities with full location
     *         and user details; empty list if no active guests exist.
     */
    @Query("""
            SELECT g FROM Guest g
            LEFT JOIN FETCH g.user
            LEFT JOIN FETCH g.bed b
            LEFT JOIN FETCH b.room r
            LEFT JOIN FETCH r.floor f
            LEFT JOIN FETCH r.block
            WHERE g.active = true
            """)
    List<Guest> findActive();
}
