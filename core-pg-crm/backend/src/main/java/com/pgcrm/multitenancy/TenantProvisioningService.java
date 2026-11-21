package com.pgcrm.multitenancy;

import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.multitenancy.dto.CreateTenantRequest;
import com.pgcrm.multitenancy.dto.CreateTenantResult;
import com.pgcrm.multitenancy.dto.TenantSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service managing tenant lifecycle operations (provisioning, activation, suspension, deletion).
 *
 * <p><strong>Tenant Provisioning Flow:</strong></p>
 * <ol>
 *   <li>Validate tenant slug and schema name against {@code ^[a-z][a-z0-9_]{2,40}$}.</li>
 *   <li>Insert record into {@code platform.tenants} with status {@code PROVISIONING}.</li>
 *   <li>Create dedicated PostgreSQL schema {@code t_<slug>}.</li>
 *   <li>Execute all tenant Flyway migrations against {@code t_<slug>}.</li>
 *   <li>Initialize default branding and feature settings in {@code t_<slug>.tenant_settings}.</li>
 *   <li>Create initial PG Owner user account in {@code t_<slug>.users} with temporary credentials.</li>
 *   <li>Map custom/subdomain hosts in {@code platform.tenant_domains}.</li>
 *   <li>Update tenant status to {@code ACTIVE} and evict registry cache.</li>
 * </ol>
 *
 * <p><strong>Rollback Guarantee:</strong> If any provisioning step fails, all artifacts created
 * during the run (PostgreSQL schema, domain entries, registry record) are rolled back cleanly.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProvisioningService {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final TenantRegistryService tenantRegistryService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates and activates a new tenant end-to-end with automated rollback on failure.
     *
     * @param request tenant creation specifications
     * @return provisioning confirmation result
     */
    public CreateTenantResult createTenant(CreateTenantRequest request) {
        if (request.slug() == null || request.slug().isBlank()) {
            throw new IllegalArgumentException("Tenant slug is required");
        }

        final String slug = request.slug().trim().toLowerCase();
        TenantContext.validateIdentifier("slug", slug);

        final String schemaName = "t_" + slug;
        TenantContext.validateIdentifier("schema_name", schemaName);

        final String tenantId = "tenant-" + UUID.randomUUID().toString().substring(0, 8);
        final String displayName = (request.displayName() != null && !request.displayName().isBlank())
                ? request.displayName().trim()
                : slug;
        final String plan = (request.plan() != null && !request.plan().isBlank())
                ? request.plan().trim().toUpperCase()
                : "STANDARD";

        final String ownerEmail = (request.ownerEmail() != null && !request.ownerEmail().isBlank())
                ? request.ownerEmail().trim().toLowerCase()
                : "owner@" + slug + ".pgcrm.com";
        final String ownerName = (request.ownerName() != null && !request.ownerName().isBlank())
                ? request.ownerName().trim()
                : "Owner (" + displayName + ")";
        final String tempPassword = (request.initialPassword() != null && !request.initialPassword().isBlank())
                ? request.initialPassword()
                : generateSecurePassword();

        log.info("▶ Starting tenant provisioning: slug='{}', schema='{}', id='{}'", slug, schemaName, tenantId);

        boolean schemaCreated = false;
        boolean registryInserted = false;

        try {
            // 1. Insert into platform.tenants as PROVISIONING
            final String insertRegistrySql = """
                    INSERT INTO platform.tenants (id, slug, display_name, schema_name, plan, status, created_at, renewal_date)
                    VALUES (?, ?, ?, ?, ?, 'PROVISIONING', CURRENT_TIMESTAMP, ?)
                    """;
            Date renewalSqlDate = request.renewalDate() != null ? Date.valueOf(request.renewalDate()) : null;
            jdbcTemplate.update(insertRegistrySql, tenantId, slug, displayName, schemaName, plan, renewalSqlDate);
            registryInserted = true;
            log.info("✓ Inserted platform.tenants record as PROVISIONING for slug '{}'", slug);

            // 2. Create schema in PostgreSQL
            jdbcTemplate.execute("CREATE SCHEMA " + schemaName);
            schemaCreated = true;
            log.info("✓ Created PostgreSQL schema '{}'", schemaName);

            // 3. Run tenant Flyway migrations on the new schema
            log.info("Running tenant Flyway migrations on schema '{}'...", schemaName);
            Flyway tenantFlyway = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schemaName)
                    .defaultSchema(schemaName)
                    .locations("classpath:db/migration/tenant")
                    .baselineOnMigrate(true)
                    .load();
            tenantFlyway.migrate();
            log.info("✓ Tenant Flyway migrations completed for schema '{}'", schemaName);

            // 4. Insert initial default tenant_settings
            final String settingsId = UUID.randomUUID().toString();
            final String insertSettingsSql = String.format("""
                    INSERT INTO %s.tenant_settings (
                        id, branding_name, branding_short_name, branding_primary_color,
                        notification_email_enabled, notification_whatsapp_enabled,
                        created_at, updated_at
                    ) VALUES (?, ?, ?, '#2563eb', true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, schemaName);
            jdbcTemplate.update(insertSettingsSql, settingsId, displayName, slug.toUpperCase());
            log.info("✓ Initialized default tenant_settings in schema '{}'", schemaName);

            // 5. Create Owner user account in t_<slug>.users
            final String ownerUserId = UUID.randomUUID().toString();
            final String passwordHash = passwordEncoder.encode(tempPassword);
            final String insertOwnerSql = String.format("""
                    INSERT INTO %s.users (
                        id, full_name, email, password, role, active, must_change_password, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 'PG_OWNER', true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, schemaName);
            jdbcTemplate.update(insertOwnerSql, ownerUserId, ownerName, ownerEmail, passwordHash);
            log.info("✓ Created PG_OWNER account '{}' in schema '{}'", ownerEmail, schemaName);

            // 6. Map domains if provided
            if (request.domains() != null && !request.domains().isEmpty()) {
                final String insertDomainSql = "INSERT INTO platform.tenant_domains (host, tenant_id) VALUES (?, ?)";
                for (String domain : request.domains()) {
                    if (domain != null && !domain.isBlank()) {
                        jdbcTemplate.update(insertDomainSql, domain.trim().toLowerCase(), tenantId);
                    }
                }
                log.info("✓ Mapped {} domain(s) for tenant '{}'", request.domains().size(), slug);
            }

            // 7. Update status to ACTIVE
            jdbcTemplate.update("UPDATE platform.tenants SET status = 'ACTIVE' WHERE id = ?", tenantId);
            tenantRegistryService.evict(null, slug);
            log.info("★ Tenant '{}' successfully provisioned and marked ACTIVE!", slug);

            return new CreateTenantResult(
                    tenantId, slug, schemaName, displayName, "ACTIVE", ownerEmail, tempPassword
            );

        } catch (Exception e) {
            log.error("✗ Tenant provisioning failed for slug '{}': {}. Initiating rollback...", slug, e.getMessage(), e);
            rollback(slug, schemaName, tenantId, schemaCreated, registryInserted);
            throw new IllegalStateException("Failed to provision tenant '" + slug + "': " + e.getMessage(), e);
        }
    }

    /**
     * Suspends an active tenant, immediately blocking incoming requests with HTTP 403.
     *
     * @param slug tenant slug
     */
    public void suspendTenant(String slug) {
        TenantContext.validateIdentifier("slug", slug);
        int rows = jdbcTemplate.update("UPDATE platform.tenants SET status = 'SUSPENDED' WHERE slug = ?", slug);
        if (rows == 0) {
            throw new ResourceNotFoundException("Tenant not found: " + slug);
        }
        tenantRegistryService.evict(null, slug);
        log.info("Tenant '{}' status updated to SUSPENDED", slug);
    }

    /**
     * Activates a suspended tenant.
     *
     * @param slug tenant slug
     */
    public void activateTenant(String slug) {
        TenantContext.validateIdentifier("slug", slug);
        int rows = jdbcTemplate.update("UPDATE platform.tenants SET status = 'ACTIVE' WHERE slug = ?", slug);
        if (rows == 0) {
            throw new ResourceNotFoundException("Tenant not found: " + slug);
        }
        tenantRegistryService.evict(null, slug);
        log.info("Tenant '{}' status updated to ACTIVE", slug);
    }

    /**
     * Soft-deletes a tenant by updating status to DELETED.
     * In accordance with Section 7 of the specification, schemas are never dropped automatically.
     *
     * @param slug tenant slug
     */
    public void deleteTenant(String slug) {
        TenantContext.validateIdentifier("slug", slug);
        int rows = jdbcTemplate.update("UPDATE platform.tenants SET status = 'DELETED' WHERE slug = ?", slug);
        if (rows == 0) {
            throw new ResourceNotFoundException("Tenant not found: " + slug);
        }
        tenantRegistryService.evict(null, slug);
        log.info("Tenant '{}' marked as DELETED. PostgreSQL schema preserved.", slug);
    }

    /**
     * Lists all registered tenants from platform.tenants.
     *
     * @return list of tenant summaries
     */
    public List<TenantSummaryResponse> listTenants() {
        final String sql = """
                SELECT id, slug, display_name, schema_name, plan, status, renewal_date, created_at
                FROM platform.tenants
                ORDER BY created_at DESC
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Date renewalDateSql = rs.getDate("renewal_date");
            Timestamp createdAtSql = rs.getTimestamp("created_at");
            return new TenantSummaryResponse(
                    rs.getString("id"),
                    rs.getString("slug"),
                    rs.getString("display_name"),
                    rs.getString("schema_name"),
                    rs.getString("plan"),
                    rs.getString("status"),
                    renewalDateSql != null ? renewalDateSql.toLocalDate() : null,
                    createdAtSql != null ? createdAtSql.toLocalDateTime() : null
            );
        });
    }

    private void rollback(String slug, String schemaName, String tenantId,
                          boolean schemaCreated, boolean registryInserted) {
        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
            log.info("↺ Rollback: Dropped schema '{}'", schemaName);
        } catch (Exception ex) {
            log.error("Rollback error dropping schema '{}': {}", schemaName, ex.getMessage());
        }

        if (registryInserted) {
            try {
                jdbcTemplate.update("DELETE FROM platform.tenant_domains WHERE tenant_id = ?", tenantId);
                jdbcTemplate.update("DELETE FROM platform.tenants WHERE id = ?", tenantId);
                log.info("↺ Rollback: Deleted registry records for tenant '{}' ({})", slug, tenantId);
            } catch (Exception ex) {
                log.error("Rollback error deleting registry record: {}", ex.getMessage());
            }
        }

        tenantRegistryService.evict(null, slug);
    }

    private String generateSecurePassword() {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
