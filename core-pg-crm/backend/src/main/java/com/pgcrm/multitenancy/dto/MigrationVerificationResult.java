package com.pgcrm.multitenancy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Report containing the results of comparing a legacy single-tenant source database/schema
 * against a migrated multi-tenant schema (t_<slug>).
 */
public record MigrationVerificationResult(
        String sourceSchema,
        String targetSchema,
        boolean allTablesMatch,
        boolean invoiceSampleMatches,
        boolean paymentSampleMatches,
        boolean overallPassed,
        List<TableCountComparison> tableCounts,
        List<InvoiceSampleComparison> invoiceSamples,
        List<PaymentSampleComparison> paymentSamples,
        List<String> discrepancies,
        LocalDateTime verifiedAt
) {
    public record TableCountComparison(
            String tableName,
            long sourceCount,
            long targetCount,
            boolean matches
    ) {}

    public record InvoiceSampleComparison(
            String invoiceId,
            String invoiceNumber,
            BigDecimal sourceAmount,
            BigDecimal targetAmount,
            BigDecimal sourceBalance,
            BigDecimal targetBalance,
            String sourceStatus,
            String targetStatus,
            boolean matches
    ) {}

    public record PaymentSampleComparison(
            String invoiceId,
            BigDecimal sourcePaidAmount,
            BigDecimal targetPaidAmount,
            boolean matches
    ) {}
}
