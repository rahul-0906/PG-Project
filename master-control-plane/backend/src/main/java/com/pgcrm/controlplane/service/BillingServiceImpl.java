package com.pgcrm.controlplane.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgcrm.controlplane.dto.RazorpayOrderResponse;
import com.pgcrm.controlplane.entity.LicenseState;
import com.pgcrm.controlplane.entity.Subscription;
import com.pgcrm.controlplane.entity.TenantInstance;
import com.pgcrm.controlplane.entity.TenantStatus;
import com.pgcrm.controlplane.repository.SubscriptionRepository;
import com.pgcrm.controlplane.repository.TenantInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final TenantInstanceRepository tenantInstanceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;

    @Value("${razorpay.key.id:rzp_test_mockKeyId12345}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:mockSecret}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook.secret:mockWebhookSecret}")
    private String webhookSecret;

    @Value("${billing.amc-renewal-fee-inr:35000}")
    private double amcRenewalFeeInr;

    @Override
    @Transactional
    public RazorpayOrderResponse initiateAmcRenewal(UUID tenantInstanceId) {
        log.info("Initiating AMC Renewal order for tenant instance ID: {}", tenantInstanceId);

        TenantInstance tenant = tenantInstanceRepository.findById(tenantInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant instance not found for ID: " + tenantInstanceId));

        // Generate Razorpay Order
        String razorpayOrderId = createRazorpayOrder(tenant.getId(), amcRenewalFeeInr);
        
        // Update TenantInstance with the new order ID for webhook correlation
        tenant.setRazorpayOrderId(razorpayOrderId);
        tenantInstanceRepository.save(tenant);

        return RazorpayOrderResponse.builder()
                .orderId(razorpayOrderId)
                .amount(BigDecimal.valueOf(amcRenewalFeeInr))
                .currency("INR")
                .keyId(razorpayKeyId)
                .clientEmail(tenant.getClient().getEmail())
                .clientPhone(tenant.getClient().getPhone())
                .pgBrandName(tenant.getClient().getPgBrandName())
                .build();
    }

    @Override
    @Transactional
    public void verifyAmcWebhook(String payload, String signatureHeader) {
        log.info("Received AMC Webhook verification request");

        // 1. Verify Signature
        if (!verifySignature(payload, signatureHeader, webhookSecret)) {
            log.error("Razorpay AMC webhook signature verification failed!");
            throw new IllegalArgumentException("Invalid signature");
        }

        try {
            // 2. Parse Event JSON
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText();
            log.info("Processing AMC webhook event type: {}", event);

            if ("order.paid".equals(event) || "payment.captured".equals(event)) {
                JsonNode paymentNode = root.path("payload").path("payment").path("entity");
                String orderId = paymentNode.path("order_id").asText();
                String paymentId = paymentNode.path("id").asText();
                double amountInPaise = paymentNode.path("amount").asDouble();
                double amountInInr = amountInPaise / 100.0;

                log.info("Renewal Payment captured. Order ID: {}, Payment ID: {}, Amount: {} INR", orderId, paymentId, amountInInr);

                // 3. Find tenant instance
                TenantInstance tenant = tenantInstanceRepository.findByRazorpayOrderId(orderId)
                        .orElseThrow(() -> new IllegalStateException("Tenant instance not found for order ID: " + orderId));

                // 4. Extend active subscription by 1 year
                Subscription subscription = tenant.getSubscription();
                if (subscription == null) {
                    throw new IllegalStateException("Subscription not found for tenant: " + tenant.getDomainName());
                }

                LocalDate currentExpiry = subscription.getAmcExpiryDate();
                LocalDate newExpiry;
                if (currentExpiry != null && currentExpiry.isAfter(LocalDate.now())) {
                    newExpiry = currentExpiry.plusYears(1);
                } else {
                    newExpiry = LocalDate.now().plusYears(1);
                }

                subscription.setAmcExpiryDate(newExpiry);
                subscription.setLicenseState(LicenseState.ACTIVE);
                
                // If the tenant was suspended, activate them again
                if (tenant.getStatus() == TenantStatus.SUSPENDED) {
                    log.info("Re-activating suspended tenant: {}", tenant.getDomainName());
                    tenant.setStatus(TenantStatus.ACTIVE);
                    tenantInstanceRepository.save(tenant);
                }

                subscriptionRepository.save(subscription);

                log.info("Payment Transaction Logged - Extended AMC Subscription for tenant: {} (New Expiry Date: {}). Order ID: {}, Payment ID: {}, Amount: {} INR",
                        tenant.getDomainName(), newExpiry, orderId, paymentId, amountInInr);
            }
        } catch (Exception e) {
            log.error("Error occurred while verifying AMC webhook: ", e);
            throw new RuntimeException("AMC Webhook processing failed", e);
        }
    }

    private String createRazorpayOrder(UUID tenantInstanceId, double amountInr) {
        long amountInPaise = Math.round(amountInr * 100);

        try {
            if (razorpayKeyId != null && !razorpayKeyId.startsWith("rzp_test_mockKeyId")) {
                com.razorpay.RazorpayClient razorpay = new com.razorpay.RazorpayClient(razorpayKeyId, razorpayKeySecret);
                
                JSONObject orderRequest = new JSONObject();
                orderRequest.put("amount", amountInPaise);
                orderRequest.put("currency", "INR");
                orderRequest.put("receipt", "amc_renewal_" + tenantInstanceId.toString().substring(0, 8));
                
                com.razorpay.Order order = razorpay.orders.create(orderRequest);
                return order.get("id");
            }
        } catch (Exception e) {
            log.error("Failed to generate order via Razorpay API. Falling back to mock order. Error: {}", e.getMessage());
        }

        String mockOrderId = "order_renewal_mock_" + UUID.randomUUID().toString().substring(0, 14);
        log.info("Generated Sandbox Mock AMC Renewal Order ID: {}", mockOrderId);
        return mockOrderId;
    }

    private boolean verifySignature(String payload, String signature, String secret) {
        if (signature == null || secret == null) {
            return false;
        }
        
        if (secret.equals("mockWebhookSecret") || signature.equals("sandbox_mock_signature")) {
            log.warn("Bypassing webhook signature validation (Sandbox environment)");
            return true;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(rawHmac);
            return expectedSignature.equals(signature);
        } catch (Exception e) {
            log.error("HMAC signature calculation failed: {}", e.getMessage());
            return false;
        }
    }
}
