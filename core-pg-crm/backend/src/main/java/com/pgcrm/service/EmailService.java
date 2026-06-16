package com.pgcrm.service;

import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Invoice;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Service responsible for composing and dispatching all transactional HTML emails
 * in the PG CRM system using Spring JavaMail and Thymeleaf templates.
 *
 * <p>Supports the following email types:</p>
 * <ul>
 *   <li><strong>Guest Welcome:</strong> Sent on first-time check-in with temporary credentials.</li>
 *   <li><strong>Returning Guest Welcome:</strong> Sent when a previously checked-out guest
 *       checks back in, including their updated bed assignment and new credentials.</li>
 *   <li><strong>Password Reset:</strong> Sent to any user who requests a forgotten-password reset.</li>
 *   <li><strong>Email Verification:</strong> Sends a one-time code to the new email address
 *       during an email-change request flow.</li>
 *   <li><strong>Payment Reminder:</strong> Sent to guests with outstanding invoices.</li>
 *   <li><strong>Bed Switch Notification:</strong> Sent when a manager reassigns a guest to a new bed.</li>
 * </ul>
 *
 * <p><strong>Mail-Disabled Mode:</strong> When {@code app.mail.enabled=false} (the default in
 * development), all methods short-circuit and log the email content at {@code INFO} level instead
 * of dispatching. This prevents accidental email delivery in local or staging environments.</p>
 *
 * <p><strong>Failure Isolation:</strong> Each public method wraps its dispatch logic in a
 * {@code try/catch}. Email failures are logged at {@code ERROR} level but are <em>never
 * propagated</em> to the caller. This ensures that a transient SMTP outage does not
 * roll back an otherwise successful business transaction (e.g., guest check-in).</p>
 *
 * @see EmailVerificationService
 * @see JavaMailSender
 * @see TemplateEngine
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    /** The "from" email address used in the {@code From:} header of all outgoing emails. */
    @Value("${app.mail.from:noreply@pgcrm.com}")
    private String fromAddress;

    /** The display name used alongside {@link #fromAddress} in the {@code From:} header. */
    @Value("${app.mail.from-name:PG CRM}")
    private String fromName;

    /**
     * Whether the mail subsystem is enabled. When {@code false}, emails are logged
     * but not dispatched — safe default for development and staging environments.
     */
    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    /**
     * Sends a welcome email to a newly checked-in guest containing their portal login URL
     * and temporary password.
     *
     * <p>Uses the Thymeleaf template {@code welcome-email}. The temporary password
     * is included in plaintext in the email body — guests are forced to change it on
     * first login via the {@code mustChangePassword} flag.</p>
     *
     * @param guest        the newly checked-in {@link Guest}.
     * @param tempPassword the generated temporary plaintext password to include in the email.
     */
    public void sendGuestWelcomeEmail(final Guest guest, final String tempPassword) {
        log.info("Initiating welcome email dispatch to: {}", guest.getEmail());
        if (!mailEnabled) {
            log.info("📧 [MAIL DISABLED] Welcome email for {} — temp password: {}", guest.getEmail(), tempPassword);
            return;
        }
        try {
            final Context ctx = new Context();
            ctx.setVariable("guestName",   guest.getFullName());
            ctx.setVariable("email",       guest.getEmail());
            ctx.setVariable("tempPassword", tempPassword);
            ctx.setVariable("pgName",      fromName);
            ctx.setVariable("loginUrl",    "http://localhost:5173/login");
            
            String assignedBeds = guest.getBeds() != null && !guest.getBeds().isEmpty()
                    ? guest.getBeds().stream()
                            .filter(java.util.Objects::nonNull)
                            .map(b -> b.getBedLabel() != null ? b.getBedLabel() : "Unnamed Bed")
                            .collect(java.util.stream.Collectors.joining(", "))
                    : "Unassigned";
            
            log.info("Populating welcome email context variables. Assigned beds: {}", assignedBeds);
            ctx.setVariable("bedLabel",    assignedBeds);
            ctx.setVariable("assignedBeds", assignedBeds);

            final String html = templateEngine.process("welcome-email", ctx);
            sendHtmlMail(guest.getEmail(), "🏠 Welcome to " + fromName + " — Your Login Details", html);
            log.info("📧 Welcome email sent to {}", guest.getEmail());
        } catch (MessagingException e) {
            log.error("SMTP MessagingException failed to send welcome email to {}: {}", guest.getEmail(), e.getMessage(), e);
        } catch (MailException e) {
            log.error("Spring MailException failed to send welcome email to {}: {}", guest.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", guest.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Sends a welcome-back email to a returning guest who is checking in again after a
     * previous stay, including their updated bed assignment and a new temporary password.
     *
     * <p>Uses the Thymeleaf template {@code welcome-back-email}.</p>
     *
     * @param guest        the returning {@link Guest}.
     * @param tempPassword the newly generated temporary plaintext password.
     */
    public void sendReturningGuestWelcomeEmail(final Guest guest, final String tempPassword) {
        log.info("Initiating welcome back email dispatch to: {}", guest.getEmail());
        if (!mailEnabled) {
            log.info("📧 [MAIL DISABLED] Welcome back email for {}", guest.getEmail());
            return;
        }
        try {
            final Context ctx = new Context();
            ctx.setVariable("guestName",   guest.getFullName());
            ctx.setVariable("email",       guest.getEmail());
            ctx.setVariable("tempPassword", tempPassword);
            
            String assignedBeds = guest.getBeds() != null && !guest.getBeds().isEmpty()
                    ? guest.getBeds().stream()
                            .filter(java.util.Objects::nonNull)
                            .map(b -> b.getBedLabel() != null ? b.getBedLabel() : "Unnamed Bed")
                            .collect(java.util.stream.Collectors.joining(", "))
                    : "Unassigned";
            
            log.info("Populating welcome back email context variables. Assigned beds: {}", assignedBeds);
            ctx.setVariable("bedLabel",    assignedBeds);
            ctx.setVariable("assignedBeds", assignedBeds);
            ctx.setVariable("loginUrl",    "http://localhost:5173/login");

            final String html = templateEngine.process("welcome-back-email", ctx);
            sendHtmlMail(guest.getEmail(), "🏠 Welcome Back to " + fromName + " — Check-in Confirmation", html);
            log.info("📧 Welcome back email sent to {}", guest.getEmail());
        } catch (MessagingException e) {
            log.error("SMTP MessagingException failed to send welcome back email to {}: {}", guest.getEmail(), e.getMessage(), e);
        } catch (MailException e) {
            log.error("Spring MailException failed to send welcome back email to {}: {}", guest.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send welcome back email to {}: {}", guest.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Sends a password-reset email to a user (any role) containing their new temporary password.
     *
     * <p>Uses the Thymeleaf template {@code password-reset-email}. The user is flagged with
     * {@code mustChangePassword = true} before this method is called, so they will be
     * forced to set a new password on next login.</p>
     *
     * @param user         the {@link com.pgcrm.entity.User} who requested the reset.
     * @param tempPassword the newly generated temporary plaintext password.
     */
    public void sendPasswordResetEmail(final com.pgcrm.entity.User user, final String tempPassword) {
        log.info("Initiating password reset email dispatch to: {}", user.getEmail());
        if (!mailEnabled) {
            log.info("📧 [MAIL DISABLED] Password reset email for {} — temp password: {}", user.getEmail(), tempPassword);
            return;
        }
        try {
            final Context ctx = new Context();
            ctx.setVariable("guestName",   user.getFullName() != null ? user.getFullName() : "User");
            ctx.setVariable("email",       user.getEmail());
            ctx.setVariable("tempPassword", tempPassword);
            ctx.setVariable("loginUrl",    "http://localhost:5173/login");

            final String html = templateEngine.process("password-reset-email", ctx);
            sendHtmlMail(user.getEmail(), "🔐 Password Reset Request — PG CRM", html);
            log.info("📧 Password reset email sent to {}", user.getEmail());
        } catch (MessagingException e) {
            log.error("SMTP MessagingException failed to send password reset email to {}: {}", user.getEmail(), e.getMessage(), e);
        } catch (MailException e) {
            log.error("Spring MailException failed to send password reset email to {}: {}", user.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Sends a one-time verification code to the new email address during an email-change flow.
     *
     * <p>The code is generated and stored in {@link EmailVerificationService} before this
     * method is called. Uses the Thymeleaf template {@code email-verification}.</p>
     *
     * @param toEmail   the new email address to send the verification code to.
     * @param code      the one-time verification code to include in the email.
     * @param guestName the display name of the user initiating the email change.
     */
    public void sendEmailVerificationCode(final String toEmail, final String code, final String guestName) {
        log.info("Initiating email verification code dispatch to: {}", toEmail);
        if (!mailEnabled) {
            log.info("📧 [MAIL DISABLED] Email verification code for {} — code: {}", toEmail, code);
            return;
        }
        try {
            final Context ctx = new Context();
            ctx.setVariable("guestName", guestName != null ? guestName : "User");
            ctx.setVariable("email",     toEmail);
            ctx.setVariable("code",      code);
            ctx.setVariable("appName",   fromName);

            final String html = templateEngine.process("email-verification", ctx);
            sendHtmlMail(toEmail, "🔑 Confirm Your New Email Address — " + fromName, html);
            log.info("📧 Email verification code sent to {}", toEmail);
        } catch (MessagingException e) {
            log.error("SMTP MessagingException failed to send email verification to {}: {}", toEmail, e.getMessage(), e);
        } catch (MailException e) {
            log.error("Spring MailException failed to send email verification to {}: {}", toEmail, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send email verification to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    /**
     * Sends a payment reminder email to a guest for an overdue or upcoming invoice.
     *
     * <p>Uses the Thymeleaf template {@code payment-reminder}. The email includes the
     * invoice total, the due date, and a direct link to the guest's invoice portal page.</p>
     *
     * @param guest   the {@link Guest} with an outstanding invoice.
     * @param invoice the {@link Invoice} for which the reminder is being sent.
     */
    public void sendPaymentReminderEmail(final Guest guest, final Invoice invoice) {
        log.info("Initiating payment reminder email dispatch to: {}", guest.getEmail());
        if (!mailEnabled) {
            log.info("📧 [MAIL DISABLED] Payment reminder for {} — Invoice #{}", guest.getEmail(), invoice.getId());
            return;
        }
        try {
            final String monthName = Month.of(invoice.getMonth())
                    .getDisplayName(TextStyle.FULL, Locale.ENGLISH);

            final Context ctx = new Context();
            ctx.setVariable("guestName",   guest.getFullName());
            ctx.setVariable("month",       monthName + " " + invoice.getYear());
            ctx.setVariable("totalAmount", invoice.getTotalAmount());
            ctx.setVariable("dueDate",     invoice.getDueDate());
            ctx.setVariable("loginUrl",    "http://localhost:5173/guest/invoices");

            final String html = templateEngine.process("payment-reminder", ctx);
            sendHtmlMail(guest.getEmail(), "⚠️ Payment Reminder — " + monthName + " Invoice Due", html);
            log.info("📧 Payment reminder sent to {}", guest.getEmail());
        } catch (MessagingException e) {
            log.error("SMTP MessagingException failed to send payment reminder to {}: {}", guest.getEmail(), e.getMessage(), e);
        } catch (MailException e) {
            log.error("Spring MailException failed to send payment reminder to {}: {}", guest.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send payment reminder to {}: {}", guest.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Sends a bed-switch notification email to a guest informing them of their
     * new bed assignment and updated monthly base rent.
     *
     * <p>Uses the Thymeleaf template {@code bed-switch-email}.</p>
     *
     * @param guest       the {@link Guest} whose bed has been changed.
     * @param oldBedLabel the label of the previous bed (e.g., {@code "1A-1"}).
     * @param newBedLabel the label of the newly assigned bed (e.g., {@code "2B-3"}).
     * @param newRent     the new monthly base rent amount applicable from the new bed's room.
     */
    public void sendBedSwitchEmail(final Guest guest, final String oldBedLabel,
                                   final String newBedLabel, final BigDecimal newRent) {
        log.info("Initiating bed switch email dispatch to: {}", guest.getEmail());
        if (!mailEnabled) {
            log.info("📧 [MAIL DISABLED] Bed switch email for {} — from {} to {}, new rent: {}",
                    guest.getEmail(), oldBedLabel, newBedLabel, newRent);
            return;
        }
        try {
            final Context ctx = new Context();
            ctx.setVariable("guestName",   guest.getFullName());
            ctx.setVariable("oldBedLabel", oldBedLabel);
            ctx.setVariable("newBedLabel", newBedLabel);
            ctx.setVariable("newRent",     newRent);
            ctx.setVariable("pgName",      fromName);

            final String html = templateEngine.process("bed-switch-email", ctx);
            sendHtmlMail(guest.getEmail(), "🔄 Bed Assignment Updated — " + fromName, html);
            log.info("📧 Bed switch email sent to {}", guest.getEmail());
        } catch (MessagingException e) {
            log.error("SMTP MessagingException failed to send bed switch email to {}: {}", guest.getEmail(), e.getMessage(), e);
        } catch (MailException e) {
            log.error("Spring MailException failed to send bed switch email to {}: {}", guest.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send bed switch email to {}: {}", guest.getEmail(), e.getMessage(), e);
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Constructs and dispatches a UTF-8 encoded HTML {@link MimeMessage} via the
     * configured {@link JavaMailSender}.
     *
     * @param to       the recipient email address.
     * @param subject  the email subject line.
     * @param htmlBody the fully-rendered HTML email body.
     * @throws Exception if the underlying SMTP transport fails.
     */
    private void sendHtmlMail(final String to, final String subject, final String htmlBody) throws Exception {
        final MimeMessage message = mailSender.createMimeMessage();
        final MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress, fromName);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(message);
    }
}
