package com.pgcrm.multitenancy;

import com.pgcrm.multitenancy.dto.CreateTenantRequest;
import com.pgcrm.multitenancy.dto.CreateTenantResult;
import com.pgcrm.multitenancy.dto.MigrationVerificationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TenantMigrationIntegrationTest {

    @MockBean
    private JavaMailSender javaMailSender;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantProvisioningService tenantProvisioningService;

    @Autowired
    private TenantMigrationVerifier tenantMigrationVerifier;

    private static final String SOURCE_SCHEMA = "mock_client_src";
    private static final String TARGET_SLUG = "migrated_pg";
    private static final String TARGET_SCHEMA = "t_migrated_pg";

    @BeforeEach
    void setUp() {
        cleanUp();

        // 1. Create a simulated legacy source schema
        jdbcTemplate.execute("CREATE SCHEMA " + SOURCE_SCHEMA);

        // Run tenant Flyway DDL on the source schema so it represents a real dedicated single-tenant DB
        org.flywaydb.core.Flyway.configure()
                .dataSource(jdbcTemplate.getDataSource())
                .schemas(SOURCE_SCHEMA)
                .defaultSchema(SOURCE_SCHEMA)
                .locations("classpath:db/migration/tenant")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        // 2. Insert sample legacy client records in SOURCE_SCHEMA
        String buildingId = UUID.randomUUID().toString();
        jdbcTemplate.update(String.format(
                "INSERT INTO %s.buildings (id, name, address) VALUES (?, 'Sai Residency', '123 Tech Park')",
                SOURCE_SCHEMA), buildingId);

        String guestUserId = UUID.randomUUID().toString();
        jdbcTemplate.update(String.format("""
                INSERT INTO %s.users (
                    id, full_name, email, password, role, active, must_change_password, created_at, updated_at
                ) VALUES (?, 'Anand Kumar', 'anand@guest.com', 'dummyhash', 'GUEST', true, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, SOURCE_SCHEMA), guestUserId);

        String guestId = UUID.randomUUID().toString();
        jdbcTemplate.update(String.format("""
                INSERT INTO %s.guests (
                    id, user_id, full_name, phone, is_active, is_deleted, check_in_date, created_at
                ) VALUES (?, ?, 'Anand Kumar', '+919876543210', true, false, CURRENT_DATE, CURRENT_TIMESTAMP)
                """, SOURCE_SCHEMA), guestId, guestUserId);

        String invoiceId = "inv-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update(String.format("""
                INSERT INTO %s.invoices (
                    id, guest_id, invoice_month, invoice_year, total_amount, status, due_date, generated_at, paid_at, is_deleted
                ) VALUES (?, ?, 5, 2026, 12000.00, 'PAID', CURRENT_DATE + 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, false)
                """, SOURCE_SCHEMA), invoiceId, guestId);

        jdbcTemplate.update(String.format("""
                INSERT INTO %s.invoice_line_items (id, invoice_id, type, description, amount)
                VALUES (?, ?, 'BASE_RENT', 'Monthly Rent for May 2026', 12000.00)
                """, SOURCE_SCHEMA), UUID.randomUUID().toString(), invoiceId);
    }

    @AfterEach
    void cleanUp() {
        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SOURCE_SCHEMA + " CASCADE");
        } catch (Exception ignored) {}

        try {
            tenantProvisioningService.deleteTenant(TARGET_SLUG);
        } catch (Exception ignored) {}

        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + TARGET_SCHEMA + " CASCADE");
        } catch (Exception ignored) {}

        try {
            jdbcTemplate.update("DELETE FROM platform.tenant_domains WHERE tenant_id LIKE '%migrated%'");
            jdbcTemplate.update("DELETE FROM platform.tenants WHERE slug = ?", TARGET_SLUG);
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Migrate legacy client database into target tenant schema and verify 100% data integrity")
    void testEndToEndClientMigrationAndVerification() {
        // Step 1: Provision target tenant schema via platform provisioning
        CreateTenantResult provisionResult = tenantProvisioningService.createTenant(new CreateTenantRequest(
                TARGET_SLUG,
                "Migrated Sai PG",
                "STANDARD",
                null,
                java.util.List.of("saipg.pgcrm.local"),
                "owner@saipg.com",
                "Anand (Owner)",
                "SecretPass123!"
        ));
        assertThat(provisionResult.status()).isEqualTo("ACTIVE");

        // Step 2: Migrate data from SOURCE_SCHEMA to TARGET_SCHEMA
        // (Copy sample records that exist in source into target)
        // Disable foreign key triggers during data copy as migration scripts do
        jdbcTemplate.execute("SET session_replication_role = 'replica'");
        try {
            for (String table : TenantMigrationVerifier.BUSINESS_TABLES) {
                // Clear any default seed data from provisioning for matched tables before copy
                jdbcTemplate.execute(String.format("DELETE FROM %s.%s", TARGET_SCHEMA, table));
                // Copy table data
                jdbcTemplate.execute(String.format("INSERT INTO %s.%s SELECT * FROM %s.%s",
                        TARGET_SCHEMA, table, SOURCE_SCHEMA, table));
            }
        } finally {
            jdbcTemplate.execute("SET session_replication_role = 'origin'");
        }

        // Step 3: Run comprehensive verification
        MigrationVerificationResult report = tenantMigrationVerifier.verifyMigration(SOURCE_SCHEMA, TARGET_SCHEMA);

        assertThat(report.overallPassed()).isTrue();
        assertThat(report.allTablesMatch()).isTrue();
        assertThat(report.invoiceSampleMatches()).isTrue();
        assertThat(report.paymentSampleMatches()).isTrue();
        assertThat(report.discrepancies()).isEmpty();

        // Step 4: Verify that if data is altered, the verifier catches the discrepancy
        String extraInvoiceId = "inv-extra-" + UUID.randomUUID().toString().substring(0, 8);
        String anyGuestId = jdbcTemplate.queryForObject(
                "SELECT id FROM " + TARGET_SCHEMA + ".guests LIMIT 1", String.class);
        jdbcTemplate.update(String.format("""
                INSERT INTO %s.invoices (
                    id, guest_id, invoice_month, invoice_year, total_amount, status, due_date, generated_at, is_deleted
                ) VALUES (?, ?, 6, 2026, 999.00, 'GENERATED', CURRENT_DATE, CURRENT_TIMESTAMP, false)
                """, TARGET_SCHEMA), extraInvoiceId, anyGuestId);

        MigrationVerificationResult tamperedReport = tenantMigrationVerifier.verifyMigration(SOURCE_SCHEMA, TARGET_SCHEMA);
        assertThat(tamperedReport.overallPassed()).isFalse();
        assertThat(tamperedReport.allTablesMatch()).isFalse();
        assertThat(tamperedReport.discrepancies()).anyMatch(d -> d.contains("'invoices' count mismatch"));
    }
}
