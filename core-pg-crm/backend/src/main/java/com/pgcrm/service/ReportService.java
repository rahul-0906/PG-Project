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
        return getMonthlyRevenueSummary(year, null);
    }

    public List<Map<String, Object>> getMonthlyRevenueSummary(final int year, final String buildingId) {
        final List<Map<String, Object>> result = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            final List<Object[]> rows = invoiceRepository.getRevenueBreakdown(year, m, buildingId);

            BigDecimal rent = BigDecimal.ZERO, eb      = BigDecimal.ZERO,
                       food = BigDecimal.ZERO, laundry = BigDecimal.ZERO;

            for (final Object[] row : rows) {
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

    public List<Map<String, Object>> getOccupancyReport(final int year) {
        return getOccupancyReport(year, null);
    }

    public List<Map<String, Object>> getOccupancyReport(final int year, final String buildingId) {
        final long totalBeds = buildingId != null ? bedRepository.countTotalByBuildingId(buildingId) : bedRepository.countTotal();
        final List<Map<String, Object>> result = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            final long   invoiced = invoiceRepository.countDistinctGuestsByMonthYear(year, m, buildingId);
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

    public List<Map<String, Object>> getGuestTurnoverReport(final int year) {
        return getGuestTurnoverReport(year, null);
    }

    public List<Map<String, Object>> getGuestTurnoverReport(final int year, final String buildingId) {
        final List<Map<String, Object>> result = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            final long checkIns  = guestRepository.countCheckInsByMonthYear(year, m, buildingId);
            final long checkOuts = guestRepository.countCheckOutsByMonthYear(year, m, buildingId);
            result.add(Map.of(
                    "month",     MONTH_NAMES[m - 1],
                    "monthNum",  m,
                    "checkIns",  checkIns,
                    "checkOuts", checkOuts
            ));
        }
        return result;
    }

    public Map<String, Object> getPaymentSummary(final int year) {
        return getPaymentSummary(year, null);
    }

    public Map<String, Object> getPaymentSummary(final int year, final String buildingId) {
        final long       generated    = invoiceRepository.countByYearAndStatus(year, InvoiceStatus.GENERATED, buildingId);
        final long       paid         = invoiceRepository.countByYearAndStatus(year, InvoiceStatus.PAID, buildingId);
        final long       overdue      = invoiceRepository.countByYearAndStatus(year, InvoiceStatus.OVERDUE, buildingId);
        final BigDecimal totalRevenue = invoiceRepository.sumPaidAmountByYear(year, buildingId);

        return Map.of(
                "generated",    generated,
                "paid",         paid,
                "overdue",      overdue,
                "totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO
        );
    }
}
