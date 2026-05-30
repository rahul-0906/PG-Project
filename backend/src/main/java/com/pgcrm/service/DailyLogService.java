package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.*;
import com.pgcrm.repository.*;
import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.exception.InvalidLockoutException;
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
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + guestId));

        validateLockouts(guest, logDate, incoming);

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

    private void validateLockouts(Guest guest, LocalDate logDate, DailyLog incoming) {
        LocalDateTime now = LocalDateTime.now();

        java.time.LocalTime breakfastCutoffTime = systemConfig.getRules().getBreakfastLockoutTime();
        java.time.LocalTime dinnerCutoffTime = systemConfig.getRules().getDinnerLockoutTime();
        boolean isPreviousDay = true;

        if (guest.getBed() != null && guest.getBed().getRoom() != null && guest.getBed().getRoom().getFloor() != null) {
            Building building = guest.getBed().getRoom().getFloor().getBuilding();
            if (building != null && building.getBuildingConfig() != null) {
                BuildingConfig cfg = building.getBuildingConfig();
                if (cfg.getBreakfastCutoffTime() != null) {
                    breakfastCutoffTime = cfg.getBreakfastCutoffTime();
                }
                if (cfg.getDinnerCutoffTime() != null) {
                    dinnerCutoffTime = cfg.getDinnerCutoffTime();
                }
                isPreviousDay = cfg.isPreviousDay();
            }
        }

        // Fetch existing opt statuses to compare changes
        DailyLog existing = dailyLogRepository.findByGuestIdAndLogDate(guest.getId(), logDate).orElse(null);
        boolean existingBreakfast = existing != null ? existing.isBreakfastOpted() : guest.isBreakfastPreference();
        boolean existingLunch = existing != null ? existing.isLunchOpted() : guest.isLunchPreference();
        boolean existingDinner = existing != null ? existing.isDinnerOpted() : guest.isDinnerPreference();

        // Breakfast & Lunch lockout check
        LocalDate breakfastLockDate = isPreviousDay ? logDate.minusDays(1) : logDate;
        LocalDateTime breakfastLock = breakfastLockDate.atTime(breakfastCutoffTime);
        if (now.isAfter(breakfastLock)) {
            if (incoming.isBreakfastOpted() != existingBreakfast) {
                throw new InvalidLockoutException("Breakfast selection is locked after " + breakfastCutoffTime + (isPreviousDay ? " on the previous day." : " on the same day."));
            }
            if (incoming.isLunchOpted() != existingLunch) {
                throw new InvalidLockoutException("Lunch selection is locked after " + breakfastCutoffTime + (isPreviousDay ? " on the previous day." : " on the same day."));
            }
        }

        // Dinner lockout check
        LocalDateTime dinnerLock = logDate.atTime(dinnerCutoffTime);
        if (now.isAfter(dinnerLock)) {
            if (incoming.isDinnerOpted() != existingDinner) {
                throw new InvalidLockoutException("Dinner selection is locked after " + dinnerCutoffTime + " on the same day.");
            }
        }
    }

    public DailyLog createDefaultLog(Guest guest, LocalDate date) {
        boolean defBreakfast = guest != null && guest.isBreakfastPreference();
        boolean defLunch = guest != null && guest.isLunchPreference();
        boolean defDinner = guest != null && guest.isDinnerPreference();
        boolean defVeg = guest == null || guest.isVegPreference();
        
        return DailyLog.builder()
                .guest(guest)
                .logDate(date)
                .breakfastOpted(defBreakfast)
                .lunchOpted(defLunch)
                .dinnerOpted(defDinner)
                .isVeg(defVeg)
                .build();
    }

    public DailyLog getLog(String guestId, LocalDate logDate) {
        return dailyLogRepository.findByGuestIdAndLogDate(guestId, logDate)
                .orElseGet(() -> {
                    Guest guest = guestRepository.findById(guestId).orElse(null);
                    return createDefaultLog(guest, logDate);
                });
    }

    public java.util.List<DailyLog> getMonthlyLogs(String guestId, LocalDate start, LocalDate end) {
        java.util.List<DailyLog> existing = dailyLogRepository.findByGuestIdAndLogDateBetween(guestId, start, end);
        java.util.Map<LocalDate, DailyLog> map = new java.util.HashMap<>();
        for (DailyLog log : existing) {
            map.put(log.getLogDate(), log);
        }
        
        Guest guest = guestRepository.findById(guestId).orElse(null);
        java.util.List<DailyLog> result = new java.util.ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            DailyLog log = map.get(date);
            if (log == null) {
                log = createDefaultLog(guest, date);
            }
            result.add(log);
        }
        return result;
    }
}
