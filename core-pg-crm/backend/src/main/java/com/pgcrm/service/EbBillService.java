package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.Block;
import com.pgcrm.entity.Building;
import com.pgcrm.entity.EbBill;
import com.pgcrm.entity.EbBillGuest;
import com.pgcrm.entity.Guest;
import com.pgcrm.entity.enums.EbSplitMethod;
import com.pgcrm.repository.BlockRepository;
import com.pgcrm.repository.EbBillGuestRepository;
import com.pgcrm.repository.EbBillRepository;
import com.pgcrm.repository.GuestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for recording and distributing block-level electricity bills
 * across the resident guests of a {@link Block}.
 *
 * <p>Supports three EB split strategies, determined by the building's configured
 * {@link EbSplitMethod} (falling back to the YAML global default when no building
 * config override is present):</p>
 * <ul>
 *   <li><strong>EQUAL_SPLIT / PER_BED:</strong> Total bill divided equally among all
 *       guests active in the block during the billing period. Handled by
 *       {@link #recordAndSplit(String, BigDecimal, LocalDate, LocalDate)}.</li>
 *   <li><strong>METER_BASED:</strong> Each guest's share is computed individually from
 *       per-room electricity meter readings. Handled by
 *       {@link #recordMeterBased(String, BigDecimal, LocalDate, LocalDate, List)}.</li>
 *   <li><strong>MANAGER_MANUAL:</strong> Shares are entered directly by the manager
 *       through a separate endpoint (not implemented in this service).</li>
 * </ul>
 *
 * <p><strong>Persistence Model:</strong> Each call creates one parent {@link EbBill} record
 * and multiple child {@link EbBillGuest} rows — one per active guest in the block.
 * These child rows are consumed by {@code InvoiceService} during monthly billing
 * to generate the {@code EB} line item on each guest's invoice.</p>
 *
 * @see EbBill
 * @see EbBillGuest
 * @see EbSplitMethod
 * @see GuestRepository#findActiveGuestsInBlock(String, LocalDate, LocalDate)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EbBillService {

    private final EbBillRepository       ebBillRepository;
    private final EbBillGuestRepository  ebBillGuestRepository;
    private final BlockRepository        blockRepository;
    private final GuestRepository        guestRepository;
    private final SystemConfigProperties systemConfig;

    // ── Equal / Per-Bed Split ─────────────────────────────────────────────────

    /**
     * Records a block-level electricity bill and distributes it equally among all active
     * guests in the block for the given billing period.
     *
     * <p><strong>Split method resolution:</strong></p>
     * <ol>
     *   <li>Per-building {@link com.pgcrm.entity.BuildingConfig#getEbSplitMethod()} (if set).</li>
     *   <li>Global {@link SystemConfigProperties.Rules#getEbSplitMethod()} YAML default.</li>
     * </ol>
     *
     * <p>Only guests with an active stay that overlaps the billing period
     * ({@code checkInDate ≤ periodEnd AND (actualCheckOutDate IS NULL OR actualCheckOutDate ≥ periodStart)})
     * are included in the split. If no eligible guests are found, the parent
     * {@link EbBill} is still saved but no {@link EbBillGuest} rows are created.</p>
     *
     * <p>This method handles {@code EQUAL_SPLIT} and {@code PER_BED} strategies.
     * {@code METER_BASED} is handled by {@link #recordMeterBased(String, BigDecimal, LocalDate, LocalDate, List)}.
     * {@code MANAGER_MANUAL} is handled externally.</p>
     *
     * @param blockId      the UUID of the {@link Block} being billed.
     * @param totalAmount  the total electricity bill amount for the block and period.
     * @param periodStart  the start date of the billing period (inclusive).
     * @param periodEnd    the end date of the billing period (inclusive).
     * @return the saved parent {@link EbBill} entity.
     * @throws RuntimeException if no block is found for the given {@code blockId}.
     */
    @Transactional
    public EbBill recordAndSplit(final String blockId, final BigDecimal totalAmount,
                                 final LocalDate periodStart, final LocalDate periodEnd) {
        log.info("Recording and splitting EB bill for block ID: {}, amount: {}, period: {} to {}",
                blockId, totalAmount, periodStart, periodEnd);
        try {
            final Block    block    = blockRepository.findById(blockId)
                    .orElseThrow(() -> {
                        log.warn("Block not found for ID: {} during EB split", blockId);
                        return new RuntimeException("Block not found: " + blockId);
                    });
            final Building building = (block.getFloor() != null) ? block.getFloor().getBuilding() : null;

            // Resolve the split method — building config takes precedence over the YAML default.
            String splitMethod = systemConfig.getRules().getEbSplitMethod();
            if (building != null && building.getBuildingConfig() != null
                    && building.getBuildingConfig().getEbSplitMethod() != null) {
                splitMethod = building.getBuildingConfig().getEbSplitMethod().name();
            }

            EbBill bill = EbBill.builder()
                    .block(block)
                    .totalAmount(totalAmount)
                    .splitMethod(splitMethod)
                    .billingPeriodStart(periodStart)
                    .billingPeriodEnd(periodEnd)
                    .build();
            bill = ebBillRepository.save(bill);

            final List<Guest> activeGuests = guestRepository.findActiveGuestsInBlock(blockId, periodStart, periodEnd);
            if (activeGuests.isEmpty()) {
                log.info("No active guests found in block ID: {} for EB split period {} to {}", blockId, periodStart, periodEnd);
                return bill;
            }

            final List<EbBillGuest> shares = new ArrayList<>();
            if (EbSplitMethod.EQUAL_SPLIT.name().equals(splitMethod)
                    || EbSplitMethod.PER_BED.name().equals(splitMethod)) {
                final BigDecimal perGuest = totalAmount.divide(
                        BigDecimal.valueOf(activeGuests.size()), 2, RoundingMode.HALF_UP);
                for (final Guest guest : activeGuests) {
                    shares.add(EbBillGuest.builder()
                            .ebBill(bill)
                            .guest(guest)
                            .shareAmount(perGuest)
                            .build());
                }
            }
            // METER_BASED and MANAGER_MANUAL are handled by their respective dedicated methods.

            ebBillGuestRepository.saveAll(shares);
            log.info("Successfully recorded and split EB bill ID: {} (Block ID: {}) among {} guests", bill.getId(), blockId, activeGuests.size());
            return bill;
        } catch (Exception e) {
            log.error("Error splitting EB bill for block ID: {}", blockId, e);
            throw e;
        }
    }

    // ── Meter-Based Split ─────────────────────────────────────────────────────

    /**
     * Records a block-level electricity bill using per-guest electricity meter readings.
     *
     * <p>The total bill amount is derived by summing the individually computed guest costs
     * ({@code (currentReading - previousReading) × ratePerUnit}). Each guest's share is
     * recorded in a separate {@link EbBillGuest} row alongside the raw meter reading values,
     * enabling a full audit trail of the per-unit consumption calculation.</p>
     *
     * <p>Each entry in the {@code readings} list must contain the following keys:</p>
     * <ul>
     *   <li>{@code "guestId"} — the UUID string of the guest.</li>
     *   <li>{@code "previousReading"} — the meter reading at the start of the period.</li>
     *   <li>{@code "currentReading"} — the meter reading at the end of the period.</li>
     * </ul>
     *
     * @param blockId      the UUID of the {@link Block} being billed.
     * @param ratePerUnit  the electricity rate per kWh (e.g., {@code ₹8.50}).
     * @param periodStart  the start date of the billing period (inclusive).
     * @param periodEnd    the end date of the billing period (inclusive).
     * @param readings     a list of per-guest meter reading maps; must not be {@code null}.
     * @return the saved parent {@link EbBill} entity with the computed total.
     * @throws RuntimeException if no block is found for {@code blockId}, or if no
     *                          guest is found for a {@code guestId} in the readings list.
     */
    @Transactional
    public EbBill recordMeterBased(final String blockId, final BigDecimal ratePerUnit,
                                   final LocalDate periodStart, final LocalDate periodEnd,
                                   final List<Map<String, Object>> readings) {
        log.info("Recording meter-based EB bill for block ID: {}, rate: {}, period: {} to {}",
                blockId, ratePerUnit, periodStart, periodEnd);
        try {
            final Block block = blockRepository.findById(blockId)
                    .orElseThrow(() -> {
                        log.warn("Block not found for ID: {} during meter-based EB split", blockId);
                        return new RuntimeException("Block not found: " + blockId);
                    });

            // Sum across all guest readings to compute the block-level total bill amount.
            final BigDecimal total = readings.stream()
                    .map(r -> {
                        final BigDecimal prev = new BigDecimal(r.get("previousReading").toString());
                        final BigDecimal curr = new BigDecimal(r.get("currentReading").toString());
                        return curr.subtract(prev).multiply(ratePerUnit);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            EbBill bill = EbBill.builder()
                    .block(block)
                    .totalAmount(total)
                    .ratePerUnit(ratePerUnit)
                    .splitMethod(EbSplitMethod.METER_BASED.name())
                    .billingPeriodStart(periodStart)
                    .billingPeriodEnd(periodEnd)
                    .build();
            bill = ebBillRepository.save(bill);

            final List<EbBillGuest> shares = new ArrayList<>();
            for (final Map<String, Object> r : readings) {
                final String  guestId = r.get("guestId").toString();
                final Guest   guest   = guestRepository.findById(guestId)
                        .orElseThrow(() -> {
                            log.warn("Guest not found for ID: {} during meter-based EB split", guestId);
                            return new RuntimeException("Guest not found: " + guestId);
                        });

                final BigDecimal prev  = new BigDecimal(r.get("previousReading").toString());
                final BigDecimal curr  = new BigDecimal(r.get("currentReading").toString());
                final BigDecimal units = curr.subtract(prev).setScale(2, RoundingMode.HALF_UP);
                final BigDecimal share = units.multiply(ratePerUnit).setScale(2, RoundingMode.HALF_UP);

                shares.add(EbBillGuest.builder()
                        .ebBill(bill)
                        .guest(guest)
                        .previousReading(prev)
                        .currentReading(curr)
                        .unitsConsumed(units)
                        .shareAmount(share)
                        .build());
            }

            ebBillGuestRepository.saveAll(shares);
            log.info("Successfully recorded meter-based EB bill ID: {} (Block ID: {}), total amount: {}, shares: {}",
                    bill.getId(), blockId, total, shares.size());
            return bill;
        } catch (Exception e) {
            log.error("Error recording meter-based EB bill for block ID: {}", blockId, e);
            throw e;
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns the total EB bill share amount accrued for a guest in a given block.
     *
     * <p>Sums all {@link EbBillGuest#getShareAmount()} values for the guest across
     * all EB bills recorded for the specified block. The result is used by the
     * {@code InvoiceService} to populate the {@code EB} line item on the guest's monthly invoice.</p>
     *
     * @param guestId the UUID of the {@link Guest}.
     * @param blockId the UUID of the {@link Block}.
     * @return the total EB share amount (sum of all shares); {@link BigDecimal#ZERO} if none exist.
     */
    public BigDecimal getGuestEbShareForMonth(final String guestId, final String blockId) {
        final List<EbBillGuest> shares = ebBillGuestRepository.findByEbBill_BlockIdAndGuestId(blockId, guestId);
        return shares.stream()
                .map(EbBillGuest::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
