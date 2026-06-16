package com.pgcrm.controlplane.controller;

import com.pgcrm.controlplane.dto.ClientDetailResponse;
import com.pgcrm.controlplane.dto.DashboardMetricsResponse;
import com.pgcrm.controlplane.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
