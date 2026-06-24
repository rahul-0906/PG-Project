package com.pgcrm.controlplane.service;

import com.pgcrm.controlplane.dto.TenantOnboardingRequest;
import com.pgcrm.controlplane.model.entity.TenantProfile;
import com.pgcrm.controlplane.model.entity.TenantSubscription;
import com.pgcrm.controlplane.model.enums.PaymentStatus;
import com.pgcrm.controlplane.model.enums.TenantStatus;
import com.pgcrm.controlplane.repository.TenantProfileRepository;
import com.pgcrm.controlplane.repository.TenantSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantOnboardingServiceImpl implements TenantOnboardingService {

    private final TenantProfileRepository tenantProfileRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;

    @Override
    @Transactional
    public void processOnboarding(TenantOnboardingRequest request, UUID currentUserId) {
        log.info("Processing onboarding request for user ID: {} and domain: {}", currentUserId, request.getCustomDomain());

        // 1. Check if a profile already exists for currentUserId
        if (tenantProfileRepository.findByOwnerUserId(currentUserId).isPresent()) {
            log.warn("Onboarding failed: Tenant profile already exists for user ID: {}", currentUserId);
            throw new IllegalStateException("A tenant profile already exists for this user.");
        }

        // 2. Build and save the TenantProfile
        TenantProfile profile = TenantProfile.builder()
                .ownerUserId(currentUserId)
                .pgName(request.getPgName())
                .pgShortTitle(request.getPgShortTitle())
                .customDomain(request.getCustomDomain())
                .routerIp(request.getRouterIp())
                .whatsappNumber(request.getWhatsappNumber())
                .contactEmail(request.getContactEmail())
                .razorpayKey(request.getRazorpayKey())
                .razorpaySecret(request.getRazorpaySecret())
                .themeConfig(request.getThemeConfig())
                .status(TenantStatus.PENDING_PAYMENT)
                .build();

        profile = tenantProfileRepository.save(profile);
        log.info("Saved new TenantProfile with ID: {}", profile.getId());

        // 3. Build and save the TenantSubscription
        TenantSubscription subscription = TenantSubscription.builder()
                .tenant(profile)
                .planType(request.getPlanType())
                .amcFee(request.getAmcFee())
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        tenantSubscriptionRepository.save(subscription);
        log.info("Saved new TenantSubscription for tenant profile ID: {}", profile.getId());
    }
}
