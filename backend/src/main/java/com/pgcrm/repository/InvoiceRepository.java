package com.pgcrm.repository;

import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {

    List<Invoice> findByGuestId(String guestId);
    Optional<Invoice> findByGuestIdAndMonthAndYear(String guestId, int month, int year);
    List<Invoice> findByMonthAndYear(int month, int year);
    List<Invoice> findByStatus(InvoiceStatus status);

    // ── Reports ───────────────────────────────────────────────────

    /** Revenue breakdown by line item type for a given month/year */
    @Query("""
        SELECT li.type, SUM(li.amount)
        FROM InvoiceLineItem li
        WHERE li.invoice.year  = :year
          AND li.invoice.month = :month
          AND li.invoice.status IN ('GENERATED','PAID')
        GROUP BY li.type
        """)
    List<Object[]> getRevenueBreakdown(
        @Param("year")     int year,
        @Param("month")    int month);

    /** Count of distinct guests billed in a given month */
    @Query("""
        SELECT COUNT(DISTINCT i.guest.id) FROM Invoice i
        WHERE i.year = :year AND i.month = :month
        """)
    long countDistinctGuestsByMonthYear(
        @Param("year") int year, @Param("month") int month);

    /** Count invoices by status in a given year */
    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.year = :year AND i.status = :status")
    long countByYearAndStatus(
        @Param("year") int year,
        @Param("status") InvoiceStatus status);

    /** Total paid revenue for a year */
    @Query("SELECT SUM(i.totalAmount) FROM Invoice i WHERE i.year = :year AND i.status = 'PAID'")
    BigDecimal sumPaidAmountByYear(
        @Param("year") int year);

    // ── Payment Reminder Scheduler ────────────────────────────────

    /** Find unpaid invoices past due date with no reminder sent yet */
    @Query("""
        SELECT i FROM Invoice i
        WHERE i.status = 'GENERATED'
          AND i.dueDate IS NOT NULL
          AND i.reminderSentAt IS NULL
          AND i.dueDate <= :cutoff
        """)
    List<Invoice> findUnpaidInvoicesDueBy(@Param("cutoff") java.time.LocalDate cutoff);
}
