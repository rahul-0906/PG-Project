package com.pgcrm.multitenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TenantAwareTaskDecoratorTest {

    private TenantAwareTaskDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new TenantAwareTaskDecorator();
        TenantContext.clear();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        MDC.clear();
    }

    @Test
    void testTenantContextPropagatedToDecoratedTask() throws InterruptedException {
        TenantContext.TenantInfo tenant = new TenantContext.TenantInfo(
                "t1", "alpha_pg", "t_alpha_pg", "Alpha PG", "ACTIVE"
        );
        TenantContext.setTenant(tenant);
        MDC.put("tenant", "alpha_pg");

        AtomicReference<String> asyncTenantSlug = new AtomicReference<>();
        AtomicReference<String> asyncMdcTenant = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            asyncTenantSlug.set(TenantContext.getCurrentTenantSlug());
            asyncMdcTenant.set(MDC.get("tenant"));
        });

        // Run decorated task in a separate background thread
        Thread asyncThread = new Thread(decorated);
        asyncThread.start();
        asyncThread.join();

        assertEquals("alpha_pg", asyncTenantSlug.get());
        assertEquals("alpha_pg", asyncMdcTenant.get());
    }

    @Test
    void testContextClearedAfterExecution() {
        TenantContext.TenantInfo tenant = new TenantContext.TenantInfo(
                "t1", "beta_pg", "t_beta_pg", "Beta PG", "ACTIVE"
        );
        TenantContext.setTenant(tenant);

        Runnable decorated = decorator.decorate(() -> {
            // Task executes with tenant set
            assertNotNull(TenantContext.getCurrentTenant());
        });

        // Outside caller thread should clear after execution if executed in same thread
        decorated.run();

        // After decorated runnable finishes, thread context should be cleared
        assertNull(TenantContext.getCurrentTenant());
        assertNull(MDC.get("tenant"));
    }
}
