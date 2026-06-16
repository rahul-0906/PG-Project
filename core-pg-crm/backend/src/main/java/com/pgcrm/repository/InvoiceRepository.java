package com.pgcrm.repository;

import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the {@link Invoice} entity.
 *
 * <p>Provides query and aggregation operations against the {@code invoices} and
 * {@code invoice_line_items} tables to support the monthly billing pipeline, the
 * payment reminder scheduler, and the Owner/Manager reporting dashboards.</p>
 *
 * <p><strong>Soft-Delete Transparency:</strong> The {@link Invoice} entity is annotated
 * with Hibernate's {@code @SQLRestriction("is_deleted = false")}, so all queries
 * in this repository automatically exclude soft-deleted invoice records.</p>
 *
 * <p><strong>Billing Period Uniqueness:</strong> The combination of
 * {@code (guestId, month, year)} is enforced as a logical unique constraint by the
 * service layer using {@link #findByGuestIdAndMonthAndYear(String, int, int)}.
 * The {@code InvoiceService} checks for an existing record before creating a new one
 * to prevent duplicate invoices for the same guest and period.</p>
 *
 * <p><strong>Cross-Table JPQL:</strong> The {@link #getRevenueBreakdown(int, int)} query
 * operates on the {@code InvoiceLineItem} entity rather than {@code Invoice} directly,
 * joining through the parent invoice to apply the billing period filter.</p>
 *
 * @see Invoice
 * @see com.pgcrm.entity.InvoiceLineItem
 * @see InvoiceStatus
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    /**
     * Returns all invoices for a specific guest, across all billing periods.
     *
     * <p>Used by the guest portal's invoice history view and the manager's
     * per-guest billing history tab. Results are returned in the default
     * repository order (no explicit ordering — callers may sort by {@code year}/{@code month}
     * descending for display).</p>
     *
     * @param guestId the UUID of the {@link com.pgcrm.entity.Guest}.
     * @return a {@link List} of non-deleted {@link Invoice} records for the guest;
     *         empty list if none exist.
     */
    List<Invoice> findByGuestId(String guestId);

    /**
     * Finds the unique invoice for a specific guest in a given billing month and year.
     *
     * <p>Used by the {@code InvoiceService} as the primary duplicate-guard lookup
     * before creating a new invoice. Also used to retrieve an existing invoice for
     * update operations (e.g., attaching Razorpay order IDs after payment initiation).</p>
     *
     * @param guestId the UUID of the {@link com.pgcrm.entity.Guest}.
     * @param month   the billing month (1 = January … 12 = December).
     * @param year    the 4-digit billing year (e.g., {@code 2026}).
     * @return an {@link Optional} containing the matching {@link Invoice},
     *         or {@link Optional#empty()} if no invoice exists for this guest/period.
     */
    Optional<Invoice> findByGuestIdAndMonthAndYear(String guestId, int month, int year);

    /**
     * Returns all invoices for a given billing month and year, across all guests.
     *
     * <p>Used by the manager's monthly billing summary view to list all invoices
     * generated for a specific period, regardless of payment status.</p>
     *
     * @param month the billing month (1–12).
     * @param year  the 4-digit billing year.
     * @return a {@link List} of non-deleted {@link Invoice} records for the period;
     *         empty list if none exist.
     */
    List<Invoice> findByMonthAndYear(int month, int year);

    /**
     * Returns all invoices with the specified payment status.
     *
     * <p>Used by the payment reminder scheduler and the manager's filtered invoice
     * view to retrieve invoices by their current {@link InvoiceStatus}.</p>
     *
     * @param status the {@link InvoiceStatus} to filter by.
     * @return a {@link List} of non-deleted {@link Invoice} records with the given status;
     *         empty list if none exist.
     */
    List<Invoice> findByStatus(InvoiceStatus status);

    // ── Reporting ─────────────────────────────────────────────────────────────

    /**
     * Returns the total revenue grouped by invoice line item type for a given billing period.
     *
     * <p>Used by the Owner and Manager reporting dashboards to render the revenue
     * breakdown chart (Rent vs. EB vs. Food vs. Laundry). The query operates directly
     * on the {@code InvoiceLineItem} entity and joins upward to the parent invoice to
     * apply the billing period and status filters.</p>
     *
     * <p>Only invoices with status {@code GENERATED} or {@code PAID} are included,
     * excluding overdue or pending-cash-verification statuses from the revenue aggregation
     * to avoid double-counting.</p>
     *
     * <p><strong>Result array column mapping (per row):</strong></p>
     * <ul>
     *   <li>{@code result[0]} — the {@link com.pgcrm.entity.enums.InvoiceLineType} enum value.</li>
     *   <li>{@code result[1]} — the {@link BigDecimal} sum of all amounts for that type.</li>
     * </ul>
     *
     * @param year  the 4-digit billing year.
     * @param month the billing month (1–12).
     * @return a {@link List} of {@code Object[]} rows, one per distinct line item type;
     *         empty list if no matching invoices exist.
     */
    @Query("""
            SELECT li.type, SUM(li.amount)
            FROM InvoiceLineItem li
            WHERE li.invoice.year  = :year
              AND li.invoice.month = :month
              AND li.invoice.status IN ('GENERATED', 'PAID')
            GROUP BY li.type
            """)
    List<Object[]> getRevenueBreakdown(
            @Param("year")  int year,
            @Param("month") int month);

    /**
     * Returns the count of distinct guests who were billed in a specific month and year.
     *
     * <p>Used by the reporting dashboard to display monthly billing coverage statistics
     * (e.g., "42 guests invoiced in May 2026").</p>
     *
     * @param year  the 4-digit billing year.
     * @param month the billing month (1–12).
     * @return the count of distinct guest IDs with invoices in the given period.
     */
    @Query("""
            SELECT COUNT(DISTINCT i.guest.id) FROM Invoice i
            WHERE i.year = :year AND i.month = :month
            """)
    long countDistinctGuestsByMonthYear(
            @Param("year")  int year,
            @Param("month") int month);

    /**
     * Returns the count of invoices with the given status in a specific year.
     *
     * <p>Used by the Owner annual report to compute how many invoices were paid vs.
     * generated vs. overdue within a calendar year.</p>
     *
     * @param year   the 4-digit calendar year.
     * @param status the {@link InvoiceStatus} to count.
     * @return the number of invoices matching the year and status.
     */
    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.year = :year AND i.status = :status")
    long countByYearAndStatus(
            @Param("year")   int year,
            @Param("status") InvoiceStatus status);

    /**
     * Returns the total paid revenue for a given calendar year.
     *
     * <p>Used by the Owner annual financial summary to display total collected revenue.
     * Only invoices with status {@code PAID} are summed to reflect actually collected
     * amounts rather than billed amounts.</p>
     *
     * @param year the 4-digit calendar year.
     * @return the sum of {@link Invoice#getTotalAmount()} for all paid invoices in the year;
     *         may return {@code null} if no paid invoices exist for that year.
     */
    @Query("SELECT SUM(i.totalAmount) FROM Invoice i WHERE i.year = :year AND i.status = 'PAID'")
    BigDecimal sumPaidAmountByYear(@Param("year") int year);

    // ── Payment Reminder Scheduler ────────────────────────────────────────────

    /**
     * Returns all unpaid invoices whose due date has passed and whose payment reminder
     * has not yet been sent.
     *
     * <p>Consumed by the {@code PaymentReminderScheduler} (a {@code @Scheduled} cron job)
     * to identify overdue invoices requiring a WhatsApp or email reminder dispatch.
     * After the reminder is sent, the service sets {@link Invoice#getReminderSentAt()}
     * to the current timestamp to prevent duplicate reminders.</p>
     *
     * <p><strong>Filter Criteria:</strong></p>
     * <ul>
     *   <li>Status must be {@code GENERATED} — {@code PAID} and {@code OVERDUE}
     *       invoices are excluded.</li>
     *   <li>{@code dueDate} must be non-null and on or before the {@code cutoff} date.</li>
     *   <li>{@code reminderSentAt} must be {@code null} — prevents repeat reminders.</li>
     * </ul>
     *
     * @param cutoff the reference date (usually today); invoices with {@code dueDate}
     *               on or before this date are included.
     * @return a {@link List} of {@link Invoice} entities matching all reminder dispatch criteria;
     *         empty list if no such invoices exist.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.status = 'GENERATED'
              AND i.dueDate IS NOT NULL
              AND i.reminderSentAt IS NULL
              AND i.dueDate <= :cutoff
            """)
    List<Invoice> findUnpaidInvoicesDueBy(@Param("cutoff") LocalDate cutoff);
}
