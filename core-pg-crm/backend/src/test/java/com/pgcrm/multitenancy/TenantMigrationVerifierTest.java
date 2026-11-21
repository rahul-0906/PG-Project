package com.pgcrm.multitenancy;

import com.pgcrm.multitenancy.dto.MigrationVerificationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantMigrationVerifierTest {

    @Mock
    private JdbcTemplate sourceJdbc;

    @Mock
    private JdbcTemplate targetJdbc;

    @Test
    @DisplayName("Verification passes when all table counts and samples match perfectly")
    void testVerificationSuccess() {
        TenantMigrationVerifier verifier = new TenantMigrationVerifier(sourceJdbc);

        // Mock counts for all tables: return 5 for both source and target
        when(sourceJdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(5L);
        when(targetJdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(5L);

        // Mock invoice samples
        Map<String, Object> sampleInvoice = Map.of(
                "id", "inv-001",
                "invoice_month", 5,
                "invoice_year", 2026,
                "total_amount", new BigDecimal("12000.00"),
                "status", "PAID"
        );
        when(sourceJdbc.queryForList(contains("FROM public.invoices"))).thenReturn(List.of(sampleInvoice));
        when(targetJdbc.queryForList(contains("FROM t_tenant.invoices WHERE id = ?"), eq("inv-001")))
                .thenReturn(List.of(sampleInvoice));

        // Mock payment samples
        when(sourceJdbc.queryForList(contains("WHERE status = 'PAID'"))).thenReturn(List.of(sampleInvoice));

        MigrationVerificationResult result = verifier.verifyMigrationWithTemplates(
                sourceJdbc, "public",
                targetJdbc, "t_tenant"
        );

        assertThat(result.overallPassed()).isTrue();
        assertThat(result.allTablesMatch()).isTrue();
        assertThat(result.invoiceSampleMatches()).isTrue();
        assertThat(result.paymentSampleMatches()).isTrue();
        assertThat(result.discrepancies()).isEmpty();
        assertThat(result.tableCounts()).hasSize(TenantMigrationVerifier.BUSINESS_TABLES.size());
    }

    @Test
    @DisplayName("Verification fails when table row count differs")
    void testTableCountMismatch() {
        TenantMigrationVerifier verifier = new TenantMigrationVerifier(sourceJdbc);

        // Source has 10 guests, target has 8
        when(sourceJdbc.queryForObject(contains(".guests"), eq(Long.class))).thenReturn(10L);
        when(targetJdbc.queryForObject(contains(".guests"), eq(Long.class))).thenReturn(8L);

        // Return empty for samples
        when(sourceJdbc.queryForList(anyString())).thenReturn(List.of());

        MigrationVerificationResult result = verifier.verifyMigrationWithTemplates(
                sourceJdbc, "public",
                targetJdbc, "t_tenant"
        );

        assertThat(result.overallPassed()).isFalse();
        assertThat(result.allTablesMatch()).isFalse();
        assertThat(result.discrepancies()).anyMatch(d -> d.contains("'guests' count mismatch: source=10, target=8"));
    }

    @Test
    @DisplayName("Verification fails when invoice sample amount or status mismatches")
    void testInvoiceSampleMismatch() {
        TenantMigrationVerifier verifier = new TenantMigrationVerifier(sourceJdbc);

        when(sourceJdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(targetJdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        Map<String, Object> srcInv = Map.of(
                "id", "inv-002",
                "total_amount", new BigDecimal("10000.00"),
                "status", "PENDING"
        );
        Map<String, Object> tgtInv = Map.of(
                "id", "inv-002",
                "total_amount", new BigDecimal("9500.00"), // Mismatched amount
                "status", "PENDING"
        );

        when(sourceJdbc.queryForList(contains("FROM public.invoices"))).thenReturn(List.of(srcInv));
        when(targetJdbc.queryForList(contains("FROM t_tenant.invoices WHERE id = ?"), eq("inv-002")))
                .thenReturn(List.of(tgtInv));
        when(sourceJdbc.queryForList(contains("WHERE status = 'PAID'"))).thenReturn(List.of());

        MigrationVerificationResult result = verifier.verifyMigrationWithTemplates(
                sourceJdbc, "public",
                targetJdbc, "t_tenant"
        );

        assertThat(result.overallPassed()).isFalse();
        assertThat(result.invoiceSampleMatches()).isFalse();
        assertThat(result.discrepancies()).anyMatch(d -> d.contains("Invoice mismatch for ID inv-002"));
    }

    @Test
    @DisplayName("Invalid schema names are rejected to prevent SQL injection")
    void testInvalidSchemaNameRejected() {
        TenantMigrationVerifier verifier = new TenantMigrationVerifier(sourceJdbc);

        assertThatThrownBy(() -> verifier.verifyMigration("public; DROP TABLE users;", "t_valid"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> verifier.verifyMigration("public", "t_valid--injection"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
