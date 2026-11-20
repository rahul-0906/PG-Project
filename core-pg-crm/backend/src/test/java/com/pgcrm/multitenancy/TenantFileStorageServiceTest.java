package com.pgcrm.multitenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class TenantFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private TenantFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new TenantFileStorageService(tempDir.toString());
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testTenantPathIsolation() {
        TenantContext.setTenant(new TenantContext.TenantInfo("t1", "delta_pg", "t_delta_pg", "Delta PG", "ACTIVE"));

        Path tenantPath = storageService.getTenantStorageDirectory();
        assertTrue(tenantPath.endsWith("delta_pg"));
        assertTrue(tenantPath.startsWith(tempDir));

        Path uploads = storageService.resolveSubfolder("uploads");
        assertTrue(uploads.endsWith("uploads"));
        assertTrue(uploads.startsWith(tenantPath));
    }

    @Test
    void testPathTraversalGuard() {
        TenantContext.setTenant(new TenantContext.TenantInfo("t1", "safe_pg", "t_safe_pg", "Safe PG", "ACTIVE"));

        assertThrows(SecurityException.class, () -> {
            storageService.resolveSubfolder("../outside_escape");
        });
    }
}
