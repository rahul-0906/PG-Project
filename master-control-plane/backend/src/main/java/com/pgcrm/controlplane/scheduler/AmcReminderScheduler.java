package com.pgcrm.controlplane.scheduler;

import com.pgcrm.controlplane.entity.LicenseState;
import com.pgcrm.controlplane.entity.Subscription;
import com.pgcrm.controlplane.entity.TenantStatus;
import com.pgcrm.controlplane.repository.SubscriptionRepository;
import com.pgcrm.controlplane.repository.TenantInstanceRepository;
import com.pgcrm.controlplane.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AmcReminderScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final TenantInstanceRepository tenantInstanceRepository;
    private final EmailService emailService;

    /**
     * Executes daily at 2:00 AM to fetch and process expiring subscriptions.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void checkAmcExpirations() {
        log.info("Starting Daily AMC Expiration Check task...");

        LocalDate today = LocalDate.now();
        LocalDate d30 = today.plusDays(30);
        LocalDate d7 = today.plusDays(7);
        LocalDate d1 = today.plusDays(1);

        // Fetch subscriptions expiring on target date milestones
        processRemindersForDate(d30, 30);
        processRemindersForDate(d7, 7);
        processRemindersForDate(d1, 1);

        // Handle active subscriptions that passed end_date without renewal
        handleExpiredContracts(today);

        log.info("Daily AMC Expiration Check completed.");
    }

    private void processRemindersForDate(LocalDate targetDate, int daysRemaining) {
        List<Subscription> expiring = subscriptionRepository.findActiveExpiringOn(targetDate);
        log.info("Found {} subscription(s) expiring in {} days (Date: {})", expiring.size(), daysRemaining, targetDate);

        for (Subscription sub : expiring) {
            try {
                emailService.sendAmcRenewalReminderEmail(sub, daysRemaining);
                log.info("Dispatched {}-day renewal reminder email to tenant client: {}", 
                        daysRemaining, sub.getTenantInstance().getClient().getEmail());
            } catch (Exception e) {
                log.error("Failed to send AMC renewal email for tenant: {}. Reason: {}", 
                        sub.getTenantInstance().getDomainName(), e.getMessage());
            }
        }
    }

    private void handleExpiredContracts(LocalDate today) {
        List<Subscription> expired = subscriptionRepository.findActiveExpiredBefore(today);
        log.info("Found {} active subscription(s) expired before {}", expired.size(), today);

        for (Subscription sub : expired) {
            log.warn("Subscription for tenant {} has expired on {}", sub.getTenantInstance().getDomainName(), sub.getAmcExpiryDate());
            
            sub.setLicenseState(LicenseState.EXPIRED);
            sub.getTenantInstance().setStatus(TenantStatus.SUSPENDED);
            
            subscriptionRepository.save(sub);
            tenantInstanceRepository.save(sub.getTenantInstance());
            
            // Notify tenant owner of service suspension
            try {
                emailService.sendServiceSuspensionEmail(sub);
                log.info("Dispatched suspension email to tenant client: {}", sub.getTenantInstance().getClient().getEmail());
            } catch (Exception e) {
                log.error("Failed to send suspension email for tenant: {}. Reason: {}", 
                        sub.getTenantInstance().getDomainName(), e.getMessage());
            }
        }
    }
}
