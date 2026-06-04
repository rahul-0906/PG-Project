package com.pgcrm.service;

import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.repository.AuditLogRepository;
import com.pgcrm.repository.BedRepository;
import com.pgcrm.repository.EbBillRepository;
import com.pgcrm.repository.GuestRepository;
import com.pgcrm.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for generating owner-level financial and operational reports.
 *
 * <p>All methods are read-only (annotated with {@link Transactional @Transactional(readOnly=true)})
 * and return structured data maps suitable for direct serialisation to the frontend reporting
 * dashboard without additional DTO mapping.</p>
 *
 * <p>Supported report types:</p>
 * <ul>
 *   <li><strong>Monthly Revenue Summary:</strong> Per-month breakdown of revenue by line-item
 *       type (Rent, EB, Food, Laundry) across all 12 months of a given year.</li>
 *   <li><strong>Occupancy Report:</strong> Per-month occupancy bed count and occupancy percentage
 *       derived from the number of distinct guests invoiced in that month.</li>
 *   <li><strong>Guest Turnover Report:</strong> Per-month count of check-ins and check-outs
 *       to track tenant churn over a year.</li>
 *   <li><strong>Payment Summary:</strong> Aggregate invoice counts by status
 *       (GENERATED, PAID, OVERDUE) and total collected revenue for a given year.</li>
 * </ul>
 *
 * @see InvoiceRepository
 * @see GuestRepository
 * @see BedRepository
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final InvoiceRepository  invoiceRepository;
    private final GuestRepository    guestRepository;
    private final BedRepository      bedRepository;
    private final EbBillRepository   ebBillRepository;
    private final AuditLogRepository auditLogRepository;

    /** Abbreviated month names used as labels in all monthly report output maps. */
    private static final String[] MONTH_NAMES =
            {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    /**
     * Returns the monthly revenue breakdown for every month in the given year,
     * decomposed by invoice line-item type.
     *
     * <p>Each map in the returned list represents one calendar month and contains:</p>
     * <ul>
     *   <li>{@code month} — abbreviated month name (e.g., {@code "Jan"}).</li>
     *   <li>{@code monthNum} — 1-based month number (1–12).</li>
     *   <li>{@code rent} — total rent revenue ({@link BigDecimal}).</li>
     *   <li>{@code eb} — total electricity bill revenue ({@link BigDecimal}).</li>
     *   <li>{@code food} — total food revenue ({@link BigDecimal}).</li>
     *   <li>{@code laundry} — total laundry revenue ({@link BigDecimal}).</li>
     *   <li>{@code total} — sum of all four line types ({@link BigDecimal}).</li>
     * </ul>
     *
     * <p>Months with no invoices return {@link BigDecimal#ZERO} for all revenue fields.</p>
     *
     * @param year the calendar year to report on (e.g., {@code 2025}).
     * @return a 12-element {@link List} of revenue maps, ordered January → December.
     */
    public List<Map<String, Object>> getMonthlyRevenueSummary(final int year) {
        final List<Map<String, Object>> result = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            final List<Object[]> rows = invoiceRepository.getRevenueBreakdown(year, m);

            BigDecimal rent = BigDecimal.ZERO, eb      = BigDecimal.ZERO,
                       food = BigDecimal.ZERO, laundry = BigDecimal.ZERO;

            for (final Object[] row : rows) {
                // row[0] is the InvoiceLineType enum — use toString() to get the name string.
                final String     type   = row[0] != null ? row[0].toString() : "";
                final BigDecimal amount = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
                switch (type) {
                    case "RENT"    -> rent    = amount;
                    case "EB"      -> eb      = amount;
                    case "FOOD"    -> food    = amount;
                    case "LAUNDRY" -> laundry = amount;
                }
            }

            result.add(Map.of(
                    "month",    MONTH_NAMES[m - 1],
                    "monthNum", m,
                    "rent",     rent,
                    "eb",       eb,
                    "food",     food,
                    "laundry",  laundry,
                    "total",    rent.add(eb).add(food).add(laundry)
            ));
        }
        return result;
    }

    /**
     * Returns the monthly bed occupancy trend for every month in the given year.
     *
     * <p>Occupancy is approximated by the count of distinct guests who had an invoice
     * generated in that month ({@link InvoiceRepository#countDistinctGuestsByMonthYear}).
     * The occupancy percentage is capped at 100 to handle edge cases where the invoiced
     * count exceeds the total bed count (e.g., mid-month bed switches).</p>
     *
     * <p>Each map in the returned list contains:</p>
     * <ul>
     *   <li>{@code month} — abbreviated month name.</li>
     *   <li>{@code monthNum} — 1-based month number.</li>
     *   <li>{@code totalBeds} — total bed count across all buildings.</li>
     *   <li>{@code occupiedBeds} — distinct guests invoiced that month.</li>
     *   <li>{@code occupancyPct} — rounded occupancy percentage (0–100).</li>
     * </ul>
     *
     * @param year the calendar year to report on.
     * @return a 12-element {@link List} of occupancy maps, ordered January → December.
     */
    public List<Map<String, Object>> getOccupancyReport(final int year) {
        final long                      totalBeds = bedRepository.countTotal();
        final List<Map<String, Object>> result    = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            final long   invoiced = invoiceRepository.countDistinctGuestsByMonthYear(year, m);
            final double pct      = totalBeds > 0 ? (invoiced * 100.0 / totalBeds) : 0;
            result.add(Map.of(
                    "month",        MONTH_NAMES[m - 1],
                    "monthNum",     m,
                    "totalBeds",    totalBeds,
                    "occupiedBeds", invoiced,
                    "occupancyPct", Math.min(100, Math.round(pct))
            ));
        }
        return result;
    }

    /**
     * Returns the monthly guest check-in and check-out counts for every month in the given year.
     *
     * <p>Each map in the returned list contains:</p>
     * <ul>
     *   <li>{@code month} — abbreviated month name.</li>
     *   <li>{@code monthNum} — 1-based month number.</li>
     *   <li>{@code checkIns} — number of guests who checked in during the month.</li>
     *   <li>{@code checkOuts} — number of guests who checked out during the month.</li>
     * </ul>
     *
     * @param year the calendar year to report on.
     * @return a 12-element {@link List} of turnover maps, ordered January → December.
     */
    public List<Map<String, Object>> getGuestTurnoverReport(final int year) {
        final List<Map<String, Object>> result = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            final long checkIns  = guestRepository.countCheckInsByMonthYear(year, m);
            final long checkOuts = guestRepository.countCheckOutsByMonthYear(year, m);
            result.add(Map.of(
                    "month",     MONTH_NAMES[m - 1],
                    "monthNum",  m,
                    "checkIns",  checkIns,
                    "checkOuts", checkOuts
            ));
        }
        return result;
    }

    /**
     * Returns an aggregate payment status summary for the given year.
     *
     * <p>The returned map contains:</p>
     * <ul>
     *   <li>{@code generated} — count of invoices with status {@link InvoiceStatus#GENERATED}.</li>
     *   <li>{@code paid} — count of invoices with status {@link InvoiceStatus#PAID}.</li>
     *   <li>{@code overdue} — count of invoices with status {@link InvoiceStatus#OVERDUE}.</li>
     *   <li>{@code totalRevenue} — sum of all paid invoice totals; {@link BigDecimal#ZERO} if none.</li>
     * </ul>
     *
     * @param year the calendar year to aggregate.
     * @return a {@link Map} containing the four payment summary fields.
     */
    public Map<String, Object> getPaymentSummary(final int year) {
        final long       generated    = invoiceRepository.countByYearAndStatus(year, InvoiceStatus.GENERATED);
        final long       paid         = invoiceRepository.countByYearAndStatus(year, InvoiceStatus.PAID);
        final long       overdue      = invoiceRepository.countByYearAndStatus(year, InvoiceStatus.OVERDUE);
        final BigDecimal totalRevenue = invoiceRepository.sumPaidAmountByYear(year);

        return Map.of(
                "generated",    generated,
                "paid",         paid,
                "overdue",      overdue,
                "totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO
        );
    }
}
