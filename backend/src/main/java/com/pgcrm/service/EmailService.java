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
            ctx.setVariable("pgName", fromName);
            ctx.setVariable("loginUrl", "http://localhost:5173/login");

            String html = templateEngine.process("welcome-email", ctx);
            sendHtmlMail(guest.getEmail(), "🏠 Welcome to " + fromName + " — Your Login Details", html);
            log.info("📧 Welcome email sent to {}", guest.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", guest.getEmail(), e.getMessage());
        }
    }
 
    /**
     * Send welcome back email to a returning guest.
     */
    public void sendReturningGuestWelcomeEmail(Guest guest, String tempPassword) {
        if (!mailEnabled) {
            log.info("📧 [MAIL DISABLED] Welcome back email for {}", guest.getEmail());
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("guestName", guest.getFullName());
            ctx.setVariable("email", guest.getEmail());
            ctx.setVariable("tempPassword", tempPassword);
            ctx.setVariable("bedLabel", guest.getBed() != null ? guest.getBed().getBedLabel() : "Unassigned");
            ctx.setVariable("loginUrl", "http://localhost:5173/login");

            String html = templateEngine.process("welcome-back-email", ctx);
            sendHtmlMail(guest.getEmail(), "🏠 Welcome Back to " + fromName + " — Check-in Confirmation", html);
            log.info("📧 Welcome back email sent to {}", guest.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome back email to {}: {}", guest.getEmail(), e.getMessage());
        }
    }

    /**
     * Send password reset email to a user with their new temporary password.
     */
    public void sendPasswordResetEmail(com.pgcrm.entity.User user, String tempPassword) {
        if (!mailEnabled) {
            log.info("📧 [MAIL DISABLED] Password reset email for {} — temp password: {}", user.getEmail(), tempPassword);
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("guestName", user.getFullName() != null ? user.getFullName() : "User");
            ctx.setVariable("email", user.getEmail());
            ctx.setVariable("tempPassword", tempPassword);
            ctx.setVariable("loginUrl", "http://localhost:5173/login");

            String html = templateEngine.process("password-reset-email", ctx);
            sendHtmlMail(user.getEmail(), "🔐 Password Reset Request — PG CRM", html);
            log.info("📧 Password reset email sent to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
        }
    }


    /**
     * Send email verification code for email change.
     */
    public void sendEmailVerificationCode(String toEmail, String code, String guestName) {
        if (!mailEnabled) {
            log.info("📧 [MAIL DISABLED] Email verification code for {} — code: {}", toEmail, code);
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("guestName", guestName != null ? guestName : "User");
            ctx.setVariable("email", toEmail);
            ctx.setVariable("code", code);
            ctx.setVariable("appName", fromName);

            String html = templateEngine.process("email-verification", ctx);
            sendHtmlMail(toEmail, "🔑 Confirm Your New Email Address — " + fromName, html);
            log.info("📧 Email verification code sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email verification to {}: {}", toEmail, e.getMessage());
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

    public void sendBedSwitchEmail(Guest guest, String oldBedLabel, String newBedLabel, BigDecimal newRent) {
        if (!mailEnabled) {
            log.info("📧 [MAIL DISABLED] Bed switch email for {} — from {} to {}, new rent: {}", 
                     guest.getEmail(), oldBedLabel, newBedLabel, newRent);
            return;
        }
        try {
            Context ctx = new Context();
            ctx.setVariable("guestName", guest.getFullName());
            ctx.setVariable("oldBedLabel", oldBedLabel);
            ctx.setVariable("newBedLabel", newBedLabel);
            ctx.setVariable("newRent", newRent);
            ctx.setVariable("pgName", fromName);

            String html = templateEngine.process("bed-switch-email", ctx);
            sendHtmlMail(guest.getEmail(), "🔄 Bed Assignment Updated — " + fromName, html);
            log.info("📧 Bed switch email sent to {}", guest.getEmail());
        } catch (Exception e) {
            log.error("Failed to send bed switch email to {}: {}", guest.getEmail(), e.getMessage());
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
