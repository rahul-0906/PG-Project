package com.pgcrm.scheduler;

import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.repository.InvoiceRepository;
import com.pgcrm.service.AuditService;
import com.pgcrm.service.EmailService;
import com.pgcrm.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs daily at 9 AM.
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

    /**
     * The publicly reachable URL for the guest portal. Used in WhatsApp reminder messages.
     * Set {@code APP_FRONTEND_URL} env var in production (e.g. https://app.yourpg.com).
     * BIZ-04: Was previously hardcoded as http://localhost:5173.
     */
    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Scheduled(cron = "0 0 9 * * *")  // Every day at 9:00 AM
    @Transactional
    public void sendPaymentReminders() {
        // Find invoices due within 5 days (already overdue or due soon)
        LocalDate cutoff = LocalDate.now().plusDays(5);
        List<Invoice> dueInvoices = invoiceRepository.findUnpaidInvoicesDueBy(cutoff);

        log.info("💳 Payment reminder check — {} invoices eligible", dueInvoices.size());

        for (Invoice invoice : dueInvoices) {
            try {
                Guest guest = invoice.getGuest();
                if (guest == null) continue;

                // Send email reminder
                emailService.sendPaymentReminderEmail(guest, invoice);

                // Send WhatsApp reminder via NotificationService (handles Twilio internally)
                String msg = String.format(
                    "⚠️ Payment Reminder: Dear %s, your invoice of ₹%s for %d/%d is due on %s. " +
                    "Please pay to avoid any issues. Login: %s",
                    guest.getFullName(), invoice.getTotalAmount(),
                    invoice.getMonth(), invoice.getYear(), invoice.getDueDate(), frontendUrl);
                notificationService.sendWhatsApp(guest, msg);

                // Stamp reminder sent
                invoice.setReminderSentAt(LocalDateTime.now());
                invoiceRepository.save(invoice);

                // Audit log
                auditService.log(AuditAction.PAYMENT_REMINDER_SENT, "Invoice", invoice.getId(),
                    // COMP-05: Use guest ID instead of email to avoid storing PII in audit logs
                    // (guest email may be anonymized after 365 days for GDPR compliance).
                    "Payment reminder sent to guest ID: " + guest.getId());

                log.info("📧 Reminder sent to {} for invoice {}", guest.getEmail(), invoice.getId());
            } catch (Exception e) {
                log.error("Failed to send reminder for invoice {}: {}", invoice.getId(), e.getMessage());
            }
        }
    }
}
