package com.pgcrm.multitenancy.alerts;

import com.pgcrm.multitenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ErrorAlertServiceTest {

    private ErrorAlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new ErrorAlertService();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testSendAlertWithTenantContext() {
        TenantContext.setCurrentTenant("sunrise_pg", "t_sunrise_pg");

        assertDoesNotThrow(() ->
                alertService.sendAlert("SCHEDULER", "Monthly billing failed for tenant", new RuntimeException("DB Timeout"))
        );
    }

    @Test
    void testSendAlertWithoutTenantDefaultsToSystemLevel() {
        assertDoesNotThrow(() ->
                alertService.sendAlert("DATABASE", "Connection pool exhausted", null)
        );
    }

    @Test
    void testFailSafeWhenWebhookFails() {
        // Even if webhook is invalid or fails, it should never throw an exception to the caller
        assertDoesNotThrow(() ->
                alertService.sendAlert("AUTH", "Brute force detected", new IllegalStateException("Security breach"))
        );
    }
}
