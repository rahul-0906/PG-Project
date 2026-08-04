package com.pgcrm.controlplane.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgcrm.controlplane.repository.TenantProfileRepository;
import com.pgcrm.controlplane.repository.TenantSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the HMAC-SHA256 webhook signature verification logic in
 * {@link RazorpayWebhookServiceImpl}.
 *
 * <p>The {@code verifySignature} method is {@code private}, so it is exercised
 * indirectly through {@link RazorpayWebhookServiceImpl#processWebhook}, which
 * throws {@link SecurityException} on a bad signature.</p>
 *
 * <p><strong>SEC-01 coverage:</strong>
 * <ul>
 *   <li>Valid HMAC-SHA256 signature → webhook processed normally</li>
 *   <li>Tampered payload → signature mismatch → {@link SecurityException}</li>
 *   <li>Wrong secret → signature mismatch → {@link SecurityException}</li>
 *   <li>Null signature → rejected → {@link SecurityException}</li>
 *   <li>Blank/unconfigured webhook secret → rejected → {@link SecurityException}</li>
 *   <li>Bypass flag = {@code true} → signature check skipped (sandbox mode only)</li>
 *   <li>Bypass flag = {@code false} (production default) → verification always enforced</li>
 * </ul></p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RazorpayWebhookServiceImpl — Signature Verification (SEC-01)")
class RazorpayWebhookSignatureTest {

    @Mock private TenantProfileRepository      tenantProfileRepository;
    @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;
    @Mock private TenantProvisioningWorker     tenantProvisioningWorker;

    @InjectMocks
    private RazorpayWebhookServiceImpl webhookService;

    // Canonical webhook payload and secret used across tests
    private static final String WEBHOOK_SECRET  = "super-secret-webhook-key";
    private static final String SAMPLE_PAYLOAD  = "{\"event\":\"unknown.event\",\"payload\":{}}";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void injectObjectMapper() {
        // Manually inject ObjectMapper since it is not a Spring-managed bean in unit tests
        ReflectionTestUtils.setField(webhookService, "objectMapper", objectMapper);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Computes the correct HMAC-SHA256 hex signature for a given payload and secret.
     * Mirrors the production implementation exactly.
     */
    private static String hmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    // ── Valid Signature ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid Signature")
    class ValidSignatureTests {

        @Test
        @DisplayName("accepts a correctly computed HMAC-SHA256 signature (unknown event is silently ignored)")
        void processWebhook_validSignature_unknownEventIgnored() throws Exception {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", WEBHOOK_SECRET);
            ReflectionTestUtils.setField(webhookService, "bypassSignatureForSandbox", false);

            String validSig = hmac(SAMPLE_PAYLOAD, WEBHOOK_SECRET);

            // Unknown event type → silently ignored (no exception, no DB call)
            assertThatNoException().isThrownBy(
                    () -> webhookService.processWebhook(SAMPLE_PAYLOAD, validSig));
        }

        @Test
        @DisplayName("signature comparison is case-insensitive (uppercase hex accepted)")
        void processWebhook_uppercaseHexSignature_accepted() throws Exception {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", WEBHOOK_SECRET);
            ReflectionTestUtils.setField(webhookService, "bypassSignatureForSandbox", false);

            String upperSig = hmac(SAMPLE_PAYLOAD, WEBHOOK_SECRET).toUpperCase();

            assertThatNoException().isThrownBy(
                    () -> webhookService.processWebhook(SAMPLE_PAYLOAD, upperSig));
        }
    }

    // ── Invalid Signature ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Invalid / Missing Signature")
    class InvalidSignatureTests {

        @Test
        @DisplayName("throws SecurityException when signature does not match payload")
        void processWebhook_wrongSignature_throwsSecurityException() {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", WEBHOOK_SECRET);
            ReflectionTestUtils.setField(webhookService, "bypassSignatureForSandbox", false);

            assertThatThrownBy(() ->
                    webhookService.processWebhook(SAMPLE_PAYLOAD, "deadbeef0000000000000000000000000000000000000000000000000000dead"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Invalid Razorpay webhook signature");
        }

        @Test
        @DisplayName("throws SecurityException when payload is tampered after signing")
        void processWebhook_tamperedPayload_throwsSecurityException() throws Exception {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", WEBHOOK_SECRET);
            ReflectionTestUtils.setField(webhookService, "bypassSignatureForSandbox", false);

            // Sign the original payload, then send a different payload
            String originalSig = hmac(SAMPLE_PAYLOAD, WEBHOOK_SECRET);
            String tamperedPayload = "{\"event\":\"payment.captured\",\"payload\":{\"injected\":true}}";

            assertThatThrownBy(() ->
                    webhookService.processWebhook(tamperedPayload, originalSig))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("throws SecurityException when webhook secret is blank (not configured)")
        void processWebhook_blankSecret_throwsSecurityException() throws Exception {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", "");
            ReflectionTestUtils.setField(webhookService, "bypassSignatureForSandbox", false);

            String sig = hmac(SAMPLE_PAYLOAD, WEBHOOK_SECRET);

            assertThatThrownBy(() -> webhookService.processWebhook(SAMPLE_PAYLOAD, sig))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("throws SecurityException when webhook secret is null")
        void processWebhook_nullSecret_throwsSecurityException() throws Exception {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", null);
            ReflectionTestUtils.setField(webhookService, "bypassSignatureForSandbox", false);

            String sig = hmac(SAMPLE_PAYLOAD, WEBHOOK_SECRET);

            assertThatThrownBy(() -> webhookService.processWebhook(SAMPLE_PAYLOAD, sig))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("throws SecurityException when incoming signature header is null")
        void processWebhook_nullSignatureHeader_throwsSecurityException() {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", WEBHOOK_SECRET);
            ReflectionTestUtils.setField(webhookService, "bypassSignatureForSandbox", false);

            assertThatThrownBy(() ->
                    webhookService.processWebhook(SAMPLE_PAYLOAD, null))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("throws SecurityException when signature is computed with a different secret")
        void processWebhook_wrongSecret_throwsSecurityException() throws Exception {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", WEBHOOK_SECRET);
            ReflectionTestUtils.setField(webhookService, "bypassSignatureForSandbox", false);

            String sigWithWrongSecret = hmac(SAMPLE_PAYLOAD, "totally-wrong-secret");

            assertThatThrownBy(() ->
                    webhookService.processWebhook(SAMPLE_PAYLOAD, sigWithWrongSecret))
                    .isInstanceOf(SecurityException.class);
        }
    }

    // ── SEC-01: Bypass Flag ───────────────────────────────────────────────────

    @Nested
    @DisplayName("SEC-01 — Sandbox Bypass Flag")
    class BypassFlagTests {

        @Test
        @DisplayName("bypass=true skips signature check and accepts any payload without a secret")
        void processWebhook_bypassEnabled_sigCheckSkipped() {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", "");
            ReflectionTestUtils.setField(webhookService, "bypassSignatureForSandbox", true);

            // No valid signature, no configured secret — but bypass=true so it must not throw
            assertThatNoException().isThrownBy(() ->
                    webhookService.processWebhook(SAMPLE_PAYLOAD, "not-a-real-signature"));
        }

        @Test
        @DisplayName("bypass=false (production default) enforces signature even if secret is set")
        void processWebhook_bypassDisabled_signatureEnforced() {
            ReflectionTestUtils.setField(webhookService, "webhookSecret", WEBHOOK_SECRET);
            ReflectionTestUtils.setField(webhookService, "bypassSignatureForSandbox", false);

            // A real secret is set, but the signature is wrong — must reject
            assertThatThrownBy(() ->
                    webhookService.processWebhook(SAMPLE_PAYLOAD, "bad-signature"))
                    .isInstanceOf(SecurityException.class);
        }
    }
}
