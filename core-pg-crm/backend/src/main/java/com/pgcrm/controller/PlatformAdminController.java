package com.pgcrm.controller;

import com.pgcrm.multitenancy.TenantMigrationVerifier;
import com.pgcrm.multitenancy.TenantProvisioningService;
import com.pgcrm.multitenancy.dto.CreateTenantRequest;
import com.pgcrm.multitenancy.dto.CreateTenantResult;
import com.pgcrm.multitenancy.dto.MigrationVerificationResult;
import com.pgcrm.multitenancy.dto.TenantSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller exposing platform administration REST endpoints for tenant lifecycle management.
 *
 * <p><strong>Access Rules (Spec Section 6):</strong></p>
 * <ul>
 *   <li>Endpoints live exclusively under {@code /platform/api/**}.</li>
 *   <li>Enabled ONLY in shared deployment mode; returns 404 in dedicated mode.</li>
 *   <li>Reachable ONLY on platform base domains; blocked on tenant subdomains.</li>
 *   <li>Authenticated exclusively via platform admin credentials ({@code X-Platform-Key}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/platform/api/tenants")
@RequiredArgsConstructor
@Slf4j
public class PlatformAdminController {

    private final TenantProvisioningService tenantProvisioningService;
    private final TenantMigrationVerifier tenantMigrationVerifier;

    @GetMapping("/{slug}/verify-migration")
    public ResponseEntity<MigrationVerificationResult> verifyMigration(
            @PathVariable String slug,
            @RequestParam(defaultValue = "public") String sourceSchema) {
        log.info("Received request to verify migration for tenant '{}' against source schema '{}'", slug, sourceSchema);
        String targetSchema = "t_" + slug;
        MigrationVerificationResult result = tenantMigrationVerifier.verifyMigration(sourceSchema, targetSchema);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<CreateTenantResult> createTenant(@RequestBody CreateTenantRequest request) {
        log.info("Received platform tenant provisioning request for slug: '{}'", request.slug());
        CreateTenantResult result = tenantProvisioningService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<List<TenantSummaryResponse>> listTenants() {
        return ResponseEntity.ok(tenantProvisioningService.listTenants());
    }

    @PostMapping("/{slug}/suspend")
    public ResponseEntity<Map<String, String>> suspendTenant(@PathVariable String slug) {
        log.info("Received request to suspend tenant: '{}'", slug);
        tenantProvisioningService.suspendTenant(slug);
        return ResponseEntity.ok(Map.of(
                "slug", slug,
                "status", "SUSPENDED",
                "message", "Tenant suspended successfully. Access is now blocked with HTTP 403."
        ));
    }

    @PostMapping("/{slug}/activate")
    public ResponseEntity<Map<String, String>> activateTenant(@PathVariable String slug) {
        log.info("Received request to activate tenant: '{}'", slug);
        tenantProvisioningService.activateTenant(slug);
        return ResponseEntity.ok(Map.of(
                "slug", slug,
                "status", "ACTIVE",
                "message", "Tenant activated successfully."
        ));
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<Map<String, String>> deleteTenant(@PathVariable String slug) {
        log.info("Received request to delete tenant: '{}'", slug);
        tenantProvisioningService.deleteTenant(slug);
        return ResponseEntity.ok(Map.of(
                "slug", slug,
                "status", "DELETED",
                "message", "Tenant marked as DELETED. Schema preserved in accordance with data retention policy."
        ));
    }
}
