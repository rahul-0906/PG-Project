-- ============================================================
--  PG CRM — Platform Schema Initialization (V1)
--  Holds ONLY the tenant registry and domain mapping.
--  Tenant business data NEVER goes here.
-- ============================================================

CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE IF NOT EXISTS platform.tenants (
    id VARCHAR(36) PRIMARY KEY,
    slug VARCHAR(40) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    schema_name VARCHAR(40) NOT NULL UNIQUE,
    plan VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    renewal_date DATE,
    CONSTRAINT chk_tenant_slug CHECK (slug ~ '^[a-z][a-z0-9_]{2,40}$'),
    CONSTRAINT chk_tenant_schema CHECK (schema_name ~ '^[a-z][a-z0-9_]{2,40}$'),
    CONSTRAINT chk_tenant_status CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE TABLE IF NOT EXISTS platform.tenant_domains (
    host VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL REFERENCES platform.tenants(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tenants_status ON platform.tenants(status);
CREATE INDEX IF NOT EXISTS idx_tenants_slug ON platform.tenants(slug);
CREATE INDEX IF NOT EXISTS idx_tenant_domains_tenant_id ON platform.tenant_domains(tenant_id);
