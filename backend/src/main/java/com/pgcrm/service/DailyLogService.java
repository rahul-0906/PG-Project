package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.Building;
import com.pgcrm.entity.BuildingConfig;
import com.pgcrm.entity.DailyLog;
import com.pgcrm.entity.Guest;
import com.pgcrm.exception.InvalidLockoutException;
import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.repository.DailyLogRepository;
import com.pgcrm.repository.GuestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for managing daily meal opt-in logs ({@link DailyLog}) for guests.
 *
 * <p>Provides three core capabilities:</p>
 * <ol>
 *   <li><strong>Upsert:</strong> Create or update a guest's daily log entry, enforcing
 *       per-building meal lockout cutoff times before allowing changes.</li>
 *   <li><strong>Default Log Creation:</strong> Synthesise a virtual log entry from a guest's
 *       stored meal preferences for dates where no explicit log exists.</li>
 *   <li><strong>Monthly Log Retrieval:</strong> Return a complete day-by-day log list for
 *       an entire month, filling gaps with preference-based defaults.</li>
 * </ol>
 *
 * <p><strong>Lockout Logic:</strong> The cutoff times are resolved in a layered precedence:
 * per-building {@link BuildingConfig} overrides the global {@link SystemConfigProperties}
 * YAML defaults. The {@code isPreviousDay} flag determines whether the breakfast cutoff
 * applies on the eve of the target date (arrears-billing model) or on the target date itself.</p>
 *
 * <p><strong>Manager-Exclusive Fields:</strong> The {@code isVeg}, {@code omeletteCount},
 * {@code boiledEggCount}, and {@code washingMachineCount} fields are intentionally NOT
 * updated by {@link #upsertLog}. These are managed exclusively by the PG Manager
 * via a separate manager API endpoint.</p>
 *
 * @see DailyLog
 * @see InvalidLockoutException
 * @see SystemConfigProperties
 */
@Service
@RequiredArgsConstructor
public class DailyLogService {

    private final DailyLogRepository       dailyLogRepository;
    private final GuestRepository          guestRepository;
    private final SystemConfigProperties   systemConfig;

    /**
     * Creates or updates a guest's daily log entry for the given date, enforcing meal lockouts.
     *
     * <p><strong>Upsert Semantics:</strong> If a log entry already exists for the
     * {@code (guestId, logDate)} pair it is updated in-place; otherwise a new entry
     * is created with the guest's preference defaults as the baseline.</p>
     *
     * <p><strong>Meal Update Guard:</strong> Meal selections ({@code breakfastOpted},
     * {@code lunchOpted}, {@code dinnerOpted}) are only overwritten when
     * {@link SystemConfigProperties.Rules#isAllowMealCancellations()} is {@code true},
     * or when this is the first log entry for the date (no existing record in the database).
     * This prevents guests from opting out of meals post-generation when cancellations
     * are disabled at the building level.</p>
     *
     * @param guestId  the UUID of the {@link Guest} whose log is being updated.
     * @param logDate  the calendar date the log applies to.
     * @param incoming a transient {@link DailyLog} instance carrying the desired meal opt states.
     * @return the saved {@link DailyLog} entity (either updated or newly created).
     * @throws ResourceNotFoundException if no guest exists for the given {@code guestId}.
     * @throws InvalidLockoutException   if any meal selection change is attempted after the
     *                                   relevant cutoff time has passed.
     */
    @Transactional
    public DailyLog upsertLog(final String guestId, final LocalDate logDate, final DailyLog incoming) {
        final Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + guestId));

        validateLockouts(guest, logDate, incoming);

        DailyLog log = dailyLogRepository.findByGuestIdAndLogDate(guestId, logDate)
                .orElse(DailyLog.builder().guest(guest).logDate(logDate).build());

        // Only update meal opts if cancellations are permitted or this is the first entry for the date.
        if (systemConfig.getRules().isAllowMealCancellations() || log.getId() == null) {
            if (systemConfig.getRules().isBreakfastEnabled()) log.setBreakfastOpted(incoming.isBreakfastOpted());
            if (systemConfig.getRules().isLunchEnabled())     log.setLunchOpted(incoming.isLunchOpted());
            if (systemConfig.getRules().isDinnerEnabled())    log.setDinnerOpted(incoming.isDinnerOpted());
        }

        // NOTE: isVeg, omeletteCount, boiledEggCount, and washingMachineCount are intentionally
        // NOT modified here — they are managed exclusively by the PG Manager via managerApi.updateGuestLog.

        return dailyLogRepository.save(log);
    }

    /**
     * Validates that the requested meal opt changes do not violate the active lockout cutoffs.
     *
     * <p><strong>Cutoff Resolution Precedence:</strong></p>
     * <ol>
     *   <li>Per-building {@link BuildingConfig} cutoff times (if the guest has an assigned bed
     *       and the building config is present).</li>
     *   <li>Global {@link SystemConfigProperties} YAML defaults.</li>
     * </ol>
     *
     * <p><strong>Lockout Rules:</strong></p>
     * <ul>
     *   <li>Breakfast &amp; Lunch share the same cutoff window. If {@code isPreviousDay} is
     *       {@code true} (arrears-billing model), the cutoff window closes on the <em>previous
     *       evening</em> relative to the target date. Otherwise it closes on the target date itself.</li>
     *   <li>Dinner has its own independent cutoff that always applies on the target date.</li>
     * </ul>
     *
     * @param guest    the {@link Guest} whose building config is used for cutoff resolution.
     * @param logDate  the target date being modified.
     * @param incoming the incoming meal selection values to compare against existing state.
     * @throws InvalidLockoutException if any meal selection is changed after its cutoff has passed.
     */
    private void validateLockouts(final Guest guest, final LocalDate logDate, final DailyLog incoming) {
        final LocalDateTime now = LocalDateTime.now();

        // Resolve cutoff times — start with YAML defaults, then override with building config.
        LocalTime breakfastCutoffTime = systemConfig.getRules().getBreakfastLockoutTime();
        LocalTime dinnerCutoffTime    = systemConfig.getRules().getDinnerLockoutTime();
        boolean   isPreviousDay       = true;

        if (guest.getBed() != null && guest.getBed().getRoom() != null
                && guest.getBed().getRoom().getFloor() != null) {
            final Building building = guest.getBed().getRoom().getFloor().getBuilding();
            if (building != null && building.getBuildingConfig() != null) {
                final BuildingConfig cfg = building.getBuildingConfig();
                if (cfg.getBreakfastCutoffTime() != null) breakfastCutoffTime = cfg.getBreakfastCutoffTime();
                if (cfg.getDinnerCutoffTime() != null)    dinnerCutoffTime    = cfg.getDinnerCutoffTime();
                isPreviousDay = cfg.isPreviousDay();
            }
        }

        // Fetch existing opt states to detect changes against a known baseline.
        final DailyLog existing     = dailyLogRepository.findByGuestIdAndLogDate(guest.getId(), logDate).orElse(null);
        final boolean existingBreakfast = existing != null ? existing.isBreakfastOpted() : guest.isBreakfastPreference();
        final boolean existingLunch     = existing != null ? existing.isLunchOpted()     : guest.isLunchPreference();
        final boolean existingDinner    = existing != null ? existing.isDinnerOpted()    : guest.isDinnerPreference();

        // ── Breakfast & Lunch lockout check ──────────────────────────────────
        final LocalDate breakfastLockDate = isPreviousDay ? logDate.minusDays(1) : logDate;
        final LocalDateTime breakfastLock = breakfastLockDate.atTime(breakfastCutoffTime);

        if (now.isAfter(breakfastLock)) {
            if (incoming.isBreakfastOpted() != existingBreakfast) {
                throw new InvalidLockoutException(
                        "Breakfast selection is locked after " + breakfastCutoffTime
                        + (isPreviousDay ? " on the previous day." : " on the same day."));
            }
            if (incoming.isLunchOpted() != existingLunch) {
                throw new InvalidLockoutException(
                        "Lunch selection is locked after " + breakfastCutoffTime
                        + (isPreviousDay ? " on the previous day." : " on the same day."));
            }
        }

        // ── Dinner lockout check ──────────────────────────────────────────────
        final LocalDateTime dinnerLock = logDate.atTime(dinnerCutoffTime);
        if (now.isAfter(dinnerLock) && incoming.isDinnerOpted() != existingDinner) {
            throw new InvalidLockoutException(
                    "Dinner selection is locked after " + dinnerCutoffTime + " on the same day.");
        }
    }

    /**
     * Creates an in-memory (unsaved) default {@link DailyLog} for a guest and date,
     * initialised from the guest's stored meal preference flags.
     *
     * <p>Used as a fallback when no persisted log entry exists for a given date —
     * for example, when rendering the meal calendar for future dates that the guest
     * has not yet explicitly opted into or out of.</p>
     *
     * @param guest the {@link Guest} whose preferences initialise the default log;
     *              may be {@code null} in which case all meal opts default to {@code false}
     *              and {@code isVeg} defaults to {@code true}.
     * @param date  the calendar date the default log represents.
     * @return an unsaved {@link DailyLog} entity pre-populated with preference defaults.
     */
    public DailyLog createDefaultLog(final Guest guest, final LocalDate date) {
        final boolean defBreakfast = guest != null && guest.isBreakfastPreference();
        final boolean defLunch     = guest != null && guest.isLunchPreference();
        final boolean defDinner    = guest != null && guest.isDinnerPreference();
        final boolean defVeg       = guest == null || guest.isVegPreference();

        return DailyLog.builder()
                .guest(guest)
                .logDate(date)
                .breakfastOpted(defBreakfast)
                .lunchOpted(defLunch)
                .dinnerOpted(defDinner)
                .isVeg(defVeg)
                .build();
    }

    /**
     * Retrieves the daily log entry for a specific guest and date.
     *
     * <p>If no persisted log exists for the given date, a preference-based default is
     * synthesised via {@link #createDefaultLog(Guest, LocalDate)} and returned without
     * being saved to the database.</p>
     *
     * @param guestId the UUID of the {@link Guest}.
     * @param logDate the calendar date to retrieve the log for.
     * @return the persisted {@link DailyLog} if it exists, or a virtual default log otherwise.
     */
    public DailyLog getLog(final String guestId, final LocalDate logDate) {
        return dailyLogRepository.findByGuestIdAndLogDate(guestId, logDate)
                .orElseGet(() -> {
                    final Guest guest = guestRepository.findById(guestId).orElse(null);
                    return createDefaultLog(guest, logDate);
                });
    }

    /**
     * Returns a complete, day-by-day list of daily logs for a guest over a specified date range.
     *
     * <p>For every calendar day in the range {@code [start, end]} (inclusive), the method
     * returns either the persisted log entry (if one exists) or a synthesised default entry
     * based on the guest's meal preferences. This ensures the caller always receives exactly
     * {@code (end - start + 1)} entries regardless of how many days have been explicitly opted.</p>
     *
     * @param guestId the UUID of the {@link Guest}.
     * @param start   the first date of the range (inclusive).
     * @param end     the last date of the range (inclusive).
     * @return a {@link List} of {@link DailyLog} entries, one per calendar day in the range.
     */
    public List<DailyLog> getMonthlyLogs(final String guestId, final LocalDate start, final LocalDate end) {
        final List<DailyLog> existing = dailyLogRepository.findByGuestIdAndLogDateBetween(guestId, start, end);

        // Index persisted entries by date for O(1) lookup during the day-by-day iteration.
        final Map<LocalDate, DailyLog> existingByDate = new HashMap<>();
        for (final DailyLog existingLog : existing) {
            existingByDate.put(existingLog.getLogDate(), existingLog);
        }

        final Guest guest = guestRepository.findById(guestId).orElse(null);
        final List<DailyLog> result = new ArrayList<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            final DailyLog dayLog = existingByDate.getOrDefault(date, createDefaultLog(guest, date));
            result.add(dayLog);
        }
        return result;
    }
}
