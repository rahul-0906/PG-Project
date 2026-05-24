package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.BedStatus;
import com.pgcrm.entity.enums.InvoiceLineType;
import com.pgcrm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final GuestRepository guestRepository;
    private final DailyLogRepository dailyLogRepository;
    private final EbBillGuestRepository ebBillGuestRepository;
    private final SystemConfigProperties systemConfig;
    private final NotificationService notificationService;
    private final BedRepository bedRepository;

    @Transactional
    public Guest initiateCheckout(String guestId) {
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new RuntimeException("Guest not found"));

        LocalDate today = LocalDate.now();
        guest.setNoticeDate(today);
        guest.setExitDate(today.plusDays(systemConfig.getRules().getNoticePeriodDays()));
        guest = guestRepository.save(guest);

        String msg = String.format(
            "Dear %s, your checkout notice has been registered. " +
            "Notice period: %d days. Expected exit date: %s.",
            guest.getFullName(), systemConfig.getRules().getNoticePeriodDays(), guest.getExitDate());
        notificationService.sendBoth(guest, msg);
        return guest;
    }

    @Transactional
    public SettlementResult confirmCheckout(String guestId) {
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new RuntimeException("Guest not found"));

        LocalDate today = LocalDate.now();
        LocalDate checkIn = guest.getCheckInDate();
        YearMonth currentMonth = YearMonth.now();

        // Pro-rated rent for current month
        BigDecimal baseRent = guest.getBed() != null
                ? guest.getBed().getRoom().getBaseRent() : BigDecimal.ZERO;
        int daysInMonth = currentMonth.lengthOfMonth();
        long daysStayed = today.getDayOfMonth();
        BigDecimal proratedRent = baseRent
                .multiply(BigDecimal.valueOf(daysStayed))
                .divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);

        // Pending food & laundry logs this month
        LocalDate monthStart = currentMonth.atDay(1);
        List<DailyLog> logs = dailyLogRepository.findByGuestIdAndLogDateBetween(guest.getId(), monthStart, today);
        BigDecimal pendingFood = BigDecimal.ZERO;
        BigDecimal pendingLaundry = BigDecimal.ZERO;

        if (!systemConfig.getRules().isFoodIncludedInRent()) {
            for (DailyLog log : logs) {
                if (log.isBreakfastOpted()) pendingFood = pendingFood.add(systemConfig.getPricing().getBreakfast());
                if (log.isLunchOpted()) pendingFood = pendingFood.add(systemConfig.getPricing().getLunch());
                if (log.isDinnerOpted()) pendingFood = pendingFood.add(systemConfig.getPricing().getDinner());
                pendingFood = pendingFood.add(systemConfig.getPricing().getOmelette().multiply(BigDecimal.valueOf(log.getOmeletteCount())));
                pendingFood = pendingFood.add(systemConfig.getPricing().getBoiledEgg().multiply(BigDecimal.valueOf(log.getBoiledEggCount())));
            }
        }

        if (systemConfig.getRules().isHasWashingMachine()) {
            int wmUses = logs.stream().mapToInt(DailyLog::getWashingMachineCount).sum();
            pendingLaundry = systemConfig.getPricing().getWashingMachine().multiply(BigDecimal.valueOf(wmUses));
        }

        BigDecimal totalDue = proratedRent.add(pendingFood).add(pendingLaundry);
        BigDecimal settlement = guest.getAdvanceDeposit().subtract(totalDue);

        // Update guest status
        guest.setActive(false);
        guest.setActualCheckOutDate(today);
        if (guest.getBed() != null) {
            Bed bed = guest.getBed();
            bed.setStatus(BedStatus.VACANT);
            bedRepository.save(bed);
            guest.setBed(null);
        }
        guestRepository.save(guest);

        String msg = String.format(
            "Dear %s, your settlement summary:\n" +
            "Pro-rated Rent: ₹%s | Food: ₹%s | Laundry: ₹%s\n" +
            "Total Due: ₹%s | Advance Paid: ₹%s | Settlement: ₹%s\n" +
            "%s",
            guest.getFullName(), proratedRent, pendingFood, pendingLaundry,
            totalDue, guest.getAdvanceDeposit(), settlement,
            settlement.compareTo(BigDecimal.ZERO) >= 0
                ? "You will receive ₹" + settlement + " back."
                : "You owe ₹" + settlement.abs() + " additionally.");
        notificationService.sendBoth(guest, msg);

        return new SettlementResult(proratedRent, pendingFood, pendingLaundry, totalDue,
                guest.getAdvanceDeposit(), settlement);
    }

    public record SettlementResult(
            BigDecimal proratedRent,
            BigDecimal pendingFood,
            BigDecimal pendingLaundry,
            BigDecimal totalDue,
            BigDecimal advanceDeposit,
            BigDecimal settlementAmount
    ) {}
}
