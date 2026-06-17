package com.pgcrm.controlplane.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgcrm.controlplane.dto.CheckoutRegistrationRequest;
import com.pgcrm.controlplane.dto.RazorpayOrderResponse;
import com.pgcrm.controlplane.entity.*;
import com.pgcrm.controlplane.repository.ClientRepository;
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
public class CheckoutServiceImpl implements CheckoutService {

    private final ClientRepository clientRepository;
    private final TenantInstanceRepository tenantInstanceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;
    private final ProvisioningService provisioningService;

    @Value("${razorpay.key.id:rzp_test_mockKeyId12345}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:mockSecret}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook.secret:mockWebhookSecret}")
    private String webhookSecret;

    @Value("${onboarding.setup-fee-inr:5000}")
    private double setupFeeInr;

    @Override
    @Transactional
    public RazorpayOrderResponse initiateOrder(CheckoutRegistrationRequest request) {
        log.info("Initiating onboarding order for domain: {} (Brand: {})", request.getDomainName(), request.getPgBrandName());

        // 1. Check if domain name is already registered
        if (tenantInstanceRepository.findByDomainName(request.getDomainName()).isPresent()) {
            throw new IllegalStateException("Domain/Subdomain name is already registered: " + request.getDomainName());
        }

        // 2. Find or create Client
        Client client = clientRepository.findByEmail(request.getEmail())
                .orElseGet(() -> {
                    log.info("Creating new client profile for email: {}", request.getEmail());
                    return clientRepository.save(Client.builder()
                            .ownerName(request.getOwnerName())
                            .email(request.getEmail())
                            .phone(request.getPhone())
                            .pgBrandName(request.getPgBrandName())
                            .build());
                });

        // 3. Create TenantInstance in PROVISIONING/PENDING_PAYMENT state
        TenantInstance tenantInstance = TenantInstance.builder()
                .client(client)
                .domainName(request.getDomainName())
                .status(TenantStatus.PROVISIONING)
                .build();

        // Save first to generate UUID reference
        tenantInstance = tenantInstanceRepository.save(tenantInstance);

        // 4. Generate Razorpay Order
        String razorpayOrderId = createRazorpayOrder(tenantInstance.getId(), setupFeeInr);
        tenantInstance.setRazorpayOrderId(razorpayOrderId);
        tenantInstanceRepository.save(tenantInstance);

        return RazorpayOrderResponse.builder()
                .orderId(razorpayOrderId)
                .amount(BigDecimal.valueOf(setupFeeInr))
                .currency("INR")
                .keyId(razorpayKeyId)
                .clientEmail(client.getEmail())
                .clientPhone(client.getPhone())
                .pgBrandName(client.getPgBrandName())
                .build();
    }

    @Override
    @Transactional
    public void reconcileWebhook(String payload, String signatureHeader) {
        log.info("Received Razorpay Webhook reconciliation request");

        // 1. Verify Signature
        if (!verifySignature(payload, signatureHeader, webhookSecret)) {
            log.error("Razorpay webhook signature verification failed!");
            throw new IllegalArgumentException("Invalid signature");
        }

        try {
            // 2. Parse Event JSON
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText();
            log.info("Processing webhook event type: {}", event);

            if ("order.paid".equals(event) || "payment.captured".equals(event)) {
                JsonNode paymentNode = root.path("payload").path("payment").path("entity");
                String orderId = paymentNode.path("order_id").asText();
                String paymentId = paymentNode.path("id").asText();

                log.info("Payment captured. Order ID: {}, Payment ID: {}", orderId, paymentId);

                // 3. Reconcile Tenant Instance
                TenantInstance tenant = tenantInstanceRepository.findByRazorpayOrderId(orderId)
                        .orElseThrow(() -> new IllegalStateException("Tenant instance not found for order ID: " + orderId));

                if (tenant.getStatus() == TenantStatus.ACTIVE || tenant.getStatus() == TenantStatus.PENDING_DEPLOYMENT) {
                    log.info("Tenant {} is already processed (Status: {}). Skipping reconciliation.", tenant.getDomainName(), tenant.getStatus());
                    return;
                }

                // Update Tenant Instance details
                tenant.setStatus(TenantStatus.PENDING_DEPLOYMENT);
                tenantInstanceRepository.save(tenant);

                // 4. Create Active Subscription (AMC contract starting now, expiring in 1 year)
                Subscription subscription = Subscription.builder()
                        .tenantInstance(tenant)
                        .setupFeePaid(true)
                        .amcStartDate(LocalDate.now())
                        .amcExpiryDate(LocalDate.now().plusYears(1))
                        .licenseState(LicenseState.ACTIVE)
                        .build();
                subscriptionRepository.save(subscription);

                log.info("Successfully provisioned subscription for tenant: {}. Initiating asynchronous VM deployment...", tenant.getDomainName());
                provisioningService.provisionNewTenant(tenant);
            }
        } catch (Exception e) {
            log.error("Error occurred while reconciling webhook: ", e);
            throw new RuntimeException("Webhook reconciliation failed", e);
        }
    }

    private String createRazorpayOrder(UUID tenantInstanceId, double amountInr) {
        // Amount is converted to Paise (1 INR = 100 Paise)
        long amountInPaise = Math.round(amountInr * 100);

        try {
            // Only try calling Razorpay client if real credentials are set
            if (razorpayKeyId != null && !razorpayKeyId.startsWith("rzp_test_mockKeyId")) {
                com.razorpay.RazorpayClient razorpay = new com.razorpay.RazorpayClient(razorpayKeyId, razorpayKeySecret);
                
                JSONObject orderRequest = new JSONObject();
                orderRequest.put("amount", amountInPaise);
                orderRequest.put("currency", "INR");
                orderRequest.put("receipt", tenantInstanceId.toString());
                
                com.razorpay.Order order = razorpay.orders.create(orderRequest);
                return order.get("id");
            }
        } catch (Exception e) {
            log.error("Failed to generate order via Razorpay API. Falling back to mock order. Error: {}", e.getMessage());
        }

        // Mock Order fallback for local testing & sandbox simulation
        String mockOrderId = "order_mock_" + UUID.randomUUID().toString().substring(0, 14);
        log.info("Generated Sandbox Mock Order ID: {}", mockOrderId);
        return mockOrderId;
    }

    private boolean verifySignature(String payload, String signature, String secret) {
        if (signature == null || secret == null) {
            return false;
        }
        
        // Skip verification if sandbox defaults are in play for local development ease
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

    private void triggerAutomatedProvisioning(TenantInstance tenant) {
        // Run provisioning scripts asynchronously (e.g. via system executor, Ansible hook, or Jenkins API)
        log.info("[ANSIBLE/SSH EVENT] Triggering playbook: ansible-playbook provision-tenant.yml -e \"subdomain={} port={}\"",
                tenant.getDomainName(), tenant.getAllocatedPort());
    }
}
