package com.pgcrm.controlplane.controller;

import com.pgcrm.controlplane.dto.TenantOnboardingRequest;
import com.pgcrm.controlplane.service.TenantOnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantOnboardingController {

    private final TenantOnboardingService tenantOnboardingService;
    private final com.pgcrm.controlplane.repository.TenantProfileRepository tenantProfileRepository;

    @PostMapping("/onboard")
    public ResponseEntity<Void> onboard(@RequestBody TenantOnboardingRequest request) {
        log.info("REST request to onboard tenant with domain: {}", request.getCustomDomain());
        
        UUID currentUserId = getCurrentUserId();
        tenantOnboardingService.processOnboarding(request, currentUserId);
        
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/me")
    public ResponseEntity<com.pgcrm.controlplane.dto.TenantProfileResponse> getMyTenant() {
        UUID currentUserId = getCurrentUserId();
        log.info("Fetching tenant profile for owner: {}", currentUserId);
        
        return tenantProfileRepository.findByOwnerUserId(currentUserId)
                .map(profile -> com.pgcrm.controlplane.dto.TenantProfileResponse.builder()
                        .id(profile.getId())
                        .pgName(profile.getPgName())
                        .customDomain(profile.getCustomDomain())
                        .status(profile.getStatus())
                        .contactEmail(profile.getContactEmail())
                        .build())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated");
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            if (principal != null) {
                try {
                    return UUID.fromString(principal.toString());
                } catch (IllegalArgumentException ex) {
                    throw new IllegalStateException("Could not extract a valid UUID from security context authentication principal");
                }
            }
            throw new IllegalStateException("User authenticated principal is null or invalid");
        }
    }
}
