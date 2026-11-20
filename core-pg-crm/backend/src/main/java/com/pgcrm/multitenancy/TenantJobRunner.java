package com.pgcrm.multitenancy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Executes background jobs across all active tenants in complete isolation.
 *
 * <p>Requirements enforced:</p>
 * <ul>
 *   <li>Each tenant runs in its own try/catch block.</li>
 *   <li>{@link TenantContext} and SLF4J MDC are populated before execution and cleared in {@code finally}.</li>
 *   <li>A failure in one tenant never aborts or blocks subsequent tenants.</li>
 *   <li>Supports both shared mode (all active tenants) and dedicated mode (single tenant).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantJobRunner {

    private final TenantRegistryService tenantRegistryService;

    /**
     * Executes the given task for each active tenant in isolation.
     *
     * @param jobName human-readable name of the background job
     * @param task    action to execute within each tenant's context
     */
    public void runForActiveTenants(String jobName, Consumer<TenantContext.TenantInfo> task) {
        List<TenantContext.TenantInfo> activeTenants = tenantRegistryService.getAllActiveTenants();
        log.info("▶ Starting multi-tenant job '{}' across {} active tenant(s)", jobName, activeTenants.size());

        int success = 0;
        int failed = 0;

        for (TenantContext.TenantInfo tenant : activeTenants) {
            try {
                TenantContext.setTenant(tenant);
                MDC.put("tenant", tenant.slug());
                log.info("▶ Job '{}' starting for tenant [{}] (schema: {})", jobName, tenant.slug(), tenant.schemaName());

                task.accept(tenant);

                log.info("✓ Job '{}' completed successfully for tenant [{}]", jobName, tenant.slug());
                success++;
            } catch (Exception e) {
                failed++;
                log.error("✗ Job '{}' failed for tenant [{}]: {}", jobName, tenant.slug(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
                MDC.remove("tenant");
            }
        }

        log.info("■ Finished multi-tenant job '{}' — Total: {}, Succeeded: {}, Failed: {}",
                jobName, activeTenants.size(), success, failed);
    }
}
