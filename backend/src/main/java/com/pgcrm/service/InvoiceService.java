package com.pgcrm.service;

import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.InvoiceLineType;
import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.repository.*;
import com.pgcrm.config.SystemConfigProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final GuestRepository guestRepository;
    private final DailyLogRepository dailyLogRepository;
    private final EbBillGuestRepository ebBillGuestRepository;
    private final SystemConfigProperties systemConfig;
    private final NotificationService notificationService;
    private final PricingService pricingService;
    private final BuildingConfigRepository buildingConfigRepository;

    /** DTO returned by previewInvoice() — no DB writes */
    @Data
    public static class InvoicePreview {
        public String id;
        public String guestId;
        public String guestName;
        public String bedLabel;
        public String roomNumber;
        public String floor;
        public BigDecimal rent;
        public BigDecimal ebShare;
        public BigDecimal food;
        public BigDecimal laundry;
        public BigDecimal total;
        public boolean alreadyGenerated;
        public String status;
    }

    @Transactional
    public Invoice generateInvoiceForGuest(Guest guest, int month, int year) {
        // Skip if already generated
        Optional<Invoice> existing = invoiceRepository.findByGuestIdAndMonthAndYear(guest.getId(), month, year);
        if (existing.isPresent()) {
            log.info("Invoice already exists for guest {} for {}/{}", guest.getId(), month, year);
            return existing.get();
        }


        YearMonth ym = YearMonth.of(year, month);
        LocalDate periodStart = ym.atDay(1);
        LocalDate periodEnd = ym.atEndOfMonth();

        List<InvoiceLineItem> lineItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        // ── 1. RENT (pro-rated if mid-month check-in) ─────────────
        BigDecimal rent = calculateProRatedRent(guest, ym);
        lineItems.add(InvoiceLineItem.builder()
                .type(InvoiceLineType.RENT)
                .description("Room Rent - " + ym.getMonth().name() + " " + year)
                .amount(rent)
                .build());
        total = total.add(rent);

        // ── 2. EB Share ───────────────────────────────────────────
        BigDecimal ebShare = BigDecimal.ZERO;
        if (guest.getBed() != null && guest.getBed().getRoom().getBlock() != null) {
            String blockId = guest.getBed().getRoom().getBlock().getId();
            List<EbBillGuest> ebShares = ebBillGuestRepository.findByEbBill_BlockIdAndGuestId(blockId, guest.getId());
            ebShare = ebShares.stream()
                    .filter(s -> isWithinPeriod(s.getEbBill().getBillingPeriodStart(), periodStart, periodEnd))
                    .map(EbBillGuest::getShareAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        lineItems.add(InvoiceLineItem.builder()
                .type(InvoiceLineType.EB)
                .description("Electricity Bill Share")
                .amount(ebShare)
                .build());
        total = total.add(ebShare);

        // ── Resolve per-building pricing ──────────────────────────
        String buildingId = (guest.getBed() != null && guest.getBed().getRoom() != null
                && guest.getBed().getRoom().getFloor() != null)
                ? guest.getBed().getRoom().getFloor().getBuilding().getId() : null;
        PricingService.EffectivePricing pricing = pricingService.getEffectivePricing(buildingId);

        // ── 3. FOOD (skip if food included in rent) ───────────────
        BigDecimal foodTotal = BigDecimal.ZERO;
        boolean foodIncluded = systemConfig.getRules().isFoodIncludedInRent();
        if (buildingId != null) {
            foodIncluded = buildingConfigRepository.findById(buildingId)
                    .map(BuildingConfig::isFoodIncludedInRent)
                    .orElse(foodIncluded);
        }
        if (!foodIncluded) {
            foodTotal = calculateFoodTotal(guest, periodStart, periodEnd, pricing);
        }
        lineItems.add(InvoiceLineItem.builder()
                .type(InvoiceLineType.FOOD)
                .description("Food & Extras")
                .amount(foodTotal)
                .build());
        total = total.add(foodTotal);

        // ── 4. LAUNDRY ────────────────────────────────────────────
        BigDecimal laundryTotal = BigDecimal.ZERO;
        if (systemConfig.getRules().isHasWashingMachine()) {
            laundryTotal = calculateLaundryTotal(guest, periodStart, periodEnd, pricing);
        }
        lineItems.add(InvoiceLineItem.builder()
                .type(InvoiceLineType.LAUNDRY)
                .description("Washing Machine")
                .amount(laundryTotal)
                .build());
        total = total.add(laundryTotal);

        // ── Build Invoice ─────────────────────────────────────────
        LocalDate dueDate = YearMonth.of(year, month).atDay(systemConfig.getRules().getPaymentDueDayOfMonth());
        Invoice invoice = Invoice.builder()
                .guest(guest)
                .month(month)
                .year(year)
                .totalAmount(total)
                .status(InvoiceStatus.GENERATED)
                .dueDate(dueDate)
                .build();

        final Invoice savedInvoice = invoiceRepository.save(invoice);
        lineItems.forEach(item -> item.setInvoice(savedInvoice));
        savedInvoice.setLineItems(lineItems);
        invoiceRepository.save(savedInvoice);

        // ── Send Notifications ────────────────────────────────────
        String message = buildInvoiceMessage(guest, savedInvoice, rent, ebShare, foodTotal, laundryTotal);
        notificationService.sendBoth(guest, message);

        log.info("Invoice generated for guest {} | Rent={} EB={} Food={} WM={} Total={}",
                guest.getId(), rent, ebShare, foodTotal, laundryTotal, total);
        return savedInvoice;
    }

    private BigDecimal calculateProRatedRent(Guest guest, YearMonth ym) {
        BigDecimal baseRent = guest.getBed() != null
                ? guest.getBed().getRoom().getBaseRent()
                : BigDecimal.ZERO;
        LocalDate checkIn = guest.getCheckInDate();
        if (checkIn == null) return baseRent;

        int totalDays = ym.lengthOfMonth();
        int activeDays;
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        if (checkIn.isAfter(monthEnd)) return BigDecimal.ZERO;
        LocalDate effectiveStart = checkIn.isBefore(monthStart) ? monthStart : checkIn;
        activeDays = (int) (monthEnd.toEpochDay() - effectiveStart.toEpochDay()) + 1;

        if (activeDays >= totalDays) return baseRent;
        return baseRent.multiply(BigDecimal.valueOf(activeDays))
                       .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateFoodTotal(Guest guest, LocalDate start, LocalDate end, PricingService.EffectivePricing pricing) {
        List<DailyLog> logs = dailyLogRepository.findByGuestIdAndLogDateBetween(guest.getId(), start, end);
        BigDecimal total = BigDecimal.ZERO;
        for (DailyLog log : logs) {
            if (log.isBreakfastOpted()) total = total.add(pricing.breakfast());
            if (log.isLunchOpted())     total = total.add(pricing.lunch());
            if (log.isDinnerOpted())    total = total.add(pricing.dinner());
            if (log.getOmeletteCount() > 0)
                total = total.add(pricing.omelette().multiply(BigDecimal.valueOf(log.getOmeletteCount())));
            if (log.getBoiledEggCount() > 0)
                total = total.add(pricing.boiledEgg().multiply(BigDecimal.valueOf(log.getBoiledEggCount())));
        }
        return total;
    }

    private BigDecimal calculateLaundryTotal(Guest guest, LocalDate start, LocalDate end, PricingService.EffectivePricing pricing) {
        List<DailyLog> logs = dailyLogRepository.findByGuestIdAndLogDateBetween(guest.getId(), start, end);
        int totalUses = logs.stream().mapToInt(DailyLog::getWashingMachineCount).sum();
        return pricing.washingMachine().multiply(BigDecimal.valueOf(totalUses));
    }

    /**
     * Computes an invoice preview for a guest without persisting anything.
     */
    public InvoicePreview previewInvoice(Guest guest, int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate periodStart = ym.atDay(1);
        LocalDate periodEnd   = ym.atEndOfMonth();

        String buildingId = (guest.getBed() != null && guest.getBed().getRoom() != null
                && guest.getBed().getRoom().getFloor() != null)
                ? guest.getBed().getRoom().getFloor().getBuilding().getId() : null;
        PricingService.EffectivePricing pricing = pricingService.getEffectivePricing(buildingId);

        BigDecimal rent = calculateProRatedRent(guest, ym);

        BigDecimal ebShare = BigDecimal.ZERO;
        if (guest.getBed() != null && guest.getBed().getRoom().getBlock() != null) {
            String blockId = guest.getBed().getRoom().getBlock().getId();
            List<EbBillGuest> ebShares = ebBillGuestRepository.findByEbBill_BlockIdAndGuestId(blockId, guest.getId());
            ebShare = ebShares.stream()
                    .filter(s -> isWithinPeriod(s.getEbBill().getBillingPeriodStart(), periodStart, periodEnd))
                    .map(EbBillGuest::getShareAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal food = BigDecimal.ZERO;
        boolean foodIncluded = systemConfig.getRules().isFoodIncludedInRent();
        if (buildingId != null) {
            foodIncluded = buildingConfigRepository.findById(buildingId)
                    .map(BuildingConfig::isFoodIncludedInRent)
                    .orElse(foodIncluded);
        }
        if (!foodIncluded) {
            food = calculateFoodTotal(guest, periodStart, periodEnd, pricing);
        }

        BigDecimal laundry = BigDecimal.ZERO;
        if (systemConfig.getRules().isHasWashingMachine()) {
            laundry = calculateLaundryTotal(guest, periodStart, periodEnd, pricing);
        }

        Optional<Invoice> existing = invoiceRepository.findByGuestIdAndMonthAndYear(guest.getId(), month, year);
        boolean alreadyGenerated = existing.isPresent();
        String invoiceId = existing.isPresent() ? existing.get().getId() : null;
        String status = existing.isPresent() ? existing.get().getStatus().name() : null;

        InvoicePreview preview = new InvoicePreview();
        preview.id        = invoiceId;
        preview.guestId   = guest.getId();
        preview.guestName = guest.getFullName();
        preview.bedLabel  = guest.getBed() != null ? guest.getBed().getBedLabel() : "—";
        preview.roomNumber = guest.getBed() != null ? guest.getBed().getRoom().getRoomNumber() : "—";
        preview.floor      = (guest.getBed() != null && guest.getBed().getRoom().getFloor() != null)
                             ? guest.getBed().getRoom().getFloor().getFloorLabel() : "—";
        preview.rent      = rent;
        preview.ebShare   = ebShare;
        preview.food      = food;
        preview.laundry   = laundry;
        preview.total     = rent.add(ebShare).add(food).add(laundry);
        preview.alreadyGenerated = alreadyGenerated;
        preview.status    = status;
        return preview;
    }

    private boolean isWithinPeriod(LocalDate billStart, LocalDate periodStart, LocalDate periodEnd) {
        return !billStart.isBefore(periodStart) && !billStart.isAfter(periodEnd);
    }

    private String buildInvoiceMessage(Guest g, Invoice inv, BigDecimal rent, BigDecimal eb,
                                        BigDecimal food, BigDecimal wm) {
        String template = systemConfig.getRules().getInvoiceWhatsappTemplate();
        return template
                .replace("{guestName}", g.getFullName())
                .replace("{month}", inv.getMonth() + "/" + inv.getYear())
                .replace("{rent}", "₹" + rent)
                .replace("{eb}", "₹" + eb)
                .replace("{food}", "₹" + food)
                .replace("{wm}", "₹" + wm)
                .replace("{total}", "₹" + inv.getTotalAmount())
                .replace("{dueDate}", inv.getDueDate().toString());
    }
}
