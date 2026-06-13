package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.BuildingConfig;
import com.pgcrm.entity.DailyLog;
import com.pgcrm.entity.EbBillGuest;
import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.InvoiceLineItem;
import com.pgcrm.entity.enums.InvoiceLineType;
import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.repository.BuildingConfigRepository;
import com.pgcrm.repository.DailyLogRepository;
import com.pgcrm.repository.EbBillGuestRepository;
import com.pgcrm.repository.GuestRepository;
import com.pgcrm.repository.InvoiceRepository;
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

/**
 * Service responsible for generating, previewing, and calculating monthly invoices
 * for all active guests.
 *
 * <p><strong>Invoice Line Items:</strong> Each generated invoice contains up to four
 * {@link InvoiceLineItem} entries:</p>
 * <ol>
 *   <li><strong>RENT:</strong> Pro-rated if the guest checked in mid-month; full rent otherwise.</li>
 *   <li><strong>EB (Electricity Bill):</strong> The guest's share of the block EB bill for
 *       the period, joined from pre-computed {@link EbBillGuest} rows.</li>
 *   <li><strong>FOOD:</strong> Meal opt-in totals computed from {@link DailyLog} records.
 *       Skipped entirely if {@code foodIncludedInRent = true} for the building.</li>
 *   <li><strong>LAUNDRY:</strong> Washing machine usage totals. Skipped if
 *       {@code hasWashingMachine = false} in the global config.</li>
 * </ol>
 *
 * <p><strong>Idempotency:</strong> {@link #generateInvoiceForGuest} is idempotent — calling
 * it multiple times for the same {@code (guestId, month, year)} triple returns the existing
 * invoice without creating a duplicate.</p>
 *
 * <p><strong>Pricing Precedence:</strong> Building-level {@link BuildingConfig} price overrides
 * take precedence over the YAML global defaults, consistent with {@link PricingService}.</p>
 *
 * <p><strong>Preview Mode:</strong> {@link #previewInvoice(Guest, int, int)} computes all the
 * same amounts but does not persist anything — used by the manager UI to show a pre-flight
 * estimate before the billing run.</p>
 *
 * @see InvoiceLineItem
 * @see PricingService
 * @see NotificationService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final InvoiceRepository        invoiceRepository;
    private final GuestRepository          guestRepository;
    private final DailyLogRepository       dailyLogRepository;
    private final EbBillGuestRepository    ebBillGuestRepository;
    private final SystemConfigProperties   systemConfig;
    private final NotificationService      notificationService;
    private final PricingService           pricingService;
    private final BuildingConfigRepository buildingConfigRepository;

    /**
     * Read-only DTO returned by {@link #previewInvoice(Guest, int, int)}.
     * Contains the computed amounts and metadata for a prospective invoice
     * without any database writes.
     */
    @Data
    public static class InvoicePreview {
        /** The existing invoice ID, or {@code null} if not yet generated. */
        public String     id;
        /** The UUID of the guest. */
        public String     guestId;
        /** The guest's full display name. */
        public String     guestName;
        /** The bed label (e.g., {@code "1A-2"}) or {@code "—"} if unassigned. */
        public String     bedLabel;
        /** The room number (e.g., {@code "1A-2S1"}) or {@code "—"} if unassigned. */
        public String     roomNumber;
        /** The floor label (e.g., {@code "1st Floor"}) or {@code "—"} if unresolved. */
        public String     floor;
        /** The computed rent amount (pro-rated if mid-month check-in). */
        public BigDecimal rent;
        /** The EB bill share amount for this guest and billing period. */
        public BigDecimal ebShare;
        /** The total food amount computed from daily logs. */
        public BigDecimal food;
        /** The total laundry amount computed from daily logs. */
        public BigDecimal laundry;
        /** The sum of all four line items. */
        public BigDecimal total;
        /** Whether an invoice for this guest/month/year already exists in the database. */
        public boolean    alreadyGenerated;
        /** The status name of the existing invoice, or {@code null} if not yet generated. */
        public String     status;
    }

    /**
     * Generates and persists a monthly invoice for the given guest, or returns the
     * existing invoice if one has already been generated for the same month/year.
     *
     * <p><strong>Computation steps:</strong></p>
     * <ol>
     *   <li>Idempotency check — returns existing invoice if found.</li>
     *   <li>Computes pro-rated rent via {@link #calculateProRatedRent}.</li>
     *   <li>Sums the guest's EB bill share for the period from {@link EbBillGuest} rows.</li>
     *   <li>Computes food total from daily logs via {@link #calculateFoodTotal} (unless food is included in rent).</li>
     *   <li>Computes laundry total from washing machine log counts via {@link #calculateLaundryTotal} (unless no washing machine).</li>
     *   <li>Persists the parent {@link Invoice} and all {@link InvoiceLineItem} children.</li>
     *   <li>Dispatches an invoice notification to the guest via {@link NotificationService#sendBoth}.</li>
     * </ol>
     *
     * @param guest the {@link Guest} to generate the invoice for.
     * @param month the billing month (1–12).
     * @param year  the billing year (e.g., {@code 2025}).
     * @return the newly created or pre-existing {@link Invoice}.
     */
    @Transactional
    public Invoice generateInvoiceForGuest(final Guest guest, final int month, final int year) {
        log.info("Generating invoice for guest ID: {} for month: {}/{}", guest.getId(), month, year);
        try {
            // Idempotency guard: return existing invoice if already generated.
            final Optional<Invoice> existing = invoiceRepository.findByGuestIdAndMonthAndYear(guest.getId(), month, year);
            if (existing.isPresent()) {
                log.info("Invoice already exists for guest {} for {}/{}", guest.getId(), month, year);
                return existing.get();
            }

            final YearMonth  ym          = YearMonth.of(year, month);
            final LocalDate  periodStart = ym.atDay(1);
            final LocalDate  periodEnd   = ym.atEndOfMonth();

            final List<InvoiceLineItem> lineItems = new ArrayList<>();
            BigDecimal                  total     = BigDecimal.ZERO;

            // ── 1. RENT (pro-rated if mid-month check-in) ─────────────────────────
            final BigDecimal rent = calculateProRatedRent(guest, ym);
            lineItems.add(InvoiceLineItem.builder()
                    .type(InvoiceLineType.RENT)
                    .description("Room Rent - " + ym.getMonth().name() + " " + year)
                    .amount(rent)
                    .build());
            total = total.add(rent);

            // ── 2. EB Share ───────────────────────────────────────────────────────
            BigDecimal ebShare = BigDecimal.ZERO;
            if (guest.getBed() != null && guest.getBed().getRoom().getBlock() != null) {
                final String          blockId  = guest.getBed().getRoom().getBlock().getId();
                final List<EbBillGuest> ebShares = ebBillGuestRepository.findByEbBill_BlockIdAndGuestId(blockId, guest.getId());
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

            // ── Resolve per-building pricing ──────────────────────────────────────
            final String buildingId = resolveBuildingId(guest);
            final PricingService.EffectivePricing pricing = pricingService.getEffectivePricing(buildingId);

            // ── 3. FOOD (skipped if food is included in rent) ─────────────────────
            BigDecimal foodTotal    = BigDecimal.ZERO;
            boolean    foodIncluded = systemConfig.getRules().isFoodIncludedInRent();
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

            // ── 4. LAUNDRY ────────────────────────────────────────────────────────
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

            // ── Persist invoice and line items ────────────────────────────────────
            final LocalDate dueDate = YearMonth.of(year, month).atDay(systemConfig.getRules().getPaymentDueDayOfMonth());
            final Invoice invoice = Invoice.builder()
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

            // ── Notify guest ──────────────────────────────────────────────────────
            final String message = buildInvoiceMessage(guest, savedInvoice, rent, ebShare, foodTotal, laundryTotal);
            notificationService.sendBoth(guest, message);

            log.info("Invoice generated for guest {} | Rent={} EB={} Food={} WM={} Total={}",
                    guest.getId(), rent, ebShare, foodTotal, laundryTotal, total);
            return savedInvoice;
        } catch (Exception e) {
            log.error("Error generating invoice for guest ID: {} for month: {}/{}", guest.getId(), month, year, e);
            throw e;
        }
    }

    /**
     * Computes a preview of a guest's monthly invoice without persisting any data.
     *
     * <p>Applies the same calculation logic as {@link #generateInvoiceForGuest} and
     * additionally checks whether an invoice already exists for the given period.
     * Used by the manager UI's "Preview Invoices" screen before running the billing job.</p>
     *
     * @param guest the {@link Guest} to compute the preview for.
     * @param month the billing month (1–12).
     * @param year  the billing year.
     * @return an {@link InvoicePreview} DTO with all computed amounts and metadata.
     */
    public InvoicePreview previewInvoice(final Guest guest, final int month, final int year) {
        final YearMonth ym          = YearMonth.of(year, month);
        final LocalDate periodStart = ym.atDay(1);
        final LocalDate periodEnd   = ym.atEndOfMonth();

        final String buildingId = resolveBuildingId(guest);
        final PricingService.EffectivePricing pricing = pricingService.getEffectivePricing(buildingId);

        final BigDecimal rent = calculateProRatedRent(guest, ym);

        BigDecimal ebShare = BigDecimal.ZERO;
        if (guest.getBed() != null && guest.getBed().getRoom().getBlock() != null) {
            final String            blockId  = guest.getBed().getRoom().getBlock().getId();
            final List<EbBillGuest> ebShares = ebBillGuestRepository.findByEbBill_BlockIdAndGuestId(blockId, guest.getId());
            ebShare = ebShares.stream()
                    .filter(s -> isWithinPeriod(s.getEbBill().getBillingPeriodStart(), periodStart, periodEnd))
                    .map(EbBillGuest::getShareAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal food         = BigDecimal.ZERO;
        boolean    foodIncluded = systemConfig.getRules().isFoodIncludedInRent();
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

        final Optional<Invoice> existingOpt      = invoiceRepository.findByGuestIdAndMonthAndYear(guest.getId(), month, year);
        final boolean           alreadyGenerated = existingOpt.isPresent();
        final String            invoiceId        = existingOpt.map(Invoice::getId).orElse(null);
        final String            status           = existingOpt.map(inv -> inv.getStatus().name()).orElse(null);

        final InvoicePreview preview = new InvoicePreview();
        preview.id               = invoiceId;
        preview.guestId          = guest.getId();
        preview.guestName        = guest.getFullName();
        preview.bedLabel         = guest.getBed() != null ? guest.getBed().getBedLabel() : "—";
        preview.roomNumber       = guest.getBed() != null ? guest.getBed().getRoom().getRoomNumber() : "—";
        preview.floor            = (guest.getBed() != null && guest.getBed().getRoom().getFloor() != null)
                                   ? guest.getBed().getRoom().getFloor().getFloorLabel() : "—";
        preview.rent             = rent;
        preview.ebShare          = ebShare;
        preview.food             = food;
        preview.laundry          = laundry;
        preview.total            = rent.add(ebShare).add(food).add(laundry);
        preview.alreadyGenerated = alreadyGenerated;
        preview.status           = status;
        return preview;
    }

    // ── Private Calculation Helpers ───────────────────────────────────────────

    /**
     * Resolves the building ID from a guest's bed → room → floor → building chain.
     *
     * @param guest the {@link Guest} whose building is being resolved.
     * @return the building UUID, or {@code null} if the guest has no assigned bed/room/floor.
     */
    private String resolveBuildingId(final Guest guest) {
        if (guest.getBed() != null && guest.getBed().getRoom() != null
                && guest.getBed().getRoom().getFloor() != null) {
            return guest.getBed().getRoom().getFloor().getBuilding().getId();
        }
        return null;
    }

    /**
     * Calculates the pro-rated rent for a guest for the given billing month.
     *
     * <p>If the guest checked in before the start of the billing month, the full rent is returned.
     * If the guest checked in mid-month, the rent is pro-rated as:
     * {@code baseRent × activeDays / totalDaysInMonth}.</p>
     *
     * @param guest the {@link Guest} whose rent is being calculated.
     * @param ym    the billing {@link YearMonth}.
     * @return the pro-rated or full rent amount, rounded to 2 decimal places.
     */
    private BigDecimal calculateProRatedRent(final Guest guest, final YearMonth ym) {
        BigDecimal baseRent = BigDecimal.ZERO;
        if (guest.getBed() != null) {
            final com.pgcrm.entity.Room room = guest.getBed().getRoom();
            baseRent = room.getBaseRent();
            if (guest.isBookEntireRoom()) {
                baseRent = baseRent.multiply(BigDecimal.valueOf(room.getSharingType()));
            }
        }
        final LocalDate checkIn = guest.getCheckInDate();
        if (checkIn == null) return baseRent;

        final int       totalDays    = ym.lengthOfMonth();
        final LocalDate monthStart   = ym.atDay(1);
        final LocalDate monthEnd     = ym.atEndOfMonth();

        if (checkIn.isAfter(monthEnd)) return BigDecimal.ZERO;

        final LocalDate effectiveStart = checkIn.isBefore(monthStart) ? monthStart : checkIn;
        final int       activeDays     = (int) (monthEnd.toEpochDay() - effectiveStart.toEpochDay()) + 1;

        if (activeDays >= totalDays) return baseRent;
        return baseRent.multiply(BigDecimal.valueOf(activeDays))
                       .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the total food and add-on charges for a guest over a date range.
     *
     * <p>Sums breakfast, lunch, dinner, omelette, and boiled-egg charges from all
     * {@link DailyLog} records in the given date range.</p>
     *
     * @param guest   the {@link Guest} whose food charges are being calculated.
     * @param start   the start date of the period (inclusive).
     * @param end     the end date of the period (inclusive).
     * @param pricing the effective pricing bundle for the building.
     * @return the total food and add-on amount.
     */
    private BigDecimal calculateFoodTotal(final Guest guest, final LocalDate start, final LocalDate end,
                                          final PricingService.EffectivePricing pricing) {
        final List<DailyLog> logs = dailyLogRepository.findByGuestIdAndLogDateBetween(guest.getId(), start, end);
        BigDecimal total = BigDecimal.ZERO;
        for (final DailyLog log : logs) {
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

    /**
     * Calculates the total laundry charges for a guest over a date range.
     *
     * <p>Sums {@link DailyLog#getWashingMachineCount()} across all logs in the range
     * and multiplies by the per-use washing machine price.</p>
     *
     * @param guest   the {@link Guest} whose laundry charges are being calculated.
     * @param start   the start date of the period (inclusive).
     * @param end     the end date of the period (inclusive).
     * @param pricing the effective pricing bundle for the building.
     * @return the total laundry amount.
     */
    private BigDecimal calculateLaundryTotal(final Guest guest, final LocalDate start, final LocalDate end,
                                              final PricingService.EffectivePricing pricing) {
        final List<DailyLog> logs      = dailyLogRepository.findByGuestIdAndLogDateBetween(guest.getId(), start, end);
        final int            totalUses = logs.stream().mapToInt(DailyLog::getWashingMachineCount).sum();
        return pricing.washingMachine().multiply(BigDecimal.valueOf(totalUses));
    }

    /**
     * Tests whether a given date falls within a billing period range (inclusive on both ends).
     *
     * @param billStart   the bill's start date to test.
     * @param periodStart the start of the target period (inclusive).
     * @param periodEnd   the end of the target period (inclusive).
     * @return {@code true} if {@code billStart} is within {@code [periodStart, periodEnd]}.
     */
    private boolean isWithinPeriod(final LocalDate billStart, final LocalDate periodStart, final LocalDate periodEnd) {
        return !billStart.isBefore(periodStart) && !billStart.isAfter(periodEnd);
    }

    /**
     * Builds the WhatsApp/in-app notification message body for a generated invoice
     * using the configured {@link SystemConfigProperties.Rules#getInvoiceWhatsappTemplate()}.
     *
     * @param g    the {@link Guest} being invoiced.
     * @param inv  the persisted {@link Invoice}.
     * @param rent the rent line-item amount.
     * @param eb   the EB share amount.
     * @param food the food total amount.
     * @param wm   the washing machine (laundry) total amount.
     * @return the rendered message string with all placeholders replaced.
     */
    private String buildInvoiceMessage(final Guest g, final Invoice inv, final BigDecimal rent,
                                       final BigDecimal eb, final BigDecimal food, final BigDecimal wm) {
        return systemConfig.getRules().getInvoiceWhatsappTemplate()
                .replace("{guestName}", g.getFullName())
                .replace("{month}",     inv.getMonth() + "/" + inv.getYear())
                .replace("{rent}",      "₹" + rent)
                .replace("{eb}",        "₹" + eb)
                .replace("{food}",      "₹" + food)
                .replace("{wm}",        "₹" + wm)
                .replace("{total}",     "₹" + inv.getTotalAmount())
                .replace("{dueDate}",   inv.getDueDate().toString());
    }
}
