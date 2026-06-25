package com.pgcrm.controlplane.controller;

import com.pgcrm.controlplane.dto.TenantSummaryResponse;
import com.pgcrm.controlplane.service.AdminTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminTenantController {

    private final AdminTenantService adminTenantService;



    @PutMapping("/tenants/{tenantId}/status")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'SUPER_ADMIN', 'ROLE_PG_OWNER', 'PG_OWNER')")
    public ResponseEntity<Void> updateTenantStatus(
            @PathVariable java.util.UUID tenantId,
            @RequestParam(required = false) com.pgcrm.controlplane.model.enums.TenantStatus status,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        
        com.pgcrm.controlplane.model.enums.TenantStatus finalStatus = status;
        if (finalStatus == null && body != null && body.containsKey("status")) {
            finalStatus = com.pgcrm.controlplane.model.enums.TenantStatus.valueOf(body.get("status"));
        }
        
        if (finalStatus == null) {
            throw new IllegalArgumentException("Status is required");
        }
        
        log.info("REST request to update status of tenant: {} to {}", tenantId, finalStatus);
        adminTenantService.updateTenantStatus(tenantId, finalStatus);
        return ResponseEntity.ok().build();
    }
}
