package com.pgcrm.config;

import com.pgcrm.entity.TenantSettings;
import com.pgcrm.service.TenantSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * Meta WhatsApp Cloud API configuration provider.
 *
 * <p>Resolves credentials dynamically per-tenant from {@link TenantSettingsService},
 * replacing global environment variables.</p>
 */
@Configuration
@RequiredArgsConstructor
public class MetaWhatsAppConfig {

    private final TenantSettingsService tenantSettingsService;

    public String getPhoneNumberId() {
        return tenantSettingsService.getSettings().getWhatsappPhoneNumberId();
    }

    public String getAccessToken() {
        return tenantSettingsService.getSettings().getWhatsappAccessToken();
    }

    public String getVerifyToken() {
        return tenantSettingsService.getSettings().getWhatsappVerifyToken();
    }

    public boolean isEnabled() {
        TenantSettings s = tenantSettingsService.getSettings();
        return s.isNotificationWhatsappEnabled()
                && s.getWhatsappAccessToken() != null && !s.getWhatsappAccessToken().isBlank()
                && s.getWhatsappPhoneNumberId() != null && !s.getWhatsappPhoneNumberId().isBlank();
    }
}
