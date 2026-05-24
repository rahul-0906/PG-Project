package com.pgcrm.service;

import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Invoice;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from:noreply@pgcrm.com}")
    private String fromAddress;

    @Value("${app.mail.from-name:PG CRM}")
    private String fromName;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    /**
     * Send welcome email to a newly checked-in guest with their temp password.
     */
    public void sendGuestWelcomeEmail(Guest guest, String tempPassword) {
        if (!mailEnabled) {
            log.info("📧 [MAIL DISABLED] Welcome email for {} — temp password: {}", guest.getEmail(), tempPassword);
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("guestName", guest.getFullName());
            ctx.setVariable("email", guest.getEmail());
            ctx.setVariable("tempPassword", tempPassword);
            ctx.setVariable("pgName", "PG CRM");
            ctx.setVariable("loginUrl", "http://localhost:5173/login");

            String html = templateEngine.process("welcome-email", ctx);
            sendHtmlMail(guest.getEmail(), "🏠 Welcome to PG CRM — Your Login Details", html);
            log.info("📧 Welcome email sent to {}", guest.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", guest.getEmail(), e.getMessage());
        }
    }

    /**
     * Send payment reminder email for an overdue/upcoming invoice.
     */
    public void sendPaymentReminderEmail(Guest guest, Invoice invoice) {
        if (!mailEnabled) {
            log.info("📧 [MAIL DISABLED] Payment reminder for {} — Invoice #{}", guest.getEmail(), invoice.getId());
            return;
        }
        try {
            String monthName = Month.of(invoice.getMonth())
                    .getDisplayName(TextStyle.FULL, Locale.ENGLISH);

            Context ctx = new Context();
            ctx.setVariable("guestName", guest.getFullName());
            ctx.setVariable("month", monthName + " " + invoice.getYear());
            ctx.setVariable("totalAmount", invoice.getTotalAmount());
            ctx.setVariable("dueDate", invoice.getDueDate());
            ctx.setVariable("loginUrl", "http://localhost:5173/guest/invoices");

            String html = templateEngine.process("payment-reminder", ctx);
            sendHtmlMail(guest.getEmail(),
                    "⚠️ Payment Reminder — " + monthName + " Invoice Due", html);
            log.info("📧 Payment reminder sent to {}", guest.getEmail());
        } catch (Exception e) {
            log.error("Failed to send payment reminder to {}: {}", guest.getEmail(), e.getMessage());
        }
    }

    private void sendHtmlMail(String to, String subject, String htmlBody) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress, fromName);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(message);
    }
}
