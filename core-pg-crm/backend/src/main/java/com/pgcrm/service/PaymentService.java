package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.TenantSettings;
import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.exception.SignatureVerificationException;
import com.pgcrm.repository.InvoiceRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

/**
 * Service managing all payment workflows: Razorpay order creation,
 * webhook signature verification, payment capture, and manual offline payment recording.
 *
 * <p>Razorpay credentials and webhook secret are resolved dynamically per-tenant
 * via {@link TenantSettingsService}, decoupling from global environment variables.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final InvoiceRepository      invoiceRepository;
    private final SystemConfigProperties systemConfig;
    private final TenantSettingsService  tenantSettingsService;

    /**
     * Resolves whether Razorpay is active for the current tenant.
     */
    public boolean isRazorpayEnabled() {
        TenantSettings settings = tenantSettingsService.getSettings();
        String keyId = settings.getRazorpayKeyId();
        String keySecret = settings.getRazorpayKeySecret();
        return keyId != null && !keyId.isBlank() && !keyId.contains("placeholder")
                && keySecret != null && !keySecret.isBlank() && !keySecret.contains("placeholder");
    }

    /**
     * Creates a Razorpay order for the given invoice and returns the order details
     * required by the Razorpay JS SDK to render the checkout modal.
     *
     * <p>If Razorpay is disabled or placeholder keys are configured,
     * a synthetic mock order map is returned with a deterministic {@code order_mock_*} ID,
     * enabling the frontend checkout flow to proceed in development without a live API call.</p>
     *
     * @param invoiceId the UUID of the {@link Invoice} to create a Razorpay order for.
     * @return a {@link Map} containing: {@code orderId}, {@code amount} (in paise),
     *         {@code currency}, {@code keyId}, {@code invoiceId}, {@code guestName},
     *         {@code guestEmail}, and {@code mock} flag.
     * @throws ResourceNotFoundException if no invoice exists for the given {@code invoiceId}.
     * @throws RuntimeException          if the invoice is already paid or Razorpay API returns an error.
     */
    @Transactional
    public Map<String, Object> createOrder(final String invoiceId) {
        log.info("Creating payment order for invoice ID: {}", invoiceId);
        try {
            final Invoice invoice = invoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> {
                        log.warn("Invoice not found for ID: {} during order creation", invoiceId);
                        return new ResourceNotFoundException("Invoice not found: " + invoiceId);
                    });

            if (invoice.getStatus() == InvoiceStatus.PAID) {
                log.warn("Invoice {} is already paid, cannot create order", invoiceId);
                throw new RuntimeException("Invoice is already paid");
            }

            final TenantSettings settings = tenantSettingsService.getSettings();
            final String keyId = settings.getRazorpayKeyId();
            final String keySecret = settings.getRazorpayKeySecret();
            final boolean enabled = isRazorpayEnabled();

            // In dev/mock mode, return a synthetic order without calling the Razorpay API.
            if (!enabled) {
                log.info("💳 [RAZORPAY DISABLED/MOCK] Mock order for invoice {} — amount ₹{}", invoiceId, invoice.getTotalAmount());
                return Map.of(
                        "orderId",    "order_mock_" + invoiceId,
                        "amount",     invoice.getTotalAmount().multiply(BigDecimal.valueOf(100)).intValue(),
                        "currency",   "INR",
                        "keyId",      keyId != null && !keyId.isBlank() ? keyId : "rzp_test_demo",
                        "invoiceId",  invoiceId,
                        "guestName",  invoice.getGuest() != null ? invoice.getGuest().getFullName() : "Guest",
                        "guestEmail", invoice.getGuest() != null ? invoice.getGuest().getEmail() : "guest@pgcrm.com",
                        "mock",       true
                );
            }

            try {
                final RazorpayClient client = new RazorpayClient(keyId, keySecret);
                final JSONObject opts = new JSONObject();
                opts.put("amount",   invoice.getTotalAmount().multiply(BigDecimal.valueOf(100)).intValue());
                opts.put("currency", "INR");
                opts.put("receipt",  "rcpt_" + invoiceId.substring(0, Math.min(8, invoiceId.length())));

                final Order order = client.orders.create(opts);
                final String orderId = order.get("id");

                invoice.setRazorpayOrderId(orderId);
                invoiceRepository.save(invoice);

                log.info("Successfully created Razorpay order: {} for invoice: {}", orderId, invoiceId);

                return Map.of(
                        "orderId",    orderId,
                        "amount",     invoice.getTotalAmount().multiply(BigDecimal.valueOf(100)).intValue(),
                        "currency",   "INR",
                        "keyId",      keyId,
                        "invoiceId",  invoiceId,
                        "guestName",  invoice.getGuest() != null ? invoice.getGuest().getFullName() : "Guest",
                        "guestEmail", invoice.getGuest() != null ? invoice.getGuest().getEmail() : "guest@pgcrm.com"
                );
            } catch (Exception e) {
                log.error("Failed to create Razorpay order for invoice {}: {}", invoiceId, e.getMessage(), e);
                throw new RuntimeException("Failed to initiate payment: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error creating payment order for invoice ID: {}", invoiceId, e);
            throw e;
        }
    }

    /**
     * Verifies the Razorpay checkout signature and captures payment for the invoice.
     *
     * @param invoiceId        the UUID of the {@link Invoice} being paid.
     * @param razorpayOrderId  the Razorpay order ID returned by {@link #createOrder}.
     * @param razorpayPaymentId the Razorpay payment ID returned by the checkout modal.
     * @param signature         the HMAC-SHA256 signature generated by the Razorpay SDK.
     * @return the updated {@link Invoice} with {@code status=PAID} and payment metadata set.
     * @throws ResourceNotFoundException     if no invoice exists for the given {@code invoiceId}.
     * @throws SignatureVerificationException if the computed signature does not match.
     */
    @Transactional
    public Invoice verifyAndCapture(final String invoiceId, final String razorpayOrderId,
                                    final String razorpayPaymentId, final String signature) {
        log.info("Verifying and capturing payment for invoice ID: {}, Razorpay Order ID: {}, Payment ID: {}",
                invoiceId, razorpayOrderId, razorpayPaymentId);
        try {
            final Invoice invoice = invoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> {
                        log.warn("Invoice not found for ID: {} during payment verification", invoiceId);
                        return new ResourceNotFoundException("Invoice not found: " + invoiceId);
                    });

            final TenantSettings settings = tenantSettingsService.getSettings();
            final String keySecret = settings.getRazorpayKeySecret();
            final boolean enabled = isRazorpayEnabled();

            if (!enabled) {
                log.info("💳 [RAZORPAY DISABLED/MOCK] Marking invoice {} as PAID (mock)", invoiceId);
            } else {
                try {
                    verifySignature(razorpayOrderId, razorpayPaymentId, signature, keySecret);
                } catch (SignatureVerificationException e) {
                    log.warn("Razorpay signature verification failed for invoice ID: {}, order ID: {}, payment ID: {}",
                            invoiceId, razorpayOrderId, razorpayPaymentId);
                    throw e;
                }
            }

            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setRazorpayOrderId(razorpayOrderId);
            invoice.setRazorpayPaymentId(razorpayPaymentId);
            invoice.setPaidAt(LocalDateTime.now());
            final Invoice savedInvoice = invoiceRepository.save(invoice);
            log.info("Successfully verified and captured payment for invoice ID: {}, marked as PAID", invoiceId);
            return savedInvoice;
        } catch (Exception e) {
            log.error("Error verifying/capturing payment for invoice ID: {}", invoiceId, e);
            throw e;
        }
    }

    /**
     * Verifies an incoming Razorpay webhook signature against the tenant's webhook secret.
     */
    public boolean verifyWebhookSignature(String payload, String signatureHeader) {
        final TenantSettings settings = tenantSettingsService.getSettings();
        final String webhookSecret = settings.getRazorpayWebhookSecret();

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Razorpay webhook rejected: No webhook secret configured for active tenant.");
            return false;
        }

        try {
            final Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            sha256Hmac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            final byte[] hash = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            final String expected = HexFormat.of().formatHex(hash);
            return expected.equalsIgnoreCase(signatureHeader);
        } catch (Exception e) {
            log.error("Failed to compute webhook HMAC signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Records an offline (manual) payment made by the manager for a given invoice.
     */
    @Transactional
    public Invoice recordManualPayment(final String invoiceId, final BigDecimal amount, final String method) {
        log.info("Recording manual offline payment for invoice ID: {}, amount: ₹{}, method: {}", invoiceId, amount, method);
        try {
            final Invoice invoice = invoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> {
                        log.warn("Invoice not found for ID: {} during manual payment recording", invoiceId);
                        return new ResourceNotFoundException("Invoice not found: " + invoiceId);
                    });

            if (invoice.getStatus() == InvoiceStatus.PAID) {
                log.warn("Invoice {} is already paid, cannot record manual payment", invoiceId);
                throw new RuntimeException("Invoice is already paid");
            }

            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaymentMethod(method);
            invoice.setPaidAt(LocalDateTime.now());
            log.info("Manual payment recorded for invoice {} — ₹{} via {}", invoiceId, amount, method);
            return invoiceRepository.save(invoice);
        } catch (Exception e) {
            log.error("Error recording manual payment for invoice ID: {}", invoiceId, e);
            throw e;
        }
    }

    private void verifySignature(final String orderId, final String paymentId,
                                 final String signature, final String secret) {
        if (signature == null || signature.isBlank()) {
            throw new SignatureVerificationException("Razorpay signature is missing.");
        }
        if (secret == null || secret.isBlank()) {
            throw new SignatureVerificationException("Razorpay key secret is not configured.");
        }
        try {
            final String data = orderId + "|" + paymentId;
            final Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            sha256Hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            final byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            final String expected = HexFormat.of().formatHex(hash);

            if (!expected.equalsIgnoreCase(signature)) {
                log.warn("Signature mismatch for orderId: {}, paymentId: {}", orderId, paymentId);
                throw new SignatureVerificationException("Signature verification failed");
            }
        } catch (SignatureVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("HMAC-SHA256 verification error: {}", e.getMessage(), e);
            throw new SignatureVerificationException("Signature calculation failed: " + e.getMessage());
        }
    }
}
