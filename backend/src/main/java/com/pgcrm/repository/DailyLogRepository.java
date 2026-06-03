package com.pgcrm.repository;

import com.pgcrm.entity.DailyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the {@link DailyLog} entity.
 *
 * <p>Provides query operations against the {@code daily_logs} table to support the
 * guest meal-opt-in management system, kitchen count aggregation, invoice generation,
 * and reporting workflows.</p>
 *
 * <p><strong>Soft-Delete Transparency:</strong> The {@link DailyLog} entity is
 * annotated with Hibernate's {@code @SQLRestriction("is_deleted = false")}, which
 * means all queries through this repository automatically exclude soft-deleted records.
 * No explicit {@code deleted = false} predicate is needed in any query defined here.</p>
 *
 * <p><strong>Composite Index:</strong> The {@code idx_daily_log_guest_date} index on
 * {@code (guest_id, log_date)} is the primary index leveraged by the most frequently
 * executed queries in this repository ({@link #findByGuestIdAndLogDate},
 * {@link #findByGuestIdAndLogDateBetween}).</p>
 *
 * <p><strong>Aggregation Queries:</strong> The kitchen-count queries
 * ({@link #getFoodCountByBuildingAndDate} and {@link #getFoodCountByDate}) return
 * a raw {@code Object[]} array rather than a typed projection to avoid the overhead
 * of creating an additional DTO or interface projection for a simple scalar result.
 * Callers are responsible for index-based extraction of the result columns.</p>
 *
 * @see DailyLog
 * @see com.pgcrm.entity.Guest
 */
@Repository
public interface DailyLogRepository extends JpaRepository<DailyLog, String> {

    /**
     * Finds the unique daily log entry for a specific guest on a specific date.
     *
     * <p>The uniqueness invariant (one log per guest per date) is enforced at the
     * service layer. This method supports both the daily opt-in update flow
     * (upsert pattern) and the invoice generation pipeline's per-day lookup.</p>
     *
     * @param guestId the UUID of the {@link com.pgcrm.entity.Guest}.
     * @param logDate the calendar date of the log entry.
     * @return an {@link Optional} containing the matching {@link DailyLog},
     *         or {@link Optional#empty()} if no entry exists for that guest/date.
     */
    Optional<DailyLog> findByGuestIdAndLogDate(String guestId, LocalDate logDate);

    /**
     * Returns all daily log entries for a given guest, ordered by the
     * default repository sort (no explicit ordering guarantee).
     *
     * <p>Used by the guest portal's meal history view and by the invoice service
     * for generating a full consumption summary for a guest. For billing period
     * scoping, prefer {@link #findByGuestIdAndLogDateBetween(String, LocalDate, LocalDate)}.</p>
     *
     * @param guestId the UUID of the {@link com.pgcrm.entity.Guest}.
     * @return a {@link List} of all non-deleted {@link DailyLog} entries for the guest;
     *         empty list if none exist.
     */
    List<DailyLog> findByGuestId(String guestId);

    /**
     * Returns all daily log entries for a guest within a specified date range (inclusive).
     *
     * <p>Primary method used by the {@code InvoiceService} billing pipeline to
     * aggregate meal and add-on charges for a specific billing period.
     * The date range should correspond to the invoice's billing month.</p>
     *
     * @param guestId the UUID of the {@link com.pgcrm.entity.Guest}.
     * @param start   the start date of the range (inclusive).
     * @param end     the end date of the range (inclusive).
     * @return a {@link List} of {@link DailyLog} entries within the range;
     *         empty list if none exist.
     */
    List<DailyLog> findByGuestIdAndLogDateBetween(String guestId, LocalDate start, LocalDate end);

    /**
     * Returns aggregated kitchen meal and service counts for a specific building and date.
     *
     * <p>Used by the kitchen management view to display the total number of each
     * meal type and add-on service to prepare for a given day, scoped to a single building.
     * The association path {@code d.guest.bed.room.floor.building.id} traverses the full
     * spatial hierarchy to filter by building.</p>
     *
     * <p><strong>Result array column mapping (index-based):</strong></p>
     * <ol start="0">
     *   <li>{@code [0]} — {@code breakfast}: total breakfast opt-ins.</li>
     *   <li>{@code [1]} — {@code lunch}: total lunch opt-ins.</li>
     *   <li>{@code [2]} — {@code dinner}: total dinner opt-ins.</li>
     *   <li>{@code [3]} — {@code omelettes}: total omelette units.</li>
     *   <li>{@code [4]} — {@code boiledEggs}: total boiled egg units.</li>
     *   <li>{@code [5]} — {@code laundry}: total washing machine cycles.</li>
     * </ol>
     *
     * @param buildingId the UUID of the building to aggregate counts for.
     * @param date       the calendar date to aggregate counts for.
     * @return a single-element {@code Object[]} row of aggregated scalar counts.
     */
    @Query("""
            SELECT
                SUM(CASE WHEN d.breakfastOpted THEN 1 ELSE 0 END) AS breakfast,
                SUM(CASE WHEN d.lunchOpted    THEN 1 ELSE 0 END) AS lunch,
                SUM(CASE WHEN d.dinnerOpted   THEN 1 ELSE 0 END) AS dinner,
                SUM(d.omeletteCount)                              AS omelettes,
                SUM(d.boiledEggCount)                             AS boiledEggs,
                SUM(d.washingMachineCount)                        AS laundry
            FROM DailyLog d
            WHERE d.guest.bed.room.floor.building.id = :buildingId
              AND d.logDate = :date
            """)
    Object[] getFoodCountByBuildingAndDate(
            @Param("buildingId") String buildingId,
            @Param("date")       LocalDate date);

    /**
     * Returns aggregated kitchen meal and service counts for a specific date,
     * across all buildings (global / single-tenant mode).
     *
     * <p>Used in single-building deployments where no building-level scoping is
     * needed. The result array format is identical to
     * {@link #getFoodCountByBuildingAndDate(String, LocalDate)}.</p>
     *
     * <p><strong>Result array column mapping (index-based):</strong></p>
     * <ol start="0">
     *   <li>{@code [0]} — {@code breakfast}: total breakfast opt-ins.</li>
     *   <li>{@code [1]} — {@code lunch}: total lunch opt-ins.</li>
     *   <li>{@code [2]} — {@code dinner}: total dinner opt-ins.</li>
     *   <li>{@code [3]} — {@code omelettes}: total omelette units.</li>
     *   <li>{@code [4]} — {@code boiledEggs}: total boiled egg units.</li>
     *   <li>{@code [5]} — {@code laundry}: total washing machine cycles.</li>
     * </ol>
     *
     * @param date the calendar date to aggregate counts for.
     * @return a single-element {@code Object[]} row of aggregated scalar counts.
     */
    @Query("""
            SELECT
                SUM(CASE WHEN d.breakfastOpted THEN 1 ELSE 0 END) AS breakfast,
                SUM(CASE WHEN d.lunchOpted    THEN 1 ELSE 0 END) AS lunch,
                SUM(CASE WHEN d.dinnerOpted   THEN 1 ELSE 0 END) AS dinner,
                SUM(d.omeletteCount)                              AS omelettes,
                SUM(d.boiledEggCount)                             AS boiledEggs,
                SUM(d.washingMachineCount)                        AS laundry
            FROM DailyLog d
            WHERE d.logDate = :date
            """)
    Object[] getFoodCountByDate(@Param("date") LocalDate date);

    /**
     * Returns all daily log entries within a specified date range (inclusive), across
     * all guests and buildings.
     *
     * <p>Used by bulk reporting and data export workflows that require a full
     * cross-guest view for a period (e.g., daily kitchen summary exports).</p>
     *
     * @param start the start date of the range (inclusive).
     * @param end   the end date of the range (inclusive).
     * @return a {@link List} of all non-deleted {@link DailyLog} entries within the range.
     */
    List<DailyLog> findByLogDateBetween(LocalDate start, LocalDate end);

    /**
     * Returns all daily log entries for a guest where at least one add-on service
     * (omelette, boiled egg, or washing machine) was consumed.
     *
     * <p>Used by the invoice service's add-on charge aggregation step to efficiently
     * retrieve only the relevant log entries without loading the full meal history.
     * Results are ordered by date descending for display in the guest's consumption history.</p>
     *
     * @param guestId the UUID of the {@link com.pgcrm.entity.Guest}.
     * @return a {@link List} of {@link DailyLog} entries with non-zero add-on counts,
     *         ordered by {@code logDate DESC}.
     */
    @Query("""
            SELECT d FROM DailyLog d
            WHERE d.guest.id = :guestId
              AND (d.omeletteCount > 0 OR d.boiledEggCount > 0 OR d.washingMachineCount > 0)
            ORDER BY d.logDate DESC
            """)
    List<DailyLog> findAddonsByGuestId(@Param("guestId") String guestId);
}
