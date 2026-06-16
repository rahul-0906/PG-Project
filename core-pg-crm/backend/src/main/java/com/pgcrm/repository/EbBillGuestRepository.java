package com.pgcrm.repository;

import com.pgcrm.entity.EbBillGuest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for the {@link EbBillGuest} entity.
 *
 * <p>Provides query operations against the {@code eb_bill_guests} table, which holds
 * per-guest electricity cost share allocations for a parent {@link com.pgcrm.entity.EbBill}.
 * Each row in this table represents one guest's computed share of a block-level
 * electricity bill for a specific billing period.</p>
 *
 * <p><strong>EB Split Methods:</strong> Depending on the building's configured
 * {@link com.pgcrm.entity.enums.EbSplitMethod}, the share amount recorded in each
 * {@link EbBillGuest} row is computed differently. Regardless of the split method,
 * this repository treats each row identically — the calculation logic lives
 * exclusively in the {@code EbService}.</p>
 *
 * <p>These guest share records are consumed by the {@code InvoiceService} during the
 * monthly billing pipeline to create {@code EB} line items on each guest's invoice.</p>
 *
 * @see EbBillGuest
 * @see EbBillRepository
 * @see com.pgcrm.entity.enums.EbSplitMethod
 */
@Repository
public interface EbBillGuestRepository extends JpaRepository<EbBillGuest, String> {

    /**
     * Returns all EB bill guest share records associated with a specific guest.
     *
     * <p>Used by the guest portal to display the guest's historical electricity
     * bill share history, and by the invoice service to check for existing EB
     * share records before generating invoice line items.</p>
     *
     * @param guestId the UUID of the {@link com.pgcrm.entity.Guest}.
     * @return a {@link List} of all {@link EbBillGuest} records for the guest;
     *         empty list if none exist.
     */
    List<EbBillGuest> findByGuestId(String guestId);

    /**
     * Finds a specific EB bill guest share record by the parent bill ID and guest ID.
     *
     * <p>Used to check whether a guest's share for a particular EB bill has already
     * been recorded, supporting idempotent EB bill updates in the {@code EbService}.</p>
     *
     * @param ebBillId the UUID of the parent {@link com.pgcrm.entity.EbBill}.
     * @param guestId  the UUID of the {@link com.pgcrm.entity.Guest}.
     * @return an {@link Optional} containing the matching {@link EbBillGuest},
     *         or {@link Optional#empty()} if no record exists for this bill/guest pair.
     */
    Optional<EbBillGuest> findByEbBillIdAndGuestId(String ebBillId, String guestId);

    /**
     * Returns all EB bill guest share records for a specific block, filtered by guest.
     *
     * <p>Used by the invoice billing pipeline (scheduled cron job) to retrieve all
     * unbilled EB shares for a guest in a given block, which are then converted into
     * {@code EB} invoice line items. The block scoping ensures only the shares relevant
     * to the guest's actual block assignment are included.</p>
     *
     * <p>The derived query traverses the {@code EbBillGuest → EbBill → blockId} path
     * using Spring Data's double-underscore convention ({@code EbBill_BlockId}).</p>
     *
     * @param blockId the UUID of the {@link com.pgcrm.entity.Block} whose EB bills to search.
     * @param guestId the UUID of the {@link com.pgcrm.entity.Guest}.
     * @return a {@link List} of {@link EbBillGuest} records matching the block and guest;
     *         empty list if none exist.
     */
    List<EbBillGuest> findByEbBill_BlockIdAndGuestId(String blockId, String guestId);
}
