package com.pgcrm.controlplane.controller;

import com.pgcrm.controlplane.dto.ClientDetailResponse;
import com.pgcrm.controlplane.dto.DashboardMetricsResponse;
import com.pgcrm.controlplane.model.entity.TenantProfile;
import com.pgcrm.controlplane.model.entity.TenantSubscription;
import com.pgcrm.controlplane.model.enums.TenantStatus;
import com.pgcrm.controlplane.repository.TenantProfileRepository;
import com.pgcrm.controlplane.repository.TenantSubscriptionRepository;
import com.pgcrm.controlplane.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final TenantProfileRepository tenantProfileRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;

    /**
     * Retrieves high-level metrics for the B2B SaaS dashboard.
     */
    @GetMapping("/metrics")
    public ResponseEntity<DashboardMetricsResponse> getMetrics() {
        log.info("REST request to fetch administration metrics");
        DashboardMetricsResponse response = adminDashboardService.getMetrics();
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the consolidated list of B2B clients, instances, and subscriptions.
     */
    @GetMapping("/clients")
    public ResponseEntity<List<ClientDetailResponse>> getClients() {
        log.info("REST request to fetch B2B client directory details");
        List<ClientDetailResponse> response = adminDashboardService.getClients();
        return ResponseEntity.ok(response);
    }

    /**
     * Manually promotes a tenant instance status from PENDING_DEPLOYMENT to ACTIVE.
     */
    @PutMapping("/tenants/{tenantId}/activate")
    public ResponseEntity<Map<String, String>> activateTenant(@PathVariable UUID tenantId) {
        log.info("REST request to manually activate tenant instance: {}", tenantId);
        try {
            adminDashboardService.activateTenant(tenantId);
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Tenant instance manually activated successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Tenant activation failed: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            log.error("Unhandled error during tenant manual activation: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Internal server error during manual activation");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/tenants")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<TenantProfile>> getAllTenants() {
        log.info("REST request to fetch all tenant profiles sorted by creation date descending");
        List<TenantProfile> profiles = tenantProfileRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        for (TenantProfile profile : profiles) {
            tenantSubscriptionRepository.findByTenantId(profile.getId()).ifPresent(sub -> {
                profile.setPlanType(sub.getPlanType());
                profile.setPaymentStatus(sub.getPaymentStatus());
                profile.setSubscriptionExpiry(sub.getNextBillingDate());
            });
        }
        return ResponseEntity.ok(profiles);
    }

    @PostMapping("/tenants/{id}/suspend")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<TenantProfile> suspendTenant(@PathVariable UUID id) {
        log.info("REST request to suspend tenant: {}", id);
        TenantProfile profile = tenantProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant profile not found for ID: " + id));
        
        profile.setStatus(TenantStatus.SUSPENDED);
        TenantProfile saved = tenantProfileRepository.save(profile);
        
        log.info("Triggering infrastructure task to stop/block the instance for tenant: {}", id);
        // Simulate triggering the infrastructure stop/block task
        triggerInfrastructureStopTask(id);
        
        // Populate transient fields for consistent response
        tenantSubscriptionRepository.findByTenantId(saved.getId()).ifPresent(sub -> {
            saved.setPlanType(sub.getPlanType());
            saved.setPaymentStatus(sub.getPaymentStatus());
            saved.setSubscriptionExpiry(sub.getNextBillingDate());
        });
        
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/tenants/{id}/activate")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<TenantProfile> activateTenantPost(@PathVariable UUID id) {
        log.info("REST request to activate tenant via POST: {}", id);
        TenantProfile profile = tenantProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant profile not found for ID: " + id));
        
        profile.setStatus(TenantStatus.LIVE);
        TenantProfile saved = tenantProfileRepository.save(profile);
        
        // Populate transient fields for consistent response
        tenantSubscriptionRepository.findByTenantId(saved.getId()).ifPresent(sub -> {
            saved.setPlanType(sub.getPlanType());
            saved.setPaymentStatus(sub.getPaymentStatus());
            saved.setSubscriptionExpiry(sub.getNextBillingDate());
        });
        
        return ResponseEntity.ok(saved);
    }

    private void triggerInfrastructureStopTask(UUID id) {
        log.info("Infrastructure task executed: PG Core instance container for tenant {} has been stopped/blocked.", id);
    }
}
