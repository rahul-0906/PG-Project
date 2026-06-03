package com.pgcrm.repository;

import com.pgcrm.entity.AuditLog;
import com.pgcrm.entity.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA repository for the {@link AuditLog} entity.
 *
 * <p>Provides read-only query operations against the {@code audit_logs} table.
 * Audit log records are <strong>immutable</strong> after creation — no update or
 * delete operations should be performed through this repository, as the audit trail
 * must remain an inviolable historical record for compliance purposes.</p>
 *
 * <p>All queries return results ordered by {@code timestamp DESC} to surface the
 * most recent events first, consistent with the Owner audit dashboard's default view.</p>
 *
 * <p><strong>Index utilisation:</strong> The custom JPQL queries in this repository
 * are designed to leverage the database indexes defined on the {@code audit_logs} table:
 * {@code idx_audit_time} (on {@code timestamp}) and {@code idx_audit_action}
 * (on {@code action}). Range predicates on {@code timestamp} should always be bounded
 * to ensure index range scans rather than full table scans.</p>
 *
 * @see AuditLog
 * @see AuditAction
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    /**
     * Returns a paginated list of all audit log entries, ordered by most recent first.
     *
     * <p>Used by the Owner dashboard's default audit log view when no filter criteria
     * have been applied. Pagination is applied by Spring Data using the provided
     * {@link Pageable} descriptor, leveraging the {@code idx_audit_time} index.</p>
     *
     * @param pageable the pagination and sorting descriptor; must not be {@code null}.
     * @return a {@link Page} of {@link AuditLog} entries ordered by {@code timestamp DESC}.
     */
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    /**
     * Returns a paginated list of audit log entries matching the given filter criteria.
     *
     * <p>Supports the Owner dashboard's filtered audit view. All parameters are
     * optional except the date range, which is always required to bound the query
     * and prevent unbounded full-table scans.</p>
     *
     * <p>The {@code :action IS NULL OR a.action = :action} predicate enables optional
     * action filtering — passing {@code null} for {@code action} returns records for
     * all action types within the date range.</p>
     *
     * @param action   the specific {@link AuditAction} to filter by; pass {@code null}
     *                 to return records for all action types.
     * @param from     the start of the timestamp range (inclusive); must not be {@code null}.
     * @param to       the end of the timestamp range (inclusive); must not be {@code null}.
     * @param pageable the pagination and sorting descriptor; must not be {@code null}.
     * @return a {@link Page} of matching {@link AuditLog} entries ordered by {@code timestamp DESC}.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:action IS NULL OR a.action = :action)
              AND a.timestamp BETWEEN :from AND :to
            ORDER BY a.timestamp DESC
            """)
    Page<AuditLog> findByFilters(
            @Param("action") AuditAction action,
            @Param("from")   LocalDateTime from,
            @Param("to")     LocalDateTime to,
            Pageable pageable);

    /**
     * Returns all audit log entries within the given timestamp range for export.
     *
     * <p>Used by the Owner audit export endpoint to generate CSV or Excel reports
     * covering a specified period. Returns an unbounded {@link List} rather than a
     * {@link Page} because the full result set is required for file generation.
     * Callers should apply reasonable date-range bounds (e.g., no more than one year)
     * to avoid excessive memory consumption.</p>
     *
     * @param from the start of the export period (inclusive); must not be {@code null}.
     * @param to   the end of the export period (inclusive); must not be {@code null}.
     * @return a {@link List} of all {@link AuditLog} entries within the range,
     *         ordered by {@code timestamp DESC}.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.timestamp BETWEEN :from AND :to
            ORDER BY a.timestamp DESC
            """)
    List<AuditLog> findForExport(
            @Param("from") LocalDateTime from,
            @Param("to")   LocalDateTime to);
}
