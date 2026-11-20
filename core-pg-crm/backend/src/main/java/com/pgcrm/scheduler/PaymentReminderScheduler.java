package com.pgcrm.scheduler;

import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.multitenancy.TenantJobRunner;
import com.pgcrm.repository.InvoiceRepository;
import com.pgcrm.service.AuditService;
import com.pgcrm.service.EmailService;
import com.pgcrm.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs daily at 9 AM.
 * Iterates through all active tenants in isolation.
 * Finds all unpaid invoices where due date is within 5 days and no reminder has been sent.
 * Sends email + WhatsApp reminder and stamps reminderSentAt.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReminderScheduler {

    private final InvoiceRepository invoiceRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final TenantJobRunner tenantJobRunner;

    @Scheduled(cron = "0 0 9 * * *")  // Every day at 9:00 AM
    public void sendPaymentReminders() {
        LocalDate cutoff = LocalDate.now().plusDays(5);

        log.info("=== Payment Reminder Cron START — Cutoff: {} ===", cutoff);

        tenantJobRunner.runForActiveTenants("PaymentReminders", tenant -> {
            processTenantPaymentReminders(cutoff);
        });

        log.info("=== Payment Reminder Cron COMPLETE ===");
    }

    /**
     * Processes payment reminders for unpaid invoices in the currently set tenant.
     * Idempotent: invoices with reminderSentAt already populated are ignored by the query.
     */
    public void processTenantPaymentReminders(LocalDate cutoff) {
        List<Invoice> dueInvoices = invoiceRepository.findUnpaidInvoicesDueBy(cutoff);
        log.info("💳 Payment reminder check — {} invoices eligible", dueInvoices.size());

        for (Invoice invoice : dueInvoices) {
            try {
                Guest guest = invoice.getGuest();
                if (guest == null) continue;

                // Send email reminder
                emailService.sendPaymentReminderEmail(guest, invoice);

                // Send WhatsApp reminder via NotificationService
                String msg = String.format(
                    "⚠️ Payment Reminder: Dear %s, your invoice of ₹%s for %d/%d is due on %s. " +
                    "Please pay to avoid any issues. Login: http://localhost:5173",
                    guest.getFullName(), invoice.getTotalAmount(),
                    invoice.getMonth(), invoice.getYear(), invoice.getDueDate());
                notificationService.sendWhatsApp(guest, msg);

                // Stamp reminder sent
                invoice.setReminderSentAt(LocalDateTime.now());
                invoiceRepository.save(invoice);

                // Audit log
                auditService.log(AuditAction.PAYMENT_REMINDER_SENT, "Invoice", invoice.getId(),
                    "Payment reminder sent to " + guest.getEmail());

                log.info("📧 Reminder sent to {} for invoice {}", guest.getEmail(), invoice.getId());
            } catch (Exception e) {
                log.error("Failed to send reminder for invoice {}: {}", invoice.getId(), e.getMessage());
            }
        }
    }
}
