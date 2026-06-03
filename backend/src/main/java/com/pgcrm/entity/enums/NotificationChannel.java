package com.pgcrm.entity.enums;

/**
 * Enumeration of communication channels used to deliver a {@link com.pgcrm.entity.Notification}.
 *
 * <p>The channel is selected by the {@code NotificationService} based on the type of
 * event and the recipient's communication preferences. Each channel has distinct
 * delivery semantics and tracking capabilities:</p>
 * <ul>
 *   <li>{@link #IN_APP} — Synchronous; the notification is immediately visible in the
 *       portal inbox without any external API dependency. Tracked via the
 *       {@link com.pgcrm.entity.Notification#isRead()} flag.</li>
 *   <li>{@link #WHATSAPP} — Asynchronous; the message is dispatched via the Twilio
 *       WhatsApp API. Delivery status is tracked via the
 *       {@link com.pgcrm.entity.Notification#getDeliveryStatus()} field, updated by
 *       incoming Twilio webhook callbacks.</li>
 * </ul>
 */
public enum NotificationChannel {

    /**
     * Notification delivered to the user's in-app inbox within the PG CRM portal.
     * Always persisted to the database; no external API dependency.
     * Guests and managers can view and dismiss these from their dashboard.
     */
    IN_APP,

    /**
     * Notification dispatched as a WhatsApp message via the Twilio API to the
     * recipient's registered WhatsApp number ({@link com.pgcrm.entity.Guest#getWhatsappNumber()}).
     * Used for time-sensitive alerts such as invoice reminders and checkout notices.
     */
    WHATSAPP
}
