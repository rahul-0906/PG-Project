package com.pgcrm.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Hibernate {@link CurrentTenantIdentifierResolver} determining which tenant schema
 * is targeted by the current Hibernate session.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    private final DeploymentProperties deploymentProperties;

    public TenantIdentifierResolver(DeploymentProperties deploymentProperties) {
        this.deploymentProperties = deploymentProperties;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        String schema = TenantContext.getCurrentTenantSchema();
        if (schema != null && !schema.isBlank()) {
            return schema;
        }

        // Fallback for bootstrap, dedicated mode, or platform context
        if (deploymentProperties.isDedicated()) {
            return "t_" + deploymentProperties.getDedicatedTenant();
        }

        return "platform";
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
