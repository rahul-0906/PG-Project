package com.pgcrm.entity.enums;

/**
 * Enumeration of auditable business actions recorded in the {@link com.pgcrm.entity.AuditLog}.
 *
 * <p>Every constant in this enum corresponds to a distinct business event that the
 * {@code AuditService} persists as an immutable {@link com.pgcrm.entity.AuditLog} entry.
 * The enum is used both as the persistence value (via {@code @Enumerated(EnumType.STRING)})
 * and as the primary filter criterion in the Owner's audit dashboard.</p>
 *
 * <p>Constants are grouped by functional domain to aid readability and future extension.
 * When adding a new constant, place it in the appropriate group and update the
 * corresponding audit log description template in {@code AuditService}.</p>
 */
public enum AuditAction {

    // ── Guest Lifecycle ────────────────────────────────────────────────────────

    /** A new guest has been checked in and assigned a bed. */
    GUEST_CHECKIN,

    /** A formal checkout notice has been issued for a guest. */
    GUEST_CHECKOUT_NOTICE,

    /** A guest's checkout has been fully confirmed and their bed vacated. */
    GUEST_CHECKOUT_CONFIRMED,

    /** A guest's bed assignment has been changed to a different bed or room. */
    GUEST_BED_SWITCH,

    // ── Billing & Payments ─────────────────────────────────────────────────────

    /** A monthly invoice has been generated for a guest. */
    INVOICE_GENERATED,

    /** A payment for an invoice has been received and confirmed. */
    PAYMENT_RECEIVED,

    /** A payment overdue reminder has been dispatched to a guest. */
    PAYMENT_REMINDER_SENT,

    /** An electricity bill has been recorded by a manager for a block. */
    EB_BILL_RECORDED,

    // ── Maintenance ────────────────────────────────────────────────────────────

    /** A new maintenance ticket has been created. */
    MAINTENANCE_CREATED,

    /** A maintenance ticket has been marked as resolved. */
    MAINTENANCE_RESOLVED,

    // ── Inventory ─────────────────────────────────────────────────────────────

    /** A new bed has been added to a room. */
    BED_ADDED,

    /** A bed has been removed from a room. */
    BED_REMOVED,

    /** A new building has been registered in the system. */
    BUILDING_CREATED,

    /** A new floor has been added to a building. */
    FLOOR_CREATED,

    /** A new block has been added to a floor. */
    BLOCK_CREATED,

    /** A new room has been added to a floor or block. */
    ROOM_CREATED,

    // ── Configuration ──────────────────────────────────────────────────────────

    /** A building's operational configuration has been updated. */
    TENANT_CONFIG_UPDATED,

    /** A new PG Manager account has been created. */
    MANAGER_CREATED,

    /** A new PG Owner (tenant) account has been provisioned. */
    TENANT_CREATED,

    // ── Authentication ─────────────────────────────────────────────────────────

    /** A user has successfully changed their account password. */
    PASSWORD_CHANGED,

    /** A user account has been deactivated by an administrator. */
    USER_DEACTIVATED
}
