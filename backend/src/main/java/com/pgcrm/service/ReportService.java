package com.pgcrm.service;

import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final InvoiceRepository invoiceRepository;
    private final GuestRepository guestRepository;
    private final BedRepository bedRepository;
    private final EbBillRepository ebBillRepository;
    private final AuditLogRepository auditLogRepository;

    /** Monthly revenue breakdown for a year: [{month, rent, eb, food, laundry, total}] */
    public List<Map<String, Object>> getMonthlyRevenueSummary(int year) {
        List<Map<String, Object>> result = new ArrayList<>();
        String[] monthNames = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};

        for (int m = 1; m <= 12; m++) {
            final int month = m;
            List<Object[]> rows = invoiceRepository.getRevenueBreakdown(year, month);

            BigDecimal rent = BigDecimal.ZERO, eb = BigDecimal.ZERO,
                       food = BigDecimal.ZERO, laundry = BigDecimal.ZERO;

            for (Object[] row : rows) {
                // row[0] is InvoiceLineType enum — use toString() to get the name
                String type = row[0] != null ? row[0].toString() : "";
                BigDecimal amount = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
                switch (type) {
                    case "RENT"    -> rent    = amount;
                    case "EB"      -> eb      = amount;
                    case "FOOD"    -> food    = amount;
                    case "LAUNDRY" -> laundry = amount;
                }
            }

            BigDecimal total = rent.add(eb).add(food).add(laundry);
            result.add(Map.of(
                "month",   monthNames[m-1],
                "monthNum", m,
                "rent",    rent,
                "eb",      eb,
                "food",    food,
                "laundry", laundry,
                "total",   total
            ));
        }
        return result;
    }

    /** Occupancy trend: [{month, totalBeds, occupiedBeds, occupancyPct}] */
    public List<Map<String, Object>> getOccupancyReport(int year) {
        String[] monthNames = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        long totalBeds = bedRepository.countTotal();
        List<Map<String, Object>> result = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            long invoiced = invoiceRepository.countDistinctGuestsByMonthYear(year, m);
            double pct = totalBeds > 0 ? (invoiced * 100.0 / totalBeds) : 0;
            result.add(Map.of(
                "month",        monthNames[m-1],
                "monthNum",     m,
                "totalBeds",    totalBeds,
                "occupiedBeds", invoiced,
                "occupancyPct", Math.min(100, Math.round(pct))
            ));
        }
        return result;
    }

    /** Guest turnover: [{month, checkIns, checkOuts}] */
    public List<Map<String, Object>> getGuestTurnoverReport(int year) {
        String[] monthNames = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        List<Map<String, Object>> result = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            long checkIns  = guestRepository.countCheckInsByMonthYear(year, m);
            long checkOuts = guestRepository.countCheckOutsByMonthYear(year, m);
            result.add(Map.of("month", monthNames[m-1], "monthNum", m,
                              "checkIns", checkIns, "checkOuts", checkOuts));
        }
        return result;
    }

    /** Payment status summary: {generated, paid, overdue, totalRevenue} */
    public Map<String, Object> getPaymentSummary(int year) {
        long generated = invoiceRepository.countByYearAndStatus(year, InvoiceStatus.GENERATED);
        long paid      = invoiceRepository.countByYearAndStatus(year, InvoiceStatus.PAID);
        long overdue   = invoiceRepository.countByYearAndStatus(year, InvoiceStatus.OVERDUE);
        BigDecimal totalRevenue = invoiceRepository.sumPaidAmountByYear(year);

        return Map.of(
            "generated",     generated,
            "paid",          paid,
            "overdue",       overdue,
            "totalRevenue",  totalRevenue != null ? totalRevenue : BigDecimal.ZERO
        );
    }
}
