package com.pgcrm.controlplane.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgcrm.controlplane.model.entity.TenantProfile;
import com.pgcrm.controlplane.model.entity.TenantSubscription;
import com.pgcrm.controlplane.model.enums.PaymentStatus;
import com.pgcrm.controlplane.model.enums.TenantStatus;
import com.pgcrm.controlplane.repository.TenantProfileRepository;
import com.pgcrm.controlplane.repository.TenantSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayWebhookServiceImpl implements RazorpayWebhookService {

    private final TenantProfileRepository tenantProfileRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final ObjectMapper objectMapper;
    private final TenantProvisioningWorker tenantProvisioningWorker;

    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

    /**
     * When {@code true}, bypasses HMAC signature verification to allow sandbox/integration
     * testing without a real Razorpay webhook secret. MUST be {@code false} in production.
     * Controlled by the {@code RAZORPAY_BYPASS_SIGNATURE} environment variable.
     */
    @Value("${razorpay.bypass-signature-for-sandbox:false}")
    private boolean bypassSignatureForSandbox;

    @Override
    @Transactional
    public void processWebhook(String payload, String signature) {
        log.info("Received Razorpay webhook payment capture request");

        // 1. Verify HMAC Signature
        if (!verifySignature(payload, signature)) {
            log.error("Webhook verification failed: signature mismatch");
            throw new SecurityException("Invalid Razorpay webhook signature");
        }

        try {
            // 2. Parse JSON payload
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText();
            log.info("Processing webhook event: {}", event);

            if ("payment.captured".equals(event) || "order.paid".equals(event)) {
                // Extract notes from payload
                JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                String tenantIdStr = paymentEntity.path("notes").path("tenant_id").asText();

                if (tenantIdStr == null || tenantIdStr.isEmpty()) {
                    log.warn("Webhook ignored: tenant_id note is missing or empty");
                    return;
                }

                UUID tenantId = UUID.fromString(tenantIdStr);
                log.info("Processing payment capture for tenant ID: {}", tenantId);

                // Fetch and update TenantProfile
                TenantProfile profile = tenantProfileRepository.findById(tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Tenant profile not found for ID: " + tenantId));
                
                profile.setStatus(TenantStatus.PROVISIONING);
                tenantProfileRepository.save(profile);
                log.info("Updated tenant profile ID {} status to PROVISIONING", tenantId);

                // Fetch and update TenantSubscription
                TenantSubscription subscription = tenantSubscriptionRepository.findByTenantId(tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Subscription not found for tenant ID: " + tenantId));
                
                subscription.setPaymentStatus(PaymentStatus.PAID);
                tenantSubscriptionRepository.save(subscription);
                log.info("Updated tenant subscription payment status to PAID for tenant ID: {}", tenantId);

                // Trigger background provisioning pipeline
                tenantProvisioningWorker.executeProvisioningPipeline(tenantId);
            } else {
                log.info("Webhook event '{}' ignored (unhandled event type)", event);
            }
        } catch (Exception e) {
            log.error("Error occurred while processing webhook payload (ignoring to prevent Razorpay retries): ", e);
            // We eat the exception here to ensure controller returns 200 OK, as per instructions.
        }
    }

    private boolean verifySignature(String payload, String signature) {
        // SEC-01: Bypass is only allowed when explicitly enabled via env var.
        // RAZORPAY_BYPASS_SIGNATURE must NEVER be true in production.
        if (bypassSignatureForSandbox) {
            log.warn("[SEC] Bypassing Razorpay webhook signature validation — SANDBOX MODE ONLY. " +
                     "Ensure RAZORPAY_BYPASS_SIGNATURE=false in production.");
            return true;
        }

        if (signature == null || webhookSecret == null || webhookSecret.isBlank()) {
            log.error("[SEC] Webhook secret is not configured. Set RAZORPAY_WEBHOOK_SECRET env var.");
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(rawHmac);
            return expectedSignature.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("HMAC signature verification failed: {}", e.getMessage());
            return false;
        }
    }
}
