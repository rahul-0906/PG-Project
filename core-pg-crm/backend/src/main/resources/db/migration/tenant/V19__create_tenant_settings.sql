-- ============================================================
--  PG CRM — Tenant Settings Migration (V19)
--  Per-tenant configuration & encrypted secrets.
-- ============================================================

CREATE TABLE IF NOT EXISTS tenant_settings (
    id VARCHAR(36) PRIMARY KEY,
    branding_name VARCHAR(255) NOT NULL DEFAULT 'PG CRM',
    branding_short_name VARCHAR(50) NOT NULL DEFAULT 'PG',
    branding_primary_color VARCHAR(50) NOT NULL DEFAULT '#2563eb',
    branding_logo_url VARCHAR(500),
    razorpay_key_id VARCHAR(255),
    razorpay_key_secret VARCHAR(500),
    razorpay_webhook_secret VARCHAR(500),
    whatsapp_phone_number_id VARCHAR(255),
    whatsapp_access_token VARCHAR(1000),
    whatsapp_verify_token VARCHAR(500),
    notification_email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    notification_whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
