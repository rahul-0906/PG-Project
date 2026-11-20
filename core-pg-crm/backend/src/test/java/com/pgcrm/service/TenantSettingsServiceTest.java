package com.pgcrm.service;

import com.pgcrm.entity.TenantSettings;
import com.pgcrm.multitenancy.crypto.AesGcmEncryptionHelper;
import com.pgcrm.repository.TenantSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TenantSettingsServiceTest {

    private static final String TEST_KEY_BASE64 = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes()
    );

    @Mock
    private TenantSettingsRepository repository;

    private AesGcmEncryptionHelper encryptionHelper;
    private TenantSettingsService service;

    @BeforeEach
    void setUp() {
        encryptionHelper = new AesGcmEncryptionHelper(TEST_KEY_BASE64);
        service = new TenantSettingsService(repository, encryptionHelper);
    }

    @Test
    void testGetSettings_InitializesDefault_WhenEmpty() {
        when(repository.findCurrentSettings()).thenReturn(Optional.empty());
        when(repository.save(any(TenantSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantSettings settings = service.getSettings();

        assertNotNull(settings);
        assertEquals("PG CRM", settings.getBrandingName());
        assertEquals("PG", settings.getBrandingShortName());
        assertEquals("#2563eb", settings.getBrandingPrimaryColor());
        assertTrue(settings.isNotificationEmailEnabled());
        assertFalse(settings.isNotificationWhatsappEnabled());

        verify(repository, times(1)).save(any(TenantSettings.class));
    }

    @Test
    void testGetSettings_DecryptsSecrets_WhenPresent() {
        String encSecret = encryptionHelper.encrypt("raw_rzp_secret");
        String encWebhook = encryptionHelper.encrypt("raw_webhook_secret");
        String encWaToken = encryptionHelper.encrypt("raw_wa_token");

        TenantSettings raw = TenantSettings.builder()
                .id("ts-1")
                .brandingName("Sri Sai PG")
                .razorpayKeyId("rzp_live_abc")
                .razorpayKeySecret(encSecret)
                .razorpayWebhookSecret(encWebhook)
                .whatsappAccessToken(encWaToken)
                .build();

        when(repository.findCurrentSettings()).thenReturn(Optional.of(raw));

        TenantSettings decrypted = service.getSettings();

        assertNotNull(decrypted);
        assertEquals("Sri Sai PG", decrypted.getBrandingName());
        assertEquals("rzp_live_abc", decrypted.getRazorpayKeyId());
        assertEquals("raw_rzp_secret", decrypted.getRazorpayKeySecret());
        assertEquals("raw_webhook_secret", decrypted.getRazorpayWebhookSecret());
        assertEquals("raw_wa_token", decrypted.getWhatsappAccessToken());
    }

    @Test
    void testSaveSettings_EncryptsSensitiveFields() {
        TenantSettings existing = TenantSettings.builder()
                .id("ts-1")
                .brandingName("Old PG")
                .build();

        when(repository.findCurrentSettings()).thenReturn(Optional.of(existing));
        when(repository.save(any(TenantSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        TenantSettings incoming = TenantSettings.builder()
                .brandingName("Green Living PG")
                .brandingShortName("GLPG")
                .brandingPrimaryColor("#10b981")
                .razorpayKeyId("rzp_test_new")
                .razorpayKeySecret("super_secret_key")
                .razorpayWebhookSecret("webhook_secret_key")
                .whatsappAccessToken("wa_access_token_val")
                .notificationEmailEnabled(true)
                .notificationWhatsappEnabled(true)
                .build();

        TenantSettings result = service.saveSettings(incoming);

        ArgumentCaptor<TenantSettings> captor = ArgumentCaptor.forClass(TenantSettings.class);
        verify(repository).save(captor.capture());
        TenantSettings savedToDb = captor.getValue();

        // Database entity must hold encrypted strings
        assertTrue(savedToDb.getRazorpayKeySecret().startsWith("v1:"));
        assertTrue(savedToDb.getRazorpayWebhookSecret().startsWith("v1:"));
        assertTrue(savedToDb.getWhatsappAccessToken().startsWith("v1:"));
        assertEquals("Green Living PG", savedToDb.getBrandingName());

        // Returned entity must be decrypted
        assertEquals("super_secret_key", result.getRazorpayKeySecret());
        assertEquals("webhook_secret_key", result.getRazorpayWebhookSecret());
        assertEquals("wa_access_token_val", result.getWhatsappAccessToken());
    }
}
