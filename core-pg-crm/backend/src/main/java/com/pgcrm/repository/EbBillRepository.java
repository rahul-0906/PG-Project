package com.pgcrm.repository;

import com.pgcrm.entity.EbBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the {@link EbBill} entity.
 *
 * <p>Provides query operations against the {@code eb_bills} table, which holds
 * block-level electricity bill records. Each {@link EbBill} covers one billing period
 * for one {@link com.pgcrm.entity.Block} and serves as the parent record for the
 * individual guest share rows stored in {@link EbBillGuestRepository}.</p>
 *
 * <p><strong>Billing Period Uniqueness:</strong> The combination of
 * {@code (blockId, billingPeriodStart, billingPeriodEnd)} is enforced as a logical
 * unique constraint at the service layer using
 * {@link #findByBlockIdAndBillingPeriodStartAndBillingPeriodEnd(String, LocalDate, LocalDate)}.
 * The {@code EbService} uses this lookup to prevent duplicate EB bill entries for
 * the same block and period.</p>
 *
 * @see EbBill
 * @see EbBillGuestRepository
 * @see com.pgcrm.entity.enums.EbSplitMethod
 */
@Repository
public interface EbBillRepository extends JpaRepository<EbBill, String> {

    /**
     * Returns all EB bill records for a specific block.
     *
     * <p>Used by the manager dashboard's EB billing history view to list all past
     * electricity bills recorded for a given block, ordered by the default repository
     * sort (no explicit ordering — callers may sort by {@code billingPeriodStart} if needed).</p>
     *
     * @param blockId the UUID of the {@link com.pgcrm.entity.Block}.
     * @return a {@link List} of all {@link EbBill} records for the block;
     *         empty list if none exist.
     */
    List<EbBill> findByBlockId(String blockId);

    /**
     * Finds an EB bill record for a specific block and exact billing period.
     *
     * <p>Used as a duplicate-guard lookup in the {@code EbService}: before creating a
     * new {@link EbBill} for a block, the service calls this method to check whether
     * a bill for the same period already exists. If a result is present, the service
     * either rejects the request or updates the existing record depending on workflow rules.</p>
     *
     * @param blockId the UUID of the {@link com.pgcrm.entity.Block}.
     * @param start   the start date of the billing period (inclusive).
     * @param end     the end date of the billing period (inclusive).
     * @return an {@link Optional} containing the matching {@link EbBill},
     *         or {@link Optional#empty()} if no bill exists for this block and period.
     */
    Optional<EbBill> findByBlockIdAndBillingPeriodStartAndBillingPeriodEnd(
            String blockId, LocalDate start, LocalDate end);
}
