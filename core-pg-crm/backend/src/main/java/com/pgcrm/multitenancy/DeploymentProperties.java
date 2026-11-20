package com.pgcrm.multitenancy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for multi-tenancy and deployment mode.
 *
 * <pre>
 * app:
 *   deployment-mode: shared | dedicated
 *   dedicated-tenant: &lt;slug&gt;        # required only in dedicated mode
 *   base-domain: &lt;domain&gt;           # shared mode subdomain base
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class DeploymentProperties {

    public enum DeploymentMode {
        SHARED,
        DEDICATED
    }

    /**
     * Deployment mode: SHARED (multi-tenant) or DEDICATED (single-tenant).
     * Defaults to DEDICATED.
     */
    private DeploymentMode deploymentMode = DeploymentMode.DEDICATED;

    /**
     * Slug of the dedicated tenant. Used exclusively in DEDICATED mode.
     * Startup fails if this tenant does not exist or is not ACTIVE.
     */
    private String dedicatedTenant = "default";

    /**
     * Base domain for host-based subdomain resolution in SHARED mode.
     * Example: "pgcrm.com" (or "localhost" for local testing).
     */
    private String baseDomain = "localhost";

    public boolean isDedicated() {
        return deploymentMode == DeploymentMode.DEDICATED;
    }

    public boolean isShared() {
        return deploymentMode == DeploymentMode.SHARED;
    }
}
