package com.pgcrm.scheduler;

import com.pgcrm.repository.GuestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Scheduled cron task to anonymize guest profiles under the database retention policy.
 * Runs daily to scrub expired guest data (checked out more than 365 days ago).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataRetentionScheduler {

    private final GuestRepository guestRepository;

    /**
     * Executes the guest data anonymization policy daily at 3:00 AM.
     * Calculated 1-year cutoff threshold is passed to bulk update.
     */
    @Scheduled(cron = "0 0 3 * * ?") // 3:00 AM daily
    @Transactional
    public void anonymizeExpiredGuestProfiles() {
        log.info("🧹 Data Retention Scheduler START — Checking for expired guest profiles to anonymize");
        try {
            LocalDate cutoffDate = LocalDate.now().minusYears(1);
            log.info("Calculating cutoff date threshold: {}", cutoffDate);

            int anonymizedCount = guestRepository.anonymizeExpiredGuests(cutoffDate);

            log.info("🧹 Data Retention Scheduler SUCCESS — Total profiles anonymized: {}", anonymizedCount);
        } catch (Exception e) {
            log.error("🧹 Data Retention Scheduler ERROR — Failed to process data retention policy: {}", e.getMessage(), e);
        }
    }
}
