package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.EbSplitMethod;
import com.pgcrm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EbBillService {

    private final EbBillRepository ebBillRepository;
    private final EbBillGuestRepository ebBillGuestRepository;
    private final BlockRepository blockRepository;
    private final GuestRepository guestRepository;
    private final SystemConfigProperties systemConfig;

    // ── Equal Split ────────────────────────────────────────────────────────
    @Transactional
    public EbBill recordAndSplit(String blockId, BigDecimal totalAmount,
                                  LocalDate periodStart, LocalDate periodEnd) {
        Block block = blockRepository.findById(blockId)
                .orElseThrow(() -> new RuntimeException("Block not found: " + blockId));

        Building building = (block.getFloor() != null) ? block.getFloor().getBuilding() : null;
        String splitMethod = systemConfig.getRules().getEbSplitMethod();
        if (building != null && building.getBuildingConfig() != null && building.getBuildingConfig().getEbSplitMethod() != null) {
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

        List<Guest> activeGuests = guestRepository.findActiveGuestsInBlock(blockId, periodStart, periodEnd);
        if (activeGuests.isEmpty()) return bill;

        List<EbBillGuest> shares = new ArrayList<>();
        if ("EQUAL_SPLIT".equals(splitMethod) || "PER_BED".equals(splitMethod)) {
            BigDecimal perGuest = totalAmount.divide(
                    BigDecimal.valueOf(activeGuests.size()), 2, RoundingMode.HALF_UP);
            for (Guest guest : activeGuests) {
                shares.add(EbBillGuest.builder()
                        .ebBill(bill).guest(guest).shareAmount(perGuest).build());
            }
        }
        // METER_BASED and MANAGER_MANUAL are handled by recordMeterBased() and recordManual()

        ebBillGuestRepository.saveAll(shares);
        return bill;
    }

    // ── Meter-Based Split ──────────────────────────────────────────────────
    /**
     * Manager provides previous + current meter reading per guest.
     * Bill is calculated as: unitsConsumed × ratePerUnit per guest.
     *
     * @param blockId      Block ID
     * @param ratePerUnit  Electricity rate per kWh (e.g. ₹8.50)
     * @param periodStart  Billing period start
     * @param periodEnd    Billing period end
     * @param readings     List of {guestId, previousReading, currentReading}
     * @param tenantId     Tenant ID
     */
    @Transactional
    public EbBill recordMeterBased(String blockId, BigDecimal ratePerUnit,
                                    LocalDate periodStart, LocalDate periodEnd,
                                    List<Map<String, Object>> readings) {
        Block block = blockRepository.findById(blockId)
                .orElseThrow(() -> new RuntimeException("Block not found: " + blockId));

        // Calculate total from all guest readings
        BigDecimal total = readings.stream()
            .map(r -> {
                BigDecimal prev = new BigDecimal(r.get("previousReading").toString());
                BigDecimal curr = new BigDecimal(r.get("currentReading").toString());
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

        List<EbBillGuest> shares = new ArrayList<>();
        for (Map<String, Object> r : readings) {
            String guestId = r.get("guestId").toString();
            Guest guest = guestRepository.findById(guestId)
                    .orElseThrow(() -> new RuntimeException("Guest not found: " + guestId));

            BigDecimal prev  = new BigDecimal(r.get("previousReading").toString());
            BigDecimal curr  = new BigDecimal(r.get("currentReading").toString());
            BigDecimal units = curr.subtract(prev).setScale(2, RoundingMode.HALF_UP);
            BigDecimal share = units.multiply(ratePerUnit).setScale(2, RoundingMode.HALF_UP);

            shares.add(EbBillGuest.builder()
                    .ebBill(bill).guest(guest)
                    .previousReading(prev).currentReading(curr)
                    .unitsConsumed(units).shareAmount(share)
                    .build());
        }

        ebBillGuestRepository.saveAll(shares);
        return bill;
    }

    public BigDecimal getGuestEbShareForMonth(String guestId, String blockId) {
        List<EbBillGuest> shares = ebBillGuestRepository.findByEbBill_BlockIdAndGuestId(blockId, guestId);
        return shares.stream()
                .map(EbBillGuest::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
