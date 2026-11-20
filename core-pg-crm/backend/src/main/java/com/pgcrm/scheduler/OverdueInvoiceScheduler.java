package com.pgcrm.scheduler;

import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.Guest;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.multitenancy.TenantJobRunner;
import com.pgcrm.repository.InvoiceRepository;
import com.pgcrm.service.AuditService;
import com.pgcrm.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that transitions unpaid invoices to {@link InvoiceStatus#OVERDUE}
 * once their {@code dueDate} has passed.
 *
 * <p>Runs daily at midnight across all active tenants in isolation.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OverdueInvoiceScheduler {

    private final InvoiceRepository   invoiceRepository;
    private final NotificationService notificationService;
    private final AuditService        auditService;
    private final TenantJobRunner     tenantJobRunner;

    /**
     * Marks all past-due unpaid invoices as {@link InvoiceStatus#OVERDUE},
     * notifies the guest in-app, and writes an audit log entry.
     *
     * <p>Runs at {@code 0 0 0 * * *} (midnight every day).</p>
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void markOverdueInvoices() {
        final LocalDate today = LocalDate.now();

        log.info("=== Overdue Invoice Scheduler START — Today: {} ===", today);

        tenantJobRunner.runForActiveTenants("OverdueInvoices", tenant -> {
            processTenantOverdueInvoices(today);
        });

        log.info("=== Overdue Invoice Scheduler COMPLETE ===");
    }

    /**
     * Processes overdue invoice transitions for the currently set tenant.
     * Idempotent: invoices already in OVERDUE status are excluded by the query.
     */
    public void processTenantOverdueInvoices(LocalDate today) {
        final List<Invoice> eligible = invoiceRepository.findOverdueEligibleInvoices(today);
        log.info("=== Checking overdue eligible invoices: {} found ===", eligible.size());

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

        log.info("Overdue transitions completed — Marked: {}, Failed: {}", marked, failed);
    }
}
