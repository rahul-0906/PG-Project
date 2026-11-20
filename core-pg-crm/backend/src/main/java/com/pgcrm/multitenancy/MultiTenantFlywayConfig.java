package com.pgcrm.multitenancy;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-tenant Flyway migration orchestrator.
 *
 * <p><strong>Execution Sequence:</strong></p>
 * <ol>
 *   <li>Runs platform migrations once at startup against schema {@code platform}
 *       from {@code classpath:db/migration/platform}.</li>
 *   <li>In {@code DEDICATED} mode, ensures the dedicated tenant record exists and is {@code ACTIVE}.</li>
 *   <li>Queries all {@code ACTIVE} tenants from {@code platform.tenants}.</li>
 *   <li>Runs tenant migrations from {@code classpath:db/migration/tenant} against each active tenant schema.
 *       Failure in one tenant schema is logged and isolated; it does not block other tenants.</li>
 * </ol>
 */
@Configuration
@Slf4j
public class MultiTenantFlywayConfig {

    @Value("${DB_WIPE_ON_STARTUP:false}")
    private boolean wipeOnStartup;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private record TenantMigrationTarget(String slug, String schemaName) {}

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(
            DataSource dataSource,
            DeploymentProperties deploymentProperties) {

        return defaultFlyway -> {
            log.info("═════════════════════════════════════════════════════════════════");
            log.info("  Starting Multi-Tenant Flyway Migrations [mode={}]", deploymentProperties.getDeploymentMode());
            log.info("═════════════════════════════════════════════════════════════════");

            // 1. Run Platform Schema Migrations
            try {
                log.info("[1/3] Running platform schema migrations on 'platform'...");
                Flyway platformFlyway = Flyway.configure()
                        .dataSource(dataSource)
                        .schemas("platform")
                        .defaultSchema("platform")
                        .locations("classpath:db/migration/platform")
                        .baselineOnMigrate(true)
                        .load();
                platformFlyway.migrate();
                log.info("✓ Platform migrations completed successfully.");
            } catch (Exception e) {
                log.error("✗ Fatal: Failed to run platform migrations: {}", e.getMessage(), e);
                throw new IllegalStateException("Platform migration failed", e);
            }

            // 2. In dedicated mode, ensure dedicated tenant exists
            if (deploymentProperties.isDedicated()) {
                String dedicatedSlug = deploymentProperties.getDedicatedTenant();
                ensureDedicatedTenantExists(dataSource, dedicatedSlug);
            }

            // 3. Query all ACTIVE tenants
            List<TenantMigrationTarget> activeTenants = fetchActiveTenants(dataSource);
            log.info("[2/3] Discovered {} active tenant(s) for migration.", activeTenants.size());

            // 4. Migrate each tenant schema
            boolean isProd = "prod".equalsIgnoreCase(activeProfile);
            for (TenantMigrationTarget target : activeTenants) {
                String schema = target.schemaName();
                try {
                    TenantContext.validateIdentifier("schema_name", schema);
                    log.info("--> Migrating tenant '{}' on schema '{}'...", target.slug(), schema);

                    Flyway tenantFlyway = Flyway.configure()
                            .dataSource(dataSource)
                            .schemas(schema)
                            .defaultSchema(schema)
                            .locations("classpath:db/migration/tenant")
                            .baselineOnMigrate(true)
                            .cleanDisabled(isProd)
                            .load();

                    if (wipeOnStartup && !isProd) {
                        log.warn("DB_WIPE_ON_STARTUP=true: Cleaning schema '{}' before migration", schema);
                        tenantFlyway.clean();
                    }

                    tenantFlyway.migrate();
                    log.info("✓ Tenant '{}' schema '{}' migrated successfully.", target.slug(), schema);

                    // If existing single-tenant database has tables in public, copy them to t_default
                    if ("t_default".equalsIgnoreCase(schema) && !isProd) {
                        copyLegacyPublicDataIfPresent(dataSource, schema);
                    }
                } catch (Exception e) {
                    log.error("✗ Failed to migrate tenant schema '{}': {}", schema, e.getMessage(), e);
                    if (deploymentProperties.isDedicated()) {
                        throw new IllegalStateException("Failed to migrate dedicated tenant schema: " + schema, e);
                    }
                }
            }

            log.info("═════════════════════════════════════════════════════════════════");
            log.info("  Multi-Tenant Flyway Migrations Finished Successfully");
            log.info("═════════════════════════════════════════════════════════════════");
        };
    }

