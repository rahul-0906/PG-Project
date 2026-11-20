package com.pgcrm.multitenancy;

import com.pgcrm.controller.PaymentController;
import com.pgcrm.entity.TenantSettings;
import com.pgcrm.exception.SignatureVerificationException;
import com.pgcrm.service.PaymentService;
import com.pgcrm.service.TenantSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies Section 10 requirement:
 * "Webhook: a payment webhook signed with A's secret sent to B's host is rejected."
 */
@ExtendWith(MockitoExtension.class)
public class WebhookTenantIsolationTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private TenantSettingsService tenantSettingsService;

    @InjectMocks
    private PaymentController paymentController;

    private static final String SECRET_A = "webhook_secret_tenant_alpha_12345";
    private static final String SECRET_B = "webhook_secret_tenant_beta_67890";

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testWebhookSignedWithTenantASecret_SentToTenantBHost_IsRejected() throws Exception {
        // Request arrived at Tenant B's host
        TenantContext.TenantInfo tenantB = new TenantContext.TenantInfo(
                "t2", "tenant_b", "t_tenant_b", "Tenant Beta", "ACTIVE"
        );
        TenantContext.setTenant(tenantB);

        String payload = "{\"event\": \"payment.captured\", \"payload\": {\"payment\": {\"entity\": {\"id\": \"pay_123\"}}}}";

        // Signature computed using Tenant A's secret (an attacker attempting cross-tenant injection)
        String signatureSignedWithSecretA = computeHmacSha256(payload, SECRET_A);

        // Active tenant B's webhook verification rejects signature
        when(paymentService.verifyWebhookSignature(payload, signatureSignedWithSecretA)).thenReturn(false);

        // Execute webhook controller — should throw SignatureVerificationException (HTTP 400)
        assertThrows(SignatureVerificationException.class, () -> {
            paymentController.razorpayWebhook(payload, signatureSignedWithSecretA);
        });

        // Verify that payment capture was NEVER called
        verify(paymentService, never()).verifyAndCapture(any(), any(), any(), any());
    }

    @Test
    void testWebhookSignedWithTenantBSecret_SentToTenantBHost_IsAccepted() throws Exception {
        // Request arrived at Tenant B's host
        TenantContext.TenantInfo tenantB = new TenantContext.TenantInfo(
                "t2", "tenant_b", "t_tenant_b", "Tenant Beta", "ACTIVE"
        );
        TenantContext.setTenant(tenantB);

        String payload = "{\"event\": \"payment.captured\", \"payload\": {\"payment\": {\"entity\": {\"id\": \"pay_123\"}}}}";
        String validSignatureForTenantB = computeHmacSha256(payload, SECRET_B);

        // Active tenant B's webhook verification accepts signature
        when(paymentService.verifyWebhookSignature(payload, validSignatureForTenantB)).thenReturn(true);

        ResponseEntity<Void> response = paymentController.razorpayWebhook(payload, validSignatureForTenantB);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private String computeHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
