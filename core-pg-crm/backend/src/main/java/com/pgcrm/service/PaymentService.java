package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.exception.SignatureVerificationException;
import com.pgcrm.repository.InvoiceRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
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
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

/**
 * Service responsible for all Razorpay payment integration workflows — order creation,
 * webhook signature verification, payment capture, and manual offline payment recording.
 *
 * <p><strong>Razorpay Order Flow:</strong></p>
 * <ol>
 *   <li>The guest clicks "Pay Now" on their invoice.</li>
 *   <li>The frontend calls {@link #createOrder(String)}, which creates a Razorpay Order
 *       and returns the order details (order ID, amount, key) for the Razorpay JS SDK.</li>
 *   <li>The guest completes payment in the Razorpay checkout modal.</li>
 *   <li>The frontend submits the payment proof ({@code orderId}, {@code paymentId},
 *       {@code signature}) to {@link #verifyAndCapture(String, String, String, String)}.</li>
 *   <li>The service verifies the HMAC-SHA256 signature and marks the invoice as PAID.</li>
 * </ol>
 *
 * <p><strong>Dev/Mock Mode:</strong> When {@code razorpay.enabled=false} (the default) or
 * the configured key contains {@code "placeholder"}, both {@link #createOrder} and
 * {@link #verifyAndCapture} short-circuit to mock responses. This allows end-to-end testing
 * of the payment UI without a live Razorpay account.</p>
 *
 * <p><strong>Manual Payment:</strong> Managers can record offline payments (cash, UPI, bank
 * transfer) via {@link #recordManualPayment(String, BigDecimal, String)}, bypassing the
 * Razorpay flow entirely.</p>
 *
 * @see InvoiceRepository
 * @see SignatureVerificationException
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final InvoiceRepository      invoiceRepository;
    private final SystemConfigProperties systemConfig;

    /** The Razorpay key ID used to authenticate API calls and returned to the frontend SDK. */
    @Value("${razorpay.key-id:rzp_test_placeholder}")
    private String globalKeyId;

    /** The Razorpay key secret used exclusively server-side for HMAC signature verification. */
    @Value("${razorpay.key-secret:placeholder_secret}")
    private String globalKeySecret;

    /**
     * Whether the Razorpay integration is active. When {@code false}, all payment operations
     * use mock responses safe for development and CI environments.
     */
    @Value("${razorpay.enabled:false}")
    private boolean razorpayEnabled;

    /**
     * Creates a Razorpay order for the given invoice and returns the order details
     * required by the Razorpay JS SDK to render the checkout modal.
     *
     * <p>If Razorpay is disabled or the configured key contains {@code "placeholder"},
     * a synthetic mock order map is returned with a deterministic {@code order_mock_*} ID,
     * enabling the frontend checkout flow to proceed in development without a live API call.</p>
     *
     * <p>On successful order creation, the Razorpay order ID is persisted to the invoice
     * record to enable correlation during the {@link #verifyAndCapture} webhook callback.</p>
     *
     * @param invoiceId the UUID of the {@link Invoice} to create a Razorpay order for.
     * @return a {@link Map} containing: {@code orderId}, {@code amount} (in paise),
     *         {@code currency}, {@code keyId}, {@code invoiceId}, {@code guestName},
     *         {@code guestEmail}, and optionally {@code mock=true} in dev mode.
     * @throws ResourceNotFoundException if no invoice exists for the given {@code invoiceId}.
     * @throws RuntimeException          if the invoice is already paid, or if the Razorpay
     *                                   API call fails.
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

            // In dev/mock mode, return a synthetic order without calling the Razorpay API.
            if (!razorpayEnabled || globalKeyId.contains("placeholder")) {
                log.info("💳 [RAZORPAY DISABLED] Mock order for invoice {} — amount ₹{}", invoiceId, invoice.getTotalAmount());
                return Map.of(
                        "orderId",    "order_mock_" + invoiceId,
                        "amount",     invoice.getTotalAmount().multiply(BigDecimal.valueOf(100)).intValue(),
                        "currency",   "INR",
                        "keyId",      "rzp_test_demo",
                        "invoiceId",  invoiceId,
                        "guestName",  invoice.getGuest().getFullName(),
                        "guestEmail", invoice.getGuest().getEmail(),
                        "mock",       true
                );
            }

            try {
                final RazorpayClient client = new RazorpayClient(globalKeyId, globalKeySecret);
                final JSONObject opts = new JSONObject();
                // Amount must be in paise (smallest currency unit).
                opts.put("amount",   invoice.getTotalAmount().multiply(BigDecimal.valueOf(100)).intValue());
                opts.put("currency", "INR");
                opts.put("receipt", "pgcrm_inv_" + invoiceId.substring(0, 8));
                opts.put("notes",    new JSONObject(Map.of("invoiceId", invoiceId, "guestId", invoice.getGuest().getId())));

                final Order  order          = client.orders.create(opts);
                final String razorpayOrderId = order.get("id");

                // Persist the Razorpay order ID for correlation during payment verification.
                invoice.setRazorpayOrderId(razorpayOrderId);
                invoiceRepository.save(invoice);

                log.info("Successfully created Razorpay order ID: {} for invoice ID: {}", razorpayOrderId, invoiceId);

                return Map.of(
                        "orderId",    razorpayOrderId,
                        "amount",     invoice.getTotalAmount().multiply(BigDecimal.valueOf(100)).intValue(),
                        "currency",   "INR",
                        "keyId",      globalKeyId,
                        "invoiceId",  invoiceId,
                        "guestName",  invoice.getGuest().getFullName(),
                        "guestEmail", invoice.getGuest().getEmail()
                );
            } catch (RazorpayException e) {
                log.error("Razorpay SDK order creation failed for invoice ID: {}", invoiceId, e);
                throw new RuntimeException("Razorpay order creation failed: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error creating order for invoice ID: {}", invoiceId, e);
            throw e;
        }
    }

    /**
     * Verifies the Razorpay payment signature and marks the invoice as paid on success.
     *
     * <p>The signature is verified using HMAC-SHA256 over the concatenation
     * {@code "<orderId>|<paymentId>"} with the Razorpay key secret as the signing key.
     * This follows the Razorpay server-side verification specification.</p>
     *
     * <p>In dev/mock mode (Razorpay disabled or placeholder key), the signature check
     * is skipped and the invoice is marked as PAID directly.</p>
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

            if (!razorpayEnabled || globalKeySecret.contains("placeholder")) {
                log.info("💳 [RAZORPAY DISABLED] Marking invoice {} as PAID (mock)", invoiceId);
            } else {
                try {
                    verifySignature(razorpayOrderId, razorpayPaymentId, signature, globalKeySecret);
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
     * Records an offline (manual) payment made by the manager for a given invoice.
     *
     * <p>Supported payment methods: {@code CASH}, {@code UPI}, {@code BANK_TRANSFER}.
     * The invoice status is set to {@link InvoiceStatus#PAID} and the payment timestamp
     * is recorded. No Razorpay API call is made.</p>
     *
     * @param invoiceId the UUID of the {@link Invoice} being settled.
     * @param amount    the amount paid (used for logging; not currently stored on the invoice).
     * @param method    the offline payment method string (e.g., {@code "CASH"}, {@code "UPI"}).
     * @return the updated {@link Invoice} with {@code status=PAID}.
     * @throws ResourceNotFoundException if no invoice exists for the given {@code invoiceId}.
     * @throws RuntimeException          if the invoice is already paid.
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

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Computes and verifies the Razorpay HMAC-SHA256 payment signature.
     *
     * <p>The expected signature is computed as:</p>
     * <pre>HMAC-SHA256(key=keySecret, message="{orderId}|{paymentId}")</pre>
     *
     * <p>The computed hex digest is compared against the signature submitted by the client.
     * A mismatch indicates a tampered or replayed payment callback and throws
     * {@link SignatureVerificationException}.</p>
     *
     * @param orderId   the Razorpay order ID.
     * @param paymentId the Razorpay payment ID.
     * @param signature the client-submitted hex-encoded HMAC-SHA256 signature.
     * @param secret    the Razorpay key secret used as the HMAC signing key.
     * @throws SignatureVerificationException if the computed signature does not match, or
     *                                        if the cryptographic algorithm is unavailable.
     */
    private void verifySignature(final String orderId, final String paymentId,
                                 final String signature, final String secret) {
        try {
            final String   payload  = orderId + "|" + paymentId;
            final Mac      mac      = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            final String computed = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            if (!computed.equals(signature)) {
                throw new SignatureVerificationException("Signature verification failed");
            }
        } catch (SignatureVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new SignatureVerificationException("Signature verification error: " + e.getMessage());
        }
    }
}