    private void ensureDedicatedTenantExists(DataSource dataSource, String dedicatedSlug) {
        TenantContext.validateIdentifier("dedicated_slug", dedicatedSlug);
        String schemaName = "t_" + dedicatedSlug;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            String checkSql = "SELECT COUNT(*) FROM platform.tenants WHERE slug = '" + dedicatedSlug + "'";
            try (ResultSet rs = stmt.executeQuery(checkSql)) {
                if (rs.next() && rs.getInt(1) > 0) {
                    // Tenant exists — verify active status
                    String statusSql = "SELECT status FROM platform.tenants WHERE slug = '" + dedicatedSlug + "'";
                    try (ResultSet statusRs = stmt.executeQuery(statusSql)) {
                        if (statusRs.next()) {
                            String status = statusRs.getString(1);
                            if (!"ACTIVE".equalsIgnoreCase(status)) {
                                throw new IllegalStateException(
                                        "Dedicated tenant '" + dedicatedSlug + "' is not ACTIVE (status: " + status + ")"
                                );
                            }
                        }
                    }
                    return;
                }
            }

            // Dedicated tenant does not exist — bootstrap it
            log.info("Bootstrapping dedicated tenant '{}' with schema '{}' in platform.tenants...",
                    dedicatedSlug, schemaName);

            String insertTenant = String.format("""
                    INSERT INTO platform.tenants (id, slug, display_name, schema_name, plan, status)
                    VALUES ('tenant-%s-001', '%s', 'Dedicated PG CRM', '%s', 'DEDICATED', 'ACTIVE')
                    ON CONFLICT (slug) DO NOTHING
                    """, dedicatedSlug, dedicatedSlug, schemaName);
            stmt.execute(insertTenant);

            String insertDomain = String.format("""
                    INSERT INTO platform.tenant_domains (host, tenant_id)
                    VALUES ('localhost', 'tenant-%s-001')
                    ON CONFLICT (host) DO NOTHING
                    """, dedicatedSlug);
            stmt.execute(insertDomain);

            log.info("✓ Dedicated tenant '{}' registered in platform registry.", dedicatedSlug);
        } catch (Exception e) {
            log.error("Failed to verify/bootstrap dedicated tenant: {}", e.getMessage(), e);
            throw new IllegalStateException("Dedicated tenant bootstrap failed", e);
        }
    }

    private List<TenantMigrationTarget> fetchActiveTenants(DataSource dataSource) {
        List<TenantMigrationTarget> targets = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT slug, schema_name FROM platform.tenants WHERE status = 'ACTIVE'")) {

            while (rs.next()) {
                targets.add(new TenantMigrationTarget(rs.getString("slug"), rs.getString("schema_name")));
            }
        } catch (Exception e) {
            log.error("Failed to query active tenants: {}", e.getMessage());
        }
        return targets;
    }

    private void copyLegacyPublicDataIfPresent(DataSource dataSource, String targetSchema) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check if public.users exists and has data, and targetSchema.users is empty
            boolean publicHasUsers = false;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'users'")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    try (ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM public.users")) {
                        if (countRs.next() && countRs.getInt(1) > 0) {
                            publicHasUsers = true;
                        }
                    }
                }
            }

            if (!publicHasUsers) return;

            boolean targetIsEmpty = true;
            try (ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + targetSchema + ".users")) {
                if (countRs.next() && countRs.getInt(1) > 0) {
                    targetIsEmpty = false;
                }
            }

            if (targetIsEmpty) {
                log.info("Migrating existing data from 'public' to dedicated tenant schema '{}'...", targetSchema);
                String[] tables = {
                        "users", "buildings", "floors", "blocks", "rooms", "beds",
                        "guests", "guest_beds", "invoices", "invoice_line_items",
                        "eb_bills", "eb_bill_guests", "daily_logs", "audit_logs",
                        "maintenance_tickets", "notifications", "building_configs", "meter_readings"
                };
                for (String table : tables) {
                    try {
                        String copySql = String.format(
                                "INSERT INTO %s.%s SELECT * FROM public.%s ON CONFLICT DO NOTHING",
                                targetSchema, table, table
                        );
                        stmt.execute(copySql);
                    } catch (Exception ex) {
                        log.debug("Table {} migration note: {}", table, ex.getMessage());
                    }
                }
                log.info("✓ Legacy public data successfully copied to '{}'", targetSchema);
            }
        } catch (Exception e) {
            log.debug("Notice on legacy data copy check: {}", e.getMessage());
        }
    }
}
