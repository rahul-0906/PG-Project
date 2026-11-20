package com.pgcrm.multitenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testValidTenantContextLifecycle() {
        assertNull(TenantContext.getCurrentTenant());
        assertNull(TenantContext.getCurrentTenantSlug());
        assertNull(TenantContext.getCurrentTenantSchema());

        TenantContext.TenantInfo info = new TenantContext.TenantInfo(
                "id-123", "sri_sai", "t_sri_sai", "Sri Sai PG", "ACTIVE"
        );

        TenantContext.setTenant(info);
        assertEquals("sri_sai", TenantContext.getCurrentTenantSlug());
        assertEquals("t_sri_sai", TenantContext.getCurrentTenantSchema());
        assertEquals("ACTIVE", TenantContext.getCurrentTenant().status());

        TenantContext.clear();
        assertNull(TenantContext.getCurrentTenant());
    }

    @Test
    void testSlugValidationRejectsInvalidIdentifiers() {
        // Uppercase not allowed
        assertThrows(IllegalArgumentException.class, () ->
                new TenantContext.TenantInfo("1", "SriSai", "t_srisai", "Display", "ACTIVE"));

        // Must start with letter
        assertThrows(IllegalArgumentException.class, () ->
                new TenantContext.TenantInfo("1", "123tenant", "t_123", "Display", "ACTIVE"));

        // Too short (< 3 chars)
        assertThrows(IllegalArgumentException.class, () ->
                new TenantContext.TenantInfo("1", "ab", "t_ab", "Display", "ACTIVE"));

        // Special characters (e.g. SQL injection attempt)
        assertThrows(IllegalArgumentException.class, () ->
                new TenantContext.TenantInfo("1", "slug;drop table users;", "t_hack", "Display", "ACTIVE"));
    }

    @Test
    void testValidIdentifiersPass() {
        assertDoesNotThrow(() -> TenantContext.validateIdentifier("slug", "default"));
        assertDoesNotThrow(() -> TenantContext.validateIdentifier("slug", "sri_sai_pg"));
        assertDoesNotThrow(() -> TenantContext.validateIdentifier("schema", "t_default"));
        assertDoesNotThrow(() -> TenantContext.validateIdentifier("schema", "t_tenant_123"));
    }
}
