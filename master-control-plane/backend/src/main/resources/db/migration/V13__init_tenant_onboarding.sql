-- ============================================================
--  Control Plane — Tenant Onboarding & Subscriptions (V13)
-- ============================================================

CREATE TABLE tenant_profiles (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    pg_name VARCHAR(255) NOT NULL,
    pg_short_title VARCHAR(255),
    custom_domain VARCHAR(255) UNIQUE,
    router_ip VARCHAR(255),
    whatsapp_number VARCHAR(255),
    contact_email VARCHAR(255),
    razorpay_key VARCHAR(255),
    razorpay_secret VARCHAR(255),
    theme_config JSONB,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_profiles_owner_user FOREIGN KEY (owner_user_id) REFERENCES users(id)
);

CREATE TABLE tenant_subscriptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    plan_type VARCHAR(50) NOT NULL,
    amc_fee DECIMAL(12, 2) NOT NULL,
    payment_status VARCHAR(50) NOT NULL,
    next_billing_date TIMESTAMP,
    CONSTRAINT fk_tenant_subscriptions_tenant_profile FOREIGN KEY (tenant_id) REFERENCES tenant_profiles(id)
);
