package com.pgcrm.service;

import com.pgcrm.config.MetaWhatsAppConfig;
import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Notification;
import com.pgcrm.entity.User;
import com.pgcrm.entity.enums.NotificationChannel;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.repository.NotificationRepository;
import com.pgcrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for dispatching notifications to guests and managers across
 * supported channels: {@link NotificationChannel#IN_APP} and {@link NotificationChannel#WHATSAPP}.
 *
 * <p><strong>Channels:</strong></p>
 * <ul>
 *   <li><strong>IN_APP:</strong> A {@link Notification} record is persisted to the database
 *       and surfaced in the portal's notification inbox. Always succeeds if the database is
 *       available.</li>
 *   <li><strong>WHATSAPP:</strong> A message is sent via the Meta WhatsApp Cloud API. A
 *       {@link Notification} record is always persisted regardless of dispatch outcome,
 *       with {@code deliveryStatus} set to {@code "SENT"}, {@code "FAILED"}, or
 *       {@code "SKIPPED_NO_CONFIG"}.</li>
 * </ul>
 *
 * <p><strong>Manager Alerting:</strong> The {@link #alertManager(String, String)} method
 * notifies all managers assigned to the specified building <em>and</em> all owners.
 * Managers receive both an in-app notification and a WhatsApp message (if Meta API is enabled
 * and the manager has a registered phone number). Owners receive in-app notifications only.</p>
 *
 * <p><strong>Phone Normalisation:</strong> When sending WhatsApp messages to managers or guests,
 * the method cleans all non-digit characters and prepends {@code "91"} if the phone number is a
 * 10-digit number (standard Indian phone format).</p>
 *
 * @see Notification
 * @see MetaWhatsAppConfig
 * @see NotificationRepository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MetaWhatsAppConfig     metaWhatsAppConfig;
    private final UserRepository         userRepository;
    private final RestTemplate           restTemplate = new RestTemplate();

    /**
     * Sends an alert to all managers assigned to the specified building and all owners.
     *
     * <p>For each manager:</p>
     * <ul>
     *   <li>An {@link NotificationChannel#IN_APP} notification is persisted.</li>
     *   <li>If Meta API is enabled and the manager has a phone number, a WhatsApp message
     *       is dispatched. Indian numbers are normalized automatically.</li>
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

            if (metaWhatsAppConfig.isEnabled() && manager.getPhone() != null && !manager.getPhone().isBlank()) {
                sendWhatsAppDirect(manager.getPhone(), messageText, manager.getFullName());
            } else {
                log.info("Manager alert skipped (no WhatsApp phone or Meta disabled) for Manager: {}",
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
     *   <li>{@code "SENT"} — Meta Graph API call succeeded.</li>
     *   <li>{@code "FAILED"} — Meta Graph API call threw an exception or returned an error status.</li>
     *   <li>{@code "SKIPPED_NO_CONFIG"} — Meta configuration is disabled or the guest has no WhatsApp number.</li>
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

        if (metaWhatsAppConfig.isEnabled() && guest.getWhatsappNumber() != null && !guest.getWhatsappNumber().isBlank()) {
            boolean success = sendWhatsAppDirect(guest.getWhatsappNumber(), messageText, guest.getFullName());
            notification.setDeliveryStatus(success ? "SENT" : "FAILED");
        } else {
            notification.setDeliveryStatus("SKIPPED_NO_CONFIG");
            log.info("WhatsApp skipped (Meta disabled or no number) for guest {}", guest.getId());
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

    /**
     * Sends WhatsApp message directly to a target phone number using the Meta WhatsApp Cloud API.
     */
    private boolean sendWhatsAppDirect(String rawPhone, String messageText, String recipientName) {
        String cleanPhone = normalizePhoneNumber(rawPhone);
        if (cleanPhone == null || cleanPhone.isBlank()) {
            log.warn("Phone number is empty, skipping WhatsApp dispatch.");
            return false;
        }

        try {
            String url = String.format("https://graph.facebook.com/v19.0/%s/messages", metaWhatsAppConfig.getPhoneNumberId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(metaWhatsAppConfig.getAccessToken());

            Map<String, Object> payload = new HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("recipient_type", "individual");
            payload.put("to", cleanPhone);
            payload.put("type", "text");

            Map<String, Object> textMap = new HashMap<>();
            textMap.put("body", messageText);
            payload.put("text", textMap);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("WhatsApp successfully sent to {} ({}) via Meta Cloud API", recipientName, cleanPhone);
                return true;
            } else {
                log.error("Meta WhatsApp Cloud API returned non-success status: {} for {}", response.getStatusCode(), recipientName);
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}: {}", recipientName, e.getMessage());
            return false;
        }
    }

    /**
     * Normalizes a phone number to standard Meta API format (digits only, prepends 91 if it's a 10-digit number).
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) return null;
        String clean = phone.trim().replaceAll("[^0-9]", "");
        if (clean.length() == 10) {
            clean = "91" + clean;
        }
        return clean;
    }
}
