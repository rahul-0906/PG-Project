package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.Bed;
import com.pgcrm.entity.BuildingConfig;
import com.pgcrm.entity.DailyLog;
import com.pgcrm.entity.Guest;
import com.pgcrm.entity.User;
import com.pgcrm.entity.enums.BedStatus;
import com.pgcrm.repository.BedRepository;
import com.pgcrm.repository.BuildingConfigRepository;
import com.pgcrm.repository.DailyLogRepository;
import com.pgcrm.repository.EbBillGuestRepository;
import com.pgcrm.repository.GuestRepository;
import com.pgcrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Service responsible for managing the guest departure lifecycle — notice registration
 * and final settlement calculation with pro-rated billing.
 *
 * <p><strong>Two-Phase Checkout:</strong></p>
 * <ol>
 *   <li><strong>{@link #initiateCheckout(String)} — Notice Phase:</strong>
 *       Sets the guest's notice date and computes the expected exit date based on the
 *       configured notice period. The guest remains active and billed normally until
 *       the exit date. A departure summary notification is dispatched via
 *       {@link NotificationService#sendBoth(Guest, String)}.</li>
 *   <li><strong>{@link #confirmCheckout(String)} — Final Settlement Phase:</strong>
 *       Calculates the pro-rated settlement (rent, food, laundry for the partial month
 *       minus the advance deposit), deactivates the guest account and associated user,
 *       vacates the bed, and dispatches a final settlement summary notification.</li>
 * </ol>
 *
 * <p><strong>Pro-Rated Rent Calculation:</strong> The checkout rent is calculated as
 * {@code baseRent × daysStayed / daysInMonth}, where {@code daysStayed} is the day-of-month
 * at checkout (a full-month equivalent to the first of the month = 1 day).</p>
 *
 * <p><strong>Food &amp; Laundry Resolution:</strong> Whether food and laundry are charged
 * follows the same layered config precedence used in {@code InvoiceService}: building-level
 * {@link BuildingConfig} overrides the YAML global defaults.</p>
 *
 * @see SettlementResult
 * @see NotificationService
 * @see PricingService
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final GuestRepository          guestRepository;
    private final DailyLogRepository       dailyLogRepository;
    private final EbBillGuestRepository    ebBillGuestRepository;
    private final SystemConfigProperties   systemConfig;
    private final NotificationService      notificationService;
    private final BedRepository            bedRepository;
    private final BuildingConfigRepository buildingConfigRepository;
    private final PricingService           pricingService;
    private final UserRepository           userRepository;

    /**
     * Registers a checkout notice for the given guest, setting the notice date and
     * computing the expected exit date.
     *
     * <p>The expected exit date is calculated as:
     * {@code today + noticePeriodDays} (configured in {@link SystemConfigProperties.Rules#getNoticePeriodDays()}).
     * After calling this method, the guest remains fully active and continues to be
     * billed until the exit date. The manager triggers {@link #confirmCheckout(String)}
     * on the actual departure day.</p>
     *
     * <p>A departure notice notification is dispatched via {@link NotificationService#sendBoth}
     * to the guest's in-app inbox and WhatsApp number.</p>
     *
     * @param guestId the UUID of the {@link Guest} initiating checkout.
     * @return the updated {@link Guest} entity with notice and exit dates set.
     * @throws RuntimeException if no guest is found for the given {@code guestId}.
     */
    @Transactional
    public Guest initiateCheckout(final String guestId) {
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new RuntimeException("Guest not found"));

        final LocalDate today = LocalDate.now();
        guest.setNoticeDate(today);
        guest.setExitDate(today.plusDays(systemConfig.getRules().getNoticePeriodDays()));
        guest = guestRepository.save(guest);

        final String msg = String.format(
                "Dear %s, your checkout notice has been registered. "
                + "Notice period: %d days. Expected exit date: %s.",
                guest.getFullName(),
                systemConfig.getRules().getNoticePeriodDays(),
                guest.getExitDate());
        notificationService.sendBoth(guest, msg);
        return guest;
    }

    /**
     * Performs the final checkout and settlement calculation for the given guest.
     *
     * <p>The settlement computation:</p>
     * <ol>
     *   <li><strong>Pro-rated Rent:</strong> {@code baseRent × dayOfMonth / daysInMonth},
     *       where {@code dayOfMonth} is today's date within the month.</li>
     *   <li><strong>Pending Food:</strong> Summed from all daily logs in the current month
     *       up to today's date. Skipped if {@code foodIncludedInRent = true}.</li>
     *   <li><strong>Pending Laundry:</strong> Summed from washing machine log counts in the
     *       current month. Skipped if {@code hasWashingMachine = false}.</li>
     *   <li><strong>Net Settlement:</strong> {@code advanceDeposit - (proratedRent + food + laundry)}.
     *       A positive value means money is returned to the guest; negative means they owe more.</li>
     * </ol>
     *
     * <p>On completion:</p>
     * <ul>
     *   <li>The guest is deactivated ({@code active = false}) and checkout dates are set.</li>
     *   <li>The guest's bed is set to {@link BedStatus#VACANT} and the bed reference is cleared.</li>
     *   <li>The associated {@link User} account is deactivated.</li>
     *   <li>A detailed settlement summary notification is dispatched to the guest.</li>
     * </ul>
     *
     * @param guestId the UUID of the {@link Guest} being checked out.
     * @return a {@link SettlementResult} containing all computed amounts.
     * @throws RuntimeException if no guest is found for the given {@code guestId}.
     */
    @Transactional
    public SettlementResult confirmCheckout(final String guestId) {
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new RuntimeException("Guest not found"));

        final LocalDate  today        = LocalDate.now();
        final YearMonth  currentMonth = YearMonth.now();

        // ── 1. Pro-rated rent ─────────────────────────────────────────────────
        BigDecimal baseRent = BigDecimal.ZERO;
        if (guest.getBed() != null) {
            final com.pgcrm.entity.Room room = guest.getBed().getRoom();
            baseRent = room.getBaseRent();
            if (guest.isBookEntireRoom()) {
                baseRent = baseRent.multiply(BigDecimal.valueOf(room.getSharingType()));
            }
        }
        final int        daysInMonth  = currentMonth.lengthOfMonth();
        final long       daysStayed   = today.getDayOfMonth();
        final BigDecimal proratedRent = baseRent
                .multiply(BigDecimal.valueOf(daysStayed))
                .divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);

        // ── 2. Pending food & laundry this month ──────────────────────────────
        final LocalDate      monthStart = currentMonth.atDay(1);
        final List<DailyLog> logs       = dailyLogRepository.findByGuestIdAndLogDateBetween(
                guest.getId(), monthStart, today);

        final String buildingId = (guest.getBed() != null && guest.getBed().getRoom() != null
                && guest.getBed().getRoom().getFloor() != null)
                ? guest.getBed().getRoom().getFloor().getBuilding().getId() : null;
        final PricingService.EffectivePricing pricing = pricingService.getEffectivePricing(buildingId);

        // Resolve whether food is included — building config overrides YAML default.
        boolean foodIncluded = systemConfig.getRules().isFoodIncludedInRent();
        if (buildingId != null) {
            foodIncluded = buildingConfigRepository.findById(buildingId)
                    .map(BuildingConfig::isFoodIncludedInRent)
                    .orElse(foodIncluded);
        }

        BigDecimal pendingFood    = BigDecimal.ZERO;
        BigDecimal pendingLaundry = BigDecimal.ZERO;

        if (!foodIncluded) {
            for (final DailyLog log : logs) {
                if (log.isBreakfastOpted()) pendingFood = pendingFood.add(pricing.breakfast());
                if (log.isLunchOpted())     pendingFood = pendingFood.add(pricing.lunch());
                if (log.isDinnerOpted())    pendingFood = pendingFood.add(pricing.dinner());
                pendingFood = pendingFood.add(pricing.omelette().multiply(BigDecimal.valueOf(log.getOmeletteCount())));
                pendingFood = pendingFood.add(pricing.boiledEgg().multiply(BigDecimal.valueOf(log.getBoiledEggCount())));
            }
        }

        if (systemConfig.getRules().isHasWashingMachine()) {
            final int wmUses = logs.stream().mapToInt(DailyLog::getWashingMachineCount).sum();
            pendingLaundry   = pricing.washingMachine().multiply(BigDecimal.valueOf(wmUses));
        }

        // ── 3. Net settlement ─────────────────────────────────────────────────
        final BigDecimal totalDue   = proratedRent.add(pendingFood).add(pendingLaundry);
        final BigDecimal settlement = guest.getAdvanceDeposit().subtract(totalDue);

        // ── 4. Deactivate guest, user, and vacate bed ─────────────────────────
        guest.setActive(false);
        guest.setActualCheckOutDate(today);
        guest.setExpectedCheckOutDate(null);
        guest.setNoticeDate(null);
        guest.setExitDate(null);
        if (guest.getBed() != null) {
            final Bed bed = guest.getBed();
            if (guest.isBookEntireRoom()) {
                final List<Bed> roomBeds = bedRepository.findByRoomId(bed.getRoom().getId());
                for (final Bed b : roomBeds) {
                    b.setStatus(BedStatus.VACANT);
                    bedRepository.save(b);
                }
            } else {
                bed.setStatus(BedStatus.VACANT);
                bedRepository.save(bed);
            }
            guest.setBed(null);
        }
        guestRepository.save(guest);

        final User user = guest.getUser();
        if (user != null) {
            user.setActive(false);
            userRepository.save(user);
        }

        // ── 5. Dispatch settlement summary notification ───────────────────────
        final String msg = String.format(
                "Dear %s, your settlement summary:\n"
                + "Pro-rated Rent: ₹%s | Food: ₹%s | Laundry: ₹%s\n"
                + "Total Due: ₹%s | Advance Paid: ₹%s | Settlement: ₹%s\n%s",
                guest.getFullName(), proratedRent, pendingFood, pendingLaundry,
                totalDue, guest.getAdvanceDeposit(), settlement,
                settlement.compareTo(BigDecimal.ZERO) >= 0
                        ? "You will receive ₹" + settlement + " back."
                        : "You owe ₹" + settlement.abs() + " additionally.");
        notificationService.sendBoth(guest, msg);

        return new SettlementResult(proratedRent, pendingFood, pendingLaundry,
                totalDue, guest.getAdvanceDeposit(), settlement);
    }

    // ── Nested Record ─────────────────────────────────────────────────────────

    /**
     * Immutable value object carrying the results of a guest settlement calculation.
     *
     * <p>Returned by {@link #confirmCheckout(String)} and serialised to JSON by the
     * settlement controller endpoint for display in the manager's checkout summary UI.</p>
     *
     * @param proratedRent    the pro-rated rent amount for the partial month.
     * @param pendingFood     the total pending food and add-on charges for the month.
     * @param pendingLaundry  the total pending laundry charges for the month.
     * @param totalDue        the total amount owed by the guest ({@code rent + food + laundry}).
     * @param advanceDeposit  the advance security deposit paid at check-in.
     * @param settlementAmount the net settlement: positive means refund to guest,
     *                         negative means guest owes the difference.
     */
    public record SettlementResult(
            BigDecimal proratedRent,
            BigDecimal pendingFood,
            BigDecimal pendingLaundry,
            BigDecimal totalDue,
            BigDecimal advanceDeposit,
            BigDecimal settlementAmount
    ) {}
}
