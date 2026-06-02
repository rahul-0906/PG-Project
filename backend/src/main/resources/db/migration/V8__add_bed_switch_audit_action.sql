-- Update check constraint on audit_logs to include GUEST_BED_SWITCH
ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_action_check;

ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check CHECK (
    action IN (
        'GUEST_CHECKIN',
        'GUEST_CHECKOUT_NOTICE',
        'GUEST_CHECKOUT_CONFIRMED',
        'GUEST_BED_SWITCH',
        'INVOICE_GENERATED',
        'PAYMENT_RECEIVED',
        'PAYMENT_REMINDER_SENT',
        'EB_BILL_RECORDED',
        'MAINTENANCE_CREATED',
        'MAINTENANCE_RESOLVED',
        'BED_ADDED',
        'BED_REMOVED',
        'BUILDING_CREATED',
        'FLOOR_CREATED',
        'BLOCK_CREATED',
        'ROOM_CREATED',
        'TENANT_CONFIG_UPDATED',
        'MANAGER_CREATED',
        'TENANT_CREATED',
        'PASSWORD_CHANGED',
        'USER_DEACTIVATED'
    )
);
