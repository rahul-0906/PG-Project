package com.pgcrm.entity.enums;

public enum AuditAction {
    // Guest lifecycle
    GUEST_CHECKIN,
    GUEST_CHECKOUT_NOTICE,
    GUEST_CHECKOUT_CONFIRMED,

    // Billing & Payments
    INVOICE_GENERATED,
    PAYMENT_RECEIVED,
    PAYMENT_REMINDER_SENT,
    EB_BILL_RECORDED,

    // Maintenance
    MAINTENANCE_CREATED,
    MAINTENANCE_RESOLVED,

    // Inventory
    BED_ADDED,
    BED_REMOVED,
    BUILDING_CREATED,
    FLOOR_CREATED,
    BLOCK_CREATED,
    ROOM_CREATED,

    // Configuration
    TENANT_CONFIG_UPDATED,
    MANAGER_CREATED,
    TENANT_CREATED,

    // Auth
    PASSWORD_CHANGED,
    USER_DEACTIVATED
}
