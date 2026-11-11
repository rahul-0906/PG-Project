package com.pgcrm.scheduler;

import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.Guest;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.repository.InvoiceRepository;
import com.pgcrm.service.AuditService;
import com.pgcrm.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that transitions unpaid invoices to {@link InvoiceStatus#OVERDUE}
 * once their {@code dueDate} has passed.
 *
 * <p>Runs daily at midnight (just after the day rolls over), immediately after
 * the due date passes. For each newly overdue invoice, the scheduler:</p>
 * <ol>
 *   <li>Sets invoice status to {@link InvoiceStatus#OVERDUE}.</li>
 *   <li>Sends an in-app notification to the guest.</li>
 *   <li>Records an {@link AuditAction#INVOICE_OVERDUE} audit log entry.</li>
 * </ol>
 *
 * <p><strong>Eligibility criteria (via
 * {@link InvoiceRepository#findOverdueEligibleInvoices}):</strong></p>
 * <ul>
 *   <li>Status is {@code GENERATED} or {@code PENDING_CASH_VERIFICATION} —
 *       already {@code PAID} or {@code OVERDUE} invoices are skipped.</li>
 *   <li>{@code dueDate} is non-null and strictly before today.</li>
 * </ul>
 *
 * <p><strong>Failure isolation:</strong> Each invoice is processed inside an
 * individual try/catch so that a single failure does not abort the rest of
 * the batch. Failures are logged at {@code ERROR} level with the invoice ID.</p>
 *
 * <p><strong>Cron:</strong> {@code 0 0 0 * * *} — midnight every day, consistent
 * with {@code MonthlyBillingScheduler}.</p>
 *
 * @see InvoiceRepository#findOverdueEligibleInvoices(LocalDate)
 * @see NotificationService#sendInApp(Guest, String)
 * @see AuditService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OverdueInvoiceScheduler {

    private final InvoiceRepository   invoiceRepository;
    private final NotificationService notificationService;
    private final AuditService        auditService;

    /**
     * Marks all past-due unpaid invoices as {@link InvoiceStatus#OVERDUE},
     * notifies the guest in-app, and writes an audit log entry.
     *
     * <p>Runs at {@code 0 0 0 * * *} (midnight every day).</p>
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markOverdueInvoices() {
        final LocalDate    today    = LocalDate.now();
        final List<Invoice> eligible = invoiceRepository.findOverdueEligibleInvoices(today);

        log.info("=== Overdue Invoice Scheduler START — {} invoices eligible for today {} ===",
                eligible.size(), today);

        int marked = 0, failed = 0;

        for (final Invoice invoice : eligible) {
            try {
                final InvoiceStatus previousStatus = invoice.getStatus();
                invoice.setStatus(InvoiceStatus.OVERDUE);
                invoiceRepository.save(invoice);

                final Guest guest = invoice.getGuest();

                // In-app notification to the guest
                if (guest != null) {
                    final String[] MONTHS = {
                            "January", "February", "March", "April", "May", "June",
                            "July", "August", "September", "October", "November", "December"
                    };
                    final String monthName = MONTHS[invoice.getMonth() - 1];
                    notificationService.sendInApp(guest,
                            String.format(
                                    "⚠️ Your invoice for %s %d (₹%s) is now overdue. " +
                                    "Please pay immediately to avoid further action.",
                                    monthName, invoice.getYear(), invoice.getTotalAmount()));
                }

                // Audit log
                auditService.log(
                        AuditAction.INVOICE_OVERDUE,
                        "Invoice",
                        invoice.getId(),
                        String.format("Invoice transitioned from %s → OVERDUE. Due date was %s. Amount: ₹%s.",
                                previousStatus, invoice.getDueDate(), invoice.getTotalAmount()));

                log.info("✓ Invoice {} marked OVERDUE (was {}) — guest: {}, due: {}, amount: ₹{}",
                        invoice.getId(), previousStatus,
                        guest != null ? guest.getId() : "N/A",
                        invoice.getDueDate(), invoice.getTotalAmount());
                marked++;

            } catch (final Exception e) {
                log.error("✗ Failed to mark invoice {} as OVERDUE: {}", invoice.getId(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("=== Overdue Invoice Scheduler DONE — Marked: {}, Failed: {} ===", marked, failed);
    }
}
