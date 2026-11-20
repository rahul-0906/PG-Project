package com.pgcrm.service;

import com.pgcrm.entity.TenantSettings;
import com.pgcrm.multitenancy.crypto.AesGcmEncryptionHelper;
import com.pgcrm.repository.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service managing per-tenant operational settings, theme branding, and encrypted external API credentials.
 *
 * <p>Replaces all global environment variable reads for Razorpay, WhatsApp, branding, and theme.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSettingsService {

    private final TenantSettingsRepository repository;
    private final AesGcmEncryptionHelper encryptionHelper;

    /**
     * Retrieves the active tenant's settings, automatically decrypting sensitive fields
     * (Razorpay secret, webhook secret, WhatsApp token) for internal service consumption.
     *
     * <p>If no settings record exists yet in the tenant schema, initializes and persists
     * a default settings record.</p>
     *
     * @return decrypted {@link TenantSettings} instance
     */
    @Transactional
    public TenantSettings getSettings() {
        return repository.findCurrentSettings()
                .map(this::decryptSettings)
                .orElseGet(this::initDefaultSettings);
    }

    /**
     * Saves or updates tenant settings. Sensitive credentials are encrypted via AES-GCM
     * before database persistence.
     *
     * @param incoming updated settings values
     * @return persisted {@link TenantSettings}
     */
    @Transactional
    public TenantSettings saveSettings(TenantSettings incoming) {
        TenantSettings current = repository.findCurrentSettings().orElse(null);
        if (current == null) {
            current = TenantSettings.builder()
                    .id(UUID.randomUUID().toString())
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        // Branding
        if (incoming.getBrandingName() != null && !incoming.getBrandingName().isBlank()) {
            current.setBrandingName(incoming.getBrandingName().trim());
        }
        if (incoming.getBrandingShortName() != null && !incoming.getBrandingShortName().isBlank()) {
            current.setBrandingShortName(incoming.getBrandingShortName().trim());
        }
        if (incoming.getBrandingPrimaryColor() != null && !incoming.getBrandingPrimaryColor().isBlank()) {
            current.setBrandingPrimaryColor(incoming.getBrandingPrimaryColor().trim());
        }
        if (incoming.getBrandingLogoUrl() != null) {
            current.setBrandingLogoUrl(incoming.getBrandingLogoUrl().trim());
        }

        // Razorpay (encrypt secret fields)
        if (incoming.getRazorpayKeyId() != null) {
            current.setRazorpayKeyId(incoming.getRazorpayKeyId().trim());
        }
        if (incoming.getRazorpayKeySecret() != null && !incoming.getRazorpayKeySecret().isBlank()) {
            current.setRazorpayKeySecret(encryptionHelper.encrypt(incoming.getRazorpayKeySecret().trim()));
        }
        if (incoming.getRazorpayWebhookSecret() != null && !incoming.getRazorpayWebhookSecret().isBlank()) {
            current.setRazorpayWebhookSecret(encryptionHelper.encrypt(incoming.getRazorpayWebhookSecret().trim()));
        }

        // WhatsApp (encrypt secret fields)
        if (incoming.getWhatsappPhoneNumberId() != null) {
            current.setWhatsappPhoneNumberId(incoming.getWhatsappPhoneNumberId().trim());
        }
        if (incoming.getWhatsappAccessToken() != null && !incoming.getWhatsappAccessToken().isBlank()) {
            current.setWhatsappAccessToken(encryptionHelper.encrypt(incoming.getWhatsappAccessToken().trim()));
        }
        if (incoming.getWhatsappVerifyToken() != null && !incoming.getWhatsappVerifyToken().isBlank()) {
            current.setWhatsappVerifyToken(encryptionHelper.encrypt(incoming.getWhatsappVerifyToken().trim()));
        }

        current.setNotificationEmailEnabled(incoming.isNotificationEmailEnabled());
        current.setNotificationWhatsappEnabled(incoming.isNotificationWhatsappEnabled());
        current.setUpdatedAt(LocalDateTime.now());

        TenantSettings saved = repository.save(current);
        log.info("✓ Tenant settings updated and persisted for active tenant.");
        return decryptSettings(saved);
    }

    private TenantSettings decryptSettings(TenantSettings raw) {
        return TenantSettings.builder()
                .id(raw.getId())
                .brandingName(raw.getBrandingName())
                .brandingShortName(raw.getBrandingShortName())
                .brandingPrimaryColor(raw.getBrandingPrimaryColor())
                .brandingLogoUrl(raw.getBrandingLogoUrl())
                .razorpayKeyId(raw.getRazorpayKeyId())
                .razorpayKeySecret(encryptionHelper.decrypt(raw.getRazorpayKeySecret()))
                .razorpayWebhookSecret(encryptionHelper.decrypt(raw.getRazorpayWebhookSecret()))
                .whatsappPhoneNumberId(raw.getWhatsappPhoneNumberId())
                .whatsappAccessToken(encryptionHelper.decrypt(raw.getWhatsappAccessToken()))
                .whatsappVerifyToken(encryptionHelper.decrypt(raw.getWhatsappVerifyToken()))
                .notificationEmailEnabled(raw.isNotificationEmailEnabled())
                .notificationWhatsappEnabled(raw.isNotificationWhatsappEnabled())
                .createdAt(raw.getCreatedAt())
                .updatedAt(raw.getUpdatedAt())
                .build();
    }

    private TenantSettings initDefaultSettings() {
        TenantSettings defaults = TenantSettings.builder()
                .id(UUID.randomUUID().toString())
                .brandingName("PG CRM")
                .brandingShortName("PG")
                .brandingPrimaryColor("#2563eb")
                .notificationEmailEnabled(true)
                .notificationWhatsappEnabled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return repository.save(defaults);
    }
}
