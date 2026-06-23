package com.pgcrm.controlplane.service;

import com.pgcrm.controlplane.dto.ClientDetailResponse;
import com.pgcrm.controlplane.dto.DashboardMetricsResponse;
import com.pgcrm.controlplane.entity.LicenseState;
import com.pgcrm.controlplane.entity.Subscription;
import com.pgcrm.controlplane.entity.TenantInstance;
import com.pgcrm.controlplane.entity.TenantStatus;
import com.pgcrm.controlplane.repository.ClientRepository;
import com.pgcrm.controlplane.repository.SubscriptionRepository;
import com.pgcrm.controlplane.repository.TenantInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final ClientRepository clientRepository;
    private final TenantInstanceRepository tenantInstanceRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardMetricsResponse getMetrics() {
        log.info("Calculating SaaS administration metrics...");

        long activeClients = clientRepository.countActiveClients(TenantStatus.ACTIVE);
        long pendingDeployments = tenantInstanceRepository.countByStatus(TenantStatus.PENDING_DEPLOYMENT);
        
        LocalDate today = LocalDate.now();
        LocalDate expiryLimit = today.plusDays(30);
        long upcomingExpirations = subscriptionRepository.countUpcomingExpirations(today, expiryLimit);

        log.info("Metrics calculated: Active Clients={}, Pending Deployments={}, Upcoming Expirations={}",
                activeClients, pendingDeployments, upcomingExpirations);

        return DashboardMetricsResponse.builder()
                .totalActiveClients(activeClients)
                .pendingDeployments(pendingDeployments)
                .upcomingAmcExpirations(upcomingExpirations)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientDetailResponse> getClients() {
        log.info("Fetching consolidated list of B2B clients...");

        return clientRepository.findAllClientsWithDetails().stream()
                .map(client -> {
                    List<ClientDetailResponse.TenantInstanceDetail> instanceDetails = client.getTenantInstances().stream()
                            .map(instance -> {
                                Subscription sub = instance.getSubscription();
                                return ClientDetailResponse.TenantInstanceDetail.builder()
                                        .tenantId(instance.getId())
                                        .domainName(instance.getDomainName())
                                        .vpsIpAddress(instance.getVpsIpAddress())
                                        .allocatedPort(instance.getAllocatedPort())
                                        .status(instance.getStatus().name())
                                        .amcStartDate(sub != null ? sub.getAmcStartDate() : null)
                                        .amcExpiryDate(sub != null ? sub.getAmcExpiryDate() : null)
                                        .licenseState(sub != null ? sub.getLicenseState().name() : null)
                                        .setupFeePaid(sub != null && sub.getSetupFeePaid())
                                        .whatsappToken(instance.getWhatsappToken())
                                        .razorpayKeyId(instance.getRazorpayKeyId())
                                        .razorpayKeySecret(instance.getRazorpayKeySecret())
                                        .primaryColor(instance.getPrimaryColor())
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return ClientDetailResponse.builder()
                            .clientId(client.getId())
                            .ownerName(client.getOwnerName())
                            .email(client.getEmail())
                            .phone(client.getPhone())
                            .pgBrandName(client.getPgBrandName())
                            .instances(instanceDetails)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void activateTenant(UUID tenantId) {
        log.info("Manual activation request for tenant ID: {}", tenantId);

        TenantInstance tenant = tenantInstanceRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant instance not found for ID: " + tenantId));

        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            log.warn("Tenant {} is already ACTIVE. Skipping manual activation.", tenant.getDomainName());
            return;
        }

        if (tenant.getStatus() != TenantStatus.PENDING_DEPLOYMENT) {
            log.warn("Tenant {} status is currently {}, but activation was triggered. Proceeding overrides.", 
                    tenant.getDomainName(), tenant.getStatus());
        }

        // 1. Manually promote TenantInstance status to ACTIVE
        tenant.setStatus(TenantStatus.ACTIVE);
        
        // Ensure production IP and port are mapped if missing
        if (tenant.getVpsIpAddress() == null) {
            tenant.setVpsIpAddress("159.65.148.22"); // Production server IP reference
        }
        if (tenant.getAllocatedPort() == null) {
            tenant.setAllocatedPort(8080);
        }
        tenantInstanceRepository.save(tenant);

        // 2. Ensure active Subscription exists
        Subscription subscription = tenant.getSubscription();
        if (subscription == null) {
            log.info("Initializing new subscription AMC contract for tenant: {}", tenant.getDomainName());
            subscription = Subscription.builder()
                    .tenantInstance(tenant)
                    .setupFeePaid(true)
                    .amcStartDate(LocalDate.now())
                    .amcExpiryDate(LocalDate.now().plusYears(1))
                    .licenseState(LicenseState.ACTIVE)
                    .build();
        } else {
            log.info("Promoting subscription AMC contract for tenant: {} to ACTIVE", tenant.getDomainName());
            subscription.setLicenseState(LicenseState.ACTIVE);
            if (subscription.getAmcStartDate() == null) {
                subscription.setAmcStartDate(LocalDate.now());
                subscription.setAmcExpiryDate(LocalDate.now().plusYears(1));
            }
        }
        subscriptionRepository.save(subscription);

        log.info("Manual VPS provisioning activation complete for subdomain: {}.pgcrm.com", tenant.getDomainName());
    }
}
