package com.pgcrm.controlplane.service;

import com.pgcrm.controlplane.entity.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    public void sendAmcRenewalReminderEmail(Subscription subscription, int daysRemaining) {
        log.info("Sending AMC renewal reminder email to {} for brand {} (Expires in {} days on {})",
                subscription.getTenantInstance().getClient().getEmail(),
                subscription.getTenantInstance().getClient().getPgBrandName(),
                daysRemaining,
                subscription.getAmcExpiryDate());
        
        // Method stub for sending emails via SMTP / Spring Mail Sender
    }

    public void sendServiceSuspensionEmail(Subscription subscription) {
        log.warn("Sending service suspension notice to {} for brand {} (Expired on {})",
                subscription.getTenantInstance().getClient().getEmail(),
                subscription.getTenantInstance().getClient().getPgBrandName(),
                subscription.getAmcExpiryDate());

        // Method stub for sending suspension emails
    }
}
