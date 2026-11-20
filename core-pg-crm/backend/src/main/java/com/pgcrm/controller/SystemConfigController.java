package com.pgcrm.controller;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.dto.SystemConfigResponse;
import com.pgcrm.entity.TenantSettings;
import com.pgcrm.service.TenantSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigProperties systemConfig;
    private final TenantSettingsService tenantSettingsService;

    @Cacheable(value = "systemConfig", key = "T(com.pgcrm.multitenancy.TenantContext).getCurrentTenantSlug() ?: 'default'")
    @GetMapping("/config")
    public ResponseEntity<SystemConfigResponse> getConfig() {
        SystemConfigResponse response = SystemConfigResponse.fromProperties(systemConfig);
        try {
            TenantSettings settings = tenantSettingsService.getSettings();
            if (settings != null && response != null && response.getBranding() != null) {
                if (settings.getBrandingName() != null) {
                    response.getBranding().setName(settings.getBrandingName());
                }
                if (settings.getBrandingShortName() != null) {
                    response.getBranding().setShortTitle(settings.getBrandingShortName());
                }
                if (settings.getBrandingPrimaryColor() != null) {
                    response.getBranding().setPrimaryColor(settings.getBrandingPrimaryColor());
                }
            }
        } catch (Exception ignored) {
            // Fallback to static systemConfig defaults
        }
        return ResponseEntity.ok(response);
    }
}
