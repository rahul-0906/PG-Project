ALTER TABLE tenant_profiles DROP COLUMN IF EXISTS theme_config;
ALTER TABLE tenant_profiles ADD COLUMN whatsapp_token VARCHAR(255);
ALTER TABLE tenant_profiles ADD COLUMN whatsapp_key VARCHAR(255);
