package com.pgcrm.service;

import com.pgcrm.config.TwilioConfig;
import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Notification;
import com.pgcrm.entity.User;
import com.pgcrm.entity.enums.NotificationChannel;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.repository.NotificationRepository;
import com.pgcrm.repository.UserRepository;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service responsible for dispatching notifications to guests and managers across
 * supported channels: {@link NotificationChannel#IN_APP} and {@link NotificationChannel#WHATSAPP}.
 *
 * <p><strong>Channels:</strong></p>
 * <ul>
 *   <li><strong>IN_APP:</strong> A {@link Notification} record is persisted to the database
 *       and surfaced in the portal's notification inbox. Always succeeds if the database is
 *       available.</li>
 *   <li><strong>WHATSAPP:</strong> A message is sent via the Twilio WhatsApp API. A
 *       {@link Notification} record is always persisted regardless of dispatch outcome,
 *       with {@code deliveryStatus} set to {@code "SENT"}, {@code "FAILED"}, or
 *       {@code "SKIPPED_NO_CONFIG"}.</li>
 * </ul>
 *
 * <p><strong>Manager Alerting:</strong> The {@link #alertManager(String, String)} method
 * notifies all managers assigned to the specified building <em>and</em> all owners.
 * Managers receive both an in-app notification and a WhatsApp message (if Twilio is enabled
 * and the manager has a registered phone number). Owners receive in-app notifications only.</p>
 *
 * <p><strong>Phone Normalisation:</strong> When sending WhatsApp messages to managers,
 * the method prepends {@code "+91"} if the stored phone number does not already start
 * with a {@code "+"} — appropriate for the primary Indian market. Guest WhatsApp numbers
 * are assumed to already be in international format.</p>
 *
 * @see Notification
 * @see TwilioConfig
 * @see NotificationRepository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TwilioConfig           twilioConfig;
    private final UserRepository         userRepository;

    /**
     * Sends an alert to all managers assigned to the specified building and all owners.
     *
     * <p>For each manager:</p>
     * <ul>
     *   <li>An {@link NotificationChannel#IN_APP} notification is persisted.</li>
     *   <li>If Twilio is enabled and the manager has a phone number, a WhatsApp message
     *       is dispatched. Indian numbers without a leading {@code "+"} are automatically
     *       prefixed with {@code "+91"}.</li>
     * </ul>
     *
     * <p>For each owner:</p>
     * <ul>
     *   <li>An {@link NotificationChannel#IN_APP} notification is persisted only.</li>
     * </ul>
     *
     * <p>WhatsApp delivery failures are caught and logged at {@code ERROR} level.
     * They do not interrupt the remaining manager notifications.</p>
     *
     * @param buildingId  the UUID of the building whose assigned managers should be alerted.
     * @param messageText the alert message body.
     */
    @Transactional
    public void alertManager(final String buildingId, final String messageText) {
        log.info("ALERTING MANAGER for building {}: {}", buildingId, messageText);

        // ── Notify assigned managers ──────────────────────────────────────────
        final List<User> managers = userRepository.findByRoleAndBranchId(Role.PG_MANAGER, buildingId);
        for (final User manager : managers) {
            notificationRepository.save(Notification.builder()
                    .user(manager)
                    .channel(NotificationChannel.IN_APP)
                    .message(messageText)
                    .deliveryStatus("DELIVERED")
                    .build());

            if (twilioConfig.isEnabled() && manager.getPhone() != null && !manager.getPhone().isBlank()) {
                try {
                    final String cleanPhone = manager.getPhone().trim();
                    final String toNumber   = "whatsapp:" + (cleanPhone.startsWith("+") ? cleanPhone : "+91" + cleanPhone);
                    Message.creator(
                            new PhoneNumber(toNumber),
                            new PhoneNumber(twilioConfig.getWhatsappFrom()),
                            messageText
                    ).create();
                    log.info("WhatsApp alert successfully sent to Manager {} ({})", manager.getFullName(), cleanPhone);
                } catch (Exception e) {
                    log.error("Failed to send WhatsApp alert to Manager {}: {}", manager.getFullName(), e.getMessage());
                }
            } else {
                log.info("Manager alert skipped (no WhatsApp phone or Twilio disabled) for Manager: {}",
                        manager.getFullName());
            }
        }

        // ── Notify all owners (in-app only) ───────────────────────────────────
        final List<User> owners = userRepository.findByRole(Role.PG_OWNER);
        for (final User owner : owners) {
            notificationRepository.save(Notification.builder()
                    .user(owner)
                    .channel(NotificationChannel.IN_APP)
                    .message(messageText)
                    .deliveryStatus("DELIVERED")
                    .build());
        }
    }

    /**
     * Sends an in-app notification to a guest.
     *
     * <p>Persists a {@link Notification} record with {@link NotificationChannel#IN_APP}
     * linked to both the {@link Guest} and their associated {@link User} account,
     * ensuring it is visible in both the guest and user notification inboxes.</p>
     *
     * @param guest       the {@link Guest} to notify.
     * @param messageText the notification message body.
     */
    @Transactional
    public void sendInApp(final Guest guest, final String messageText) {
        notificationRepository.save(Notification.builder()
                .guest(guest)
                .user(guest.getUser())
                .channel(NotificationChannel.IN_APP)
                .message(messageText)
                .deliveryStatus("DELIVERED")
                .build());
    }

    /**
     * Sends a WhatsApp message to a guest and records the delivery outcome.
     *
     * <p>A {@link Notification} record is always persisted, regardless of whether the
     * WhatsApp dispatch succeeds. The {@code deliveryStatus} field reflects the outcome:</p>
     * <ul>
     *   <li>{@code "SENT"} — Twilio API call succeeded.</li>
     *   <li>{@code "FAILED"} — Twilio API call threw an exception.</li>
     *   <li>{@code "SKIPPED_NO_CONFIG"} — Twilio is disabled or the guest has no WhatsApp number.</li>
     * </ul>
     *
     * @param guest       the {@link Guest} to send the WhatsApp message to.
     * @param messageText the message body.
     */
    @Transactional
    public void sendWhatsApp(final Guest guest, final String messageText) {
        final Notification notification = Notification.builder()
                .guest(guest)
                .user(guest.getUser())
                .channel(NotificationChannel.WHATSAPP)
                .message(messageText)
                .build();

        if (twilioConfig.isEnabled() && guest.getWhatsappNumber() != null) {
            try {
                final String toNumber = "whatsapp:" + guest.getWhatsappNumber();
                Message.creator(
                        new PhoneNumber(toNumber),
                        new PhoneNumber(twilioConfig.getWhatsappFrom()),
                        messageText
                ).create();
                notification.setDeliveryStatus("SENT");
                log.info("WhatsApp sent to {} for guest {}", guest.getWhatsappNumber(), guest.getId());
            } catch (Exception e) {
                notification.setDeliveryStatus("FAILED");
                log.error("WhatsApp send failed for guest {}: {}", guest.getId(), e.getMessage());
            }
        } else {
            notification.setDeliveryStatus("SKIPPED_NO_CONFIG");
            log.info("WhatsApp skipped (Twilio disabled or no number) for guest {}", guest.getId());
        }
        notificationRepository.save(notification);
    }

    /**
     * Convenience method that sends both an in-app notification and a WhatsApp message
     * to a guest in a single call.
     *
     * <p>Internally delegates to {@link #sendInApp(Guest, String)} and
     * {@link #sendWhatsApp(Guest, String)} sequentially. Each dispatch is independent —
     * a failure in the WhatsApp send does not affect the in-app record.</p>
     *
     * @param guest   the {@link Guest} to notify.
     * @param message the notification message body.
     */
    @Transactional
    public void sendBoth(final Guest guest, final String message) {
        sendInApp(guest, message);
        sendWhatsApp(guest, message);
    }
}
