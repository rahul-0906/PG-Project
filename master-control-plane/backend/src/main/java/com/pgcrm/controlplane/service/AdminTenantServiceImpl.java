package com.pgcrm.controlplane.service;

import com.pgcrm.controlplane.dto.TenantSummaryResponse;
import com.pgcrm.controlplane.model.entity.TenantProfile;
import com.pgcrm.controlplane.model.entity.TenantSubscription;
import com.pgcrm.controlplane.repository.TenantProfileRepository;
import com.pgcrm.controlplane.repository.TenantSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTenantServiceImpl implements AdminTenantService {

    private final TenantProfileRepository tenantProfileRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final TenantProvisioningWorker tenantProvisioningWorker;

    @Override
    @Transactional(readOnly = true)
    public List<TenantSummaryResponse> getAllTenantsSummary() {
        log.info("Fetching all tenants summary sorted by newest first");
        
        // Fetch profiles sorted by createdAt newest first
        List<TenantProfile> profiles = tenantProfileRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        return profiles.stream().map(profile -> {
            // Fetch subscription if present
            TenantSubscription subscription = tenantSubscriptionRepository.findByTenantId(profile.getId()).orElse(null);
            
            return TenantSummaryResponse.builder()
                    .tenantId(profile.getId())
                    .pgName(profile.getPgName())
                    .customDomain(profile.getCustomDomain())
                    .contactEmail(profile.getContactEmail())
                    .status(profile.getStatus())
                    .planType(subscription != null ? subscription.getPlanType() : null)
                    .paymentStatus(subscription != null ? subscription.getPaymentStatus() : null)
                    .createdAt(profile.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateTenantStatus(java.util.UUID tenantId, com.pgcrm.controlplane.model.enums.TenantStatus newStatus) {
        log.info("Updating tenant status for profile ID: {} to {}", tenantId, newStatus);
        TenantProfile profile = tenantProfileRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant profile not found for ID: " + tenantId));
        profile.setStatus(newStatus);
        tenantProfileRepository.save(profile);

        if (newStatus == com.pgcrm.controlplane.model.enums.TenantStatus.PROVISIONING) {
            tenantProvisioningWorker.executeProvisioningPipeline(tenantId);
        }
    }
}
