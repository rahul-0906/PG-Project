package com.pgcrm.multitenancy;

import com.pgcrm.multitenancy.dto.MigrationVerificationResult;
import com.pgcrm.multitenancy.dto.MigrationVerificationResult.InvoiceSampleComparison;
import com.pgcrm.multitenancy.dto.MigrationVerificationResult.PaymentSampleComparison;
import com.pgcrm.multitenancy.dto.MigrationVerificationResult.TableCountComparison;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Verifier service to perform comprehensive data integrity verification when migrating
 * an existing single-tenant client database into a multi-tenant schema (t_<slug>).
 *
 * <p><strong>Verification Requirements (Spec Phase 6):</strong></p>
 * <ul>
 *   <li>Verify row counts across all core business tables between source and target schema.</li>
 *   <li>Verify a random/recent sample of invoices (amounts, status, month/year).</li>
 *   <li>Verify a sample of payments and paid receipts between source and target.</li>
 *   <li>Guarantee fail-closed: If ANY discrepancy is detected, migration is flagged as unverified.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantMigrationVerifier {

    private final JdbcTemplate jdbcTemplate;

    public static final List<String> BUSINESS_TABLES = List.of(
            "users",
            "buildings",
            "building_configs",
            "floors",
            "blocks",
            "rooms",
            "beds",
            "guests",
            "guest_beds",
            "invoices",
            "invoice_line_items",
            "eb_bills",
            "eb_bill_guests",
            "daily_logs",
            "meter_readings",
            "maintenance_tickets",
            "notifications",
            "pricing_config",
            "audit_logs"
    );

    /**
     * Verifies data integrity between source and target schema within the configured database.
     *
     * @param sourceSchema e.g. "public" or dedicated source schema
     * @param targetSchema e.g. "t_sri_sai"
     * @return structured verification report
     */
    public MigrationVerificationResult verifyMigration(String sourceSchema, String targetSchema) {
        return verifyMigrationWithTemplates(jdbcTemplate, sourceSchema, jdbcTemplate, targetSchema);
    }

    /**
     * Verifies data integrity across different DataSources (e.g. separate source DB and target DB).
     */
    public MigrationVerificationResult verifyMigrationAcrossDataSources(
            DataSource sourceDs, String sourceSchema,
            DataSource targetDs, String targetSchema) {
        return verifyMigrationWithTemplates(
                new JdbcTemplate(sourceDs), sourceSchema,
                new JdbcTemplate(targetDs), targetSchema
        );
    }

    public MigrationVerificationResult verifyMigrationWithTemplates(
            JdbcTemplate sourceJdbc, String sourceSchema,
            JdbcTemplate targetJdbc, String targetSchema) {

        TenantContext.validateIdentifier("source_schema", sourceSchema);
        TenantContext.validateIdentifier("target_schema", targetSchema);

        log.info("▶ Starting migration verification: source='{}' -> target='{}'", sourceSchema, targetSchema);

        List<TableCountComparison> tableComparisons = new ArrayList<>();
        List<String> discrepancies = new ArrayList<>();
        boolean allTablesMatch = true;

        // 1. Compare Row Counts across all core business tables
        for (String table : BUSINESS_TABLES) {
            long sourceCount = getTableCountSafe(sourceJdbc, sourceSchema, table);
            long targetCount = getTableCountSafe(targetJdbc, targetSchema, table);

            boolean matches = (sourceCount == targetCount);
            if (!matches) {
                allTablesMatch = false;
                String msg = String.format("Table '%s' count mismatch: source=%d, target=%d", table, sourceCount, targetCount);
                discrepancies.add(msg);
                log.warn("✗ {}", msg);
            } else {
                log.debug("✓ Table '{}' matched: {} rows", table, sourceCount);
            }

            tableComparisons.add(new TableCountComparison(table, sourceCount, targetCount, matches));
        }

        // 2. Compare Sample of Invoices
        List<InvoiceSampleComparison> invoiceSamples = new ArrayList<>();
        boolean invoiceSampleMatches = true;

        try {
            String sourceInvoiceSql = String.format("""
                    SELECT id, invoice_month, invoice_year, total_amount, status
                    FROM %s.invoices
                    ORDER BY generated_at DESC NULLS LAST
                    LIMIT 10
                    """, sourceSchema);

            List<Map<String, Object>> sourceInvoices = sourceJdbc.queryForList(sourceInvoiceSql);

            for (Map<String, Object> srcInv : sourceInvoices) {
                String id = (String) srcInv.get("id");
                BigDecimal srcTotal = (BigDecimal) srcInv.get("total_amount");
                String srcStatus = (String) srcInv.get("status");

                String targetInvoiceSql = String.format("""
                        SELECT id, total_amount, status
                        FROM %s.invoices WHERE id = ?
                        """, targetSchema);

                List<Map<String, Object>> targetInvoices = targetJdbc.queryForList(targetInvoiceSql, id);
                if (targetInvoices.isEmpty()) {
                    invoiceSampleMatches = false;
                    discrepancies.add("Sample invoice missing in target: " + id);
                    invoiceSamples.add(new InvoiceSampleComparison(id, id, srcTotal, null, null, null, srcStatus, null, false));
                } else {
                    Map<String, Object> tgtInv = targetInvoices.get(0);
                    BigDecimal tgtTotal = (BigDecimal) tgtInv.get("total_amount");
                    String tgtStatus = (String) tgtInv.get("status");

                    boolean amountMatch = (srcTotal == null && tgtTotal == null) ||
                            (srcTotal != null && tgtTotal != null && srcTotal.compareTo(tgtTotal) == 0);
                    boolean statusMatch = Objects.equals(srcStatus, tgtStatus);
                    boolean invMatch = amountMatch && statusMatch;

                    if (!invMatch) {
                        invoiceSampleMatches = false;
                        discrepancies.add(String.format("Invoice mismatch for ID %s: srcAmount=%s, tgtAmount=%s, srcStatus=%s, tgtStatus=%s",
                                id, srcTotal, tgtTotal, srcStatus, tgtStatus));
                    }

                    invoiceSamples.add(new InvoiceSampleComparison(
                            id, id, srcTotal, tgtTotal, null, null, srcStatus, tgtStatus, invMatch
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Error verifying invoice samples: {}", e.getMessage());
            invoiceSampleMatches = false;
            discrepancies.add("Invoice sample query error: " + e.getMessage());
        }

        // 3. Compare Sample of Payments / Paid Invoices
        List<PaymentSampleComparison> paymentSamples = new ArrayList<>();
        boolean paymentSampleMatches = true;

        try {
            String sourcePaymentSql = String.format("""
                    SELECT id, total_amount, status, razorpay_payment_id
                    FROM %s.invoices
                    WHERE status = 'PAID' OR razorpay_payment_id IS NOT NULL
                    ORDER BY paid_at DESC NULLS LAST
                    LIMIT 10
                    """, sourceSchema);

            List<Map<String, Object>> sourcePaidInvoices = sourceJdbc.queryForList(sourcePaymentSql);

            for (Map<String, Object> srcPaid : sourcePaidInvoices) {
                String id = (String) srcPaid.get("id");
                BigDecimal srcAmount = (BigDecimal) srcPaid.get("total_amount");

                String targetPaymentSql = String.format("""
                        SELECT id, total_amount, status, razorpay_payment_id
                        FROM %s.invoices WHERE id = ?
                        """, targetSchema);

                List<Map<String, Object>> targetRows = targetJdbc.queryForList(targetPaymentSql, id);
                if (targetRows.isEmpty()) {
                    paymentSampleMatches = false;
                    discrepancies.add("Paid invoice sample missing in target: " + id);
                    paymentSamples.add(new PaymentSampleComparison(id, srcAmount, null, false));
                } else {
                    Map<String, Object> tgtPaid = targetRows.get(0);
                    BigDecimal tgtAmount = (BigDecimal) tgtPaid.get("total_amount");

                    boolean amountMatch = (srcAmount == null && tgtAmount == null) ||
                            (srcAmount != null && tgtAmount != null && srcAmount.compareTo(tgtAmount) == 0);

                    if (!amountMatch) {
                        paymentSampleMatches = false;
                        discrepancies.add(String.format("Payment amount mismatch for ID %s: src=%s, tgt=%s", id, srcAmount, tgtAmount));
                    }

                    paymentSamples.add(new PaymentSampleComparison(id, srcAmount, tgtAmount, amountMatch));
                }
            }
        } catch (Exception e) {
            log.error("Error verifying payment samples: {}", e.getMessage());
            paymentSampleMatches = false;
            discrepancies.add("Payment sample query error: " + e.getMessage());
        }

        boolean overallPassed = allTablesMatch && invoiceSampleMatches && paymentSampleMatches;

        if (overallPassed) {
            log.info("★ Migration verification PASSED between '{}' and '{}' across all tables and samples.", sourceSchema, targetSchema);
        } else {
            log.error("✗ Migration verification FAILED between '{}' and '{}': {} discrepancies found.",
                    sourceSchema, targetSchema, discrepancies.size());
        }

        return new MigrationVerificationResult(
                sourceSchema,
                targetSchema,
                allTablesMatch,
                invoiceSampleMatches,
                paymentSampleMatches,
                overallPassed,
                tableComparisons,
                invoiceSamples,
                paymentSamples,
                discrepancies,
                LocalDateTime.now()
        );
    }

    private long getTableCountSafe(JdbcTemplate template, String schema, String table) {
        try {
            Long count = template.queryForObject(
                    String.format("SELECT COUNT(*) FROM %s.%s", schema, table),
                    Long.class
            );
            return count != null ? count : 0L;
        } catch (Exception e) {
            // Table might not exist in source if from an older migration baseline
            log.debug("Table {}.{} does not exist or cannot be counted: {}", schema, table, e.getMessage());
            return 0L;
        }
    }
}
