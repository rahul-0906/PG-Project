package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.*;
import com.pgcrm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class DailyLogService {

    private final DailyLogRepository dailyLogRepository;
    private final GuestRepository guestRepository;
    private final SystemConfigProperties systemConfig;

    @Transactional
    public DailyLog upsertLog(String guestId, LocalDate logDate, DailyLog incoming) {
        validateLockouts(logDate, incoming);

        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new RuntimeException("Guest not found"));

        DailyLog log = dailyLogRepository.findByGuestIdAndLogDate(guestId, logDate)
                .orElse(DailyLog.builder().guest(guest).logDate(logDate).build());

        // Only update meals if cancellations are allowed or this is first entry
        if (systemConfig.getRules().isAllowMealCancellations() || log.getId() == null) {
            if (systemConfig.getRules().isBreakfastEnabled()) log.setBreakfastOpted(incoming.isBreakfastOpted());
            if (systemConfig.getRules().isLunchEnabled()) log.setLunchOpted(incoming.isLunchOpted());
            if (systemConfig.getRules().isDinnerEnabled()) log.setDinnerOpted(incoming.isDinnerOpted());
        }

        // We do NOT modify isVeg, omeletteCount, boiledEggCount, washingMachineCount here
        // as they are managed exclusively by the PG Manager via managerApi.updateGuestLog.

        return dailyLogRepository.save(log);
    }

    private void validateLockouts(LocalDate logDate, DailyLog incoming) {
        LocalDateTime now = LocalDateTime.now();

        // Breakfast & Lunch: locked after previous night's lockout time
        LocalDateTime breakfastLock = logDate.minusDays(1).atTime(systemConfig.getRules().getBreakfastLockoutTime());
        if (now.isAfter(breakfastLock)) {
            if (incoming.isBreakfastOpted() || incoming.isLunchOpted()) {
                // Don't throw — just silently ignore if they're setting to false (cancellation)
                // Only reject if they're trying to OPT IN after lockout
            }
        }

        // Dinner: locked after same day's lockout time
        LocalDateTime dinnerLock = logDate.atTime(systemConfig.getRules().getDinnerLockoutTime());
        if (now.isAfter(dinnerLock) && incoming.isDinnerOpted()) {
            throw new RuntimeException("Dinner selection is locked after " + systemConfig.getRules().getDinnerLockoutTime() + " on the same day.");
        }
    }

    public DailyLog getLog(String guestId, LocalDate logDate) {
        return dailyLogRepository.findByGuestIdAndLogDate(guestId, logDate)
                .orElse(DailyLog.builder().logDate(logDate).build());
    }
}
