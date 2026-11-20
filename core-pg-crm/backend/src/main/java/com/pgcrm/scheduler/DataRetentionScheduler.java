package com.pgcrm.scheduler;

import com.pgcrm.multitenancy.TenantJobRunner;
import com.pgcrm.repository.GuestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Scheduled cron task to anonymize guest profiles under the database retention policy.
 * Runs daily across all active tenants in isolation to scrub expired guest data
 * (checked out more than 365 days ago).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataRetentionScheduler {

    private final GuestRepository guestRepository;
    private final TenantJobRunner tenantJobRunner;

    /**
     * Executes the guest data anonymization policy daily at 3:00 AM.
     * Calculated 1-year cutoff threshold is passed to bulk update.
     */
    @Scheduled(cron = "0 0 3 * * ?") // 3:00 AM daily
    public void anonymizeExpiredGuestProfiles() {
        LocalDate cutoffDate = LocalDate.now().minusYears(1);
        log.info("🧹 Data Retention Scheduler START — Threshold cutoff date: {}", cutoffDate);

        tenantJobRunner.runForActiveTenants("DataRetention", tenant -> {
            processTenantDataRetention(cutoffDate);
        });

        log.info("🧹 Data Retention Scheduler COMPLETE");
    }

    /**
     * Anonymizes expired guest records in the currently active tenant schema.
     * Idempotent: already anonymized records have anonymized=true and are not modified again.
     */
    public void processTenantDataRetention(LocalDate cutoffDate) {
        int anonymizedCount = guestRepository.anonymizeExpiredGuests(cutoffDate);
        log.info("🧹 Anonymized {} expired guest profile(s)", anonymizedCount);
    }
}
