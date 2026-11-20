package com.pgcrm.controller;

import com.pgcrm.entity.TenantSettings;
import com.pgcrm.service.TenantSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class PublicConfigController {

    private final TenantSettingsService tenantSettingsService;

    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> getPublicConfig() {
        TenantSettings settings = tenantSettingsService.getSettings();
        Map<String, String> config = new HashMap<>();
        config.put("pgName", settings.getBrandingName());
        config.put("pgShortName", settings.getBrandingShortName());
        config.put("primaryColor", settings.getBrandingPrimaryColor());
        if (settings.getBrandingLogoUrl() != null) {
            config.put("logoUrl", settings.getBrandingLogoUrl());
        }
        return ResponseEntity.ok(config);
    }
}
