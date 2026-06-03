package com.pgcrm.repository;

import com.pgcrm.entity.Notification;
import com.pgcrm.entity.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@link Notification} entity.
 *
 * <p>Provides query and count operations against the {@code notifications} table
 * to support the in-app notification inbox, the unread badge counter, and the
 * notification dispatch audit trail for both guest and manager recipients.</p>
 *
 * <p><strong>Recipient Distinction:</strong> The {@link Notification} entity supports
 * two mutually exclusive recipient types — {@code guest} (for portal guests) and
 * {@code user} (for manager/owner accounts). The repository provides parallel
 * method sets for both recipient types to keep queries type-safe and avoid ambiguous
 * multi-recipient lookups.</p>
 *
 * <p><strong>Channel Support:</strong> {@link NotificationChannel#IN_APP} notifications
 * are persisted here and displayed in the portal inbox. {@link NotificationChannel#WHATSAPP}
 * notifications are also recorded here for delivery tracking and audit purposes,
 * with their {@code deliveryStatus} updated asynchronously via Twilio webhook callbacks.</p>
 *
 * @see Notification
 * @see NotificationChannel
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    // ── Guest Notifications ───────────────────────────────────────────────────

    /**
     * Returns all unread notifications for a specific guest.
     *
     * <p>Used by the guest portal's notification bell/inbox to display the list of
     * unread notifications. The frontend marks them as read after the guest views them,
     * which updates the {@link Notification#isRead()} flag via the notification service.</p>
     *
     * @param guestId the UUID of the {@link com.pgcrm.entity.Guest} recipient.
     * @return a {@link List} of unread {@link Notification} records for the guest;
     *         empty list if all notifications are read or no notifications exist.
     */
    List<Notification> findByGuestIdAndReadFalse(String guestId);

    /**
     * Returns all notifications for a specific guest, ordered by most recent first.
     *
     * <p>Used by the guest portal's full notification history view, displaying all
     * notifications including read ones for audit and reference purposes.</p>
     *
     * @param guestId the UUID of the {@link com.pgcrm.entity.Guest} recipient.
     * @return a {@link List} of all {@link Notification} records for the guest,
     *         ordered by {@code sentAt DESC}; empty list if none exist.
     */
    List<Notification> findByGuestIdOrderBySentAtDesc(String guestId);

    /**
     * Returns all notifications for a specific guest delivered via a specific channel.
     *
     * <p>Used for per-channel delivery auditing (e.g., retrieve only WhatsApp
     * notifications for a guest to check delivery status) and by the notification
     * service to avoid sending duplicate channel-specific notifications.</p>
     *
     * @param guestId the UUID of the {@link com.pgcrm.entity.Guest} recipient.
     * @param channel the {@link NotificationChannel} to filter by.
     * @return a {@link List} of {@link Notification} records for the guest on the
     *         specified channel; empty list if none exist.
     */
    List<Notification> findByGuestIdAndChannel(String guestId, NotificationChannel channel);

    /**
     * Returns the count of unread notifications for a specific guest.
     *
     * <p>Used by the guest portal to render the notification badge counter
     * (the red bubble showing the unread count) on the navigation bar icon,
     * without loading full notification records.</p>
     *
     * @param guestId the UUID of the {@link com.pgcrm.entity.Guest} recipient.
     * @return the number of unread notifications for the guest.
     */
    long countByGuestIdAndReadFalse(String guestId);

    // ── Manager / User Notifications ──────────────────────────────────────────

    /**
     * Returns all notifications for a specific user (manager or owner), ordered by
     * most recent first.
     *
     * <p>Used by the manager portal's notification history view. Targets the
     * {@link com.pgcrm.entity.User} recipient field, distinct from the guest
     * recipient field, since managers are not modelled as guests in the system.</p>
     *
     * @param userId the UUID of the {@link com.pgcrm.entity.User} (manager/owner) recipient.
     * @return a {@link List} of all {@link Notification} records for the user,
     *         ordered by {@code sentAt DESC}; empty list if none exist.
     */
    List<Notification> findByUserIdOrderBySentAtDesc(String userId);

    /**
     * Returns all unread notifications for a specific user (manager or owner).
     *
     * <p>Used by the manager portal's notification inbox to display only unread
     * alerts (e.g., new maintenance ticket raised, overdue payment flagged).</p>
     *
     * @param userId the UUID of the {@link com.pgcrm.entity.User} (manager/owner) recipient.
     * @return a {@link List} of unread {@link Notification} records for the user;
     *         empty list if all notifications are read or none exist.
     */
    List<Notification> findByUserIdAndReadFalse(String userId);

    /**
     * Returns the count of unread notifications for a specific user (manager or owner).
     *
     * <p>Used by the manager portal to render the notification badge counter
     * on the navigation bar without loading full notification records.</p>
     *
     * @param userId the UUID of the {@link com.pgcrm.entity.User} (manager/owner) recipient.
     * @return the number of unread notifications for the user.
     */
    long countByUserIdAndReadFalse(String userId);
}
