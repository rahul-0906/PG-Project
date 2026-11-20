package com.pgcrm.multitenancy;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * TaskDecorator propagating TenantContext and SLF4J MDC across asynchronous
 * threads (such as @Async or TaskExecutor executions).
 */
@Component
public class TenantAwareTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        final TenantContext.TenantInfo tenantInfo = TenantContext.getCurrentTenant();
        final Map<String, String> contextMap = MDC.getCopyOfContextMap();

        return () -> {
            try {
                if (tenantInfo != null) {
                    TenantContext.setTenant(tenantInfo);
                }
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                TenantContext.clear();
                MDC.clear();
            }
        };
    }
}
