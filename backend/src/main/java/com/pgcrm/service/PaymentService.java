package com.pgcrm.service;

import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.exception.SignatureVerificationException;
import com.pgcrm.entity.Invoice;
import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.enums.InvoiceStatus;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final InvoiceRepository invoiceRepository;
    private final SystemConfigProperties systemConfig;

    @Value("${razorpay.key-id:rzp_test_placeholder}")
    private String globalKeyId;

    @Value("${razorpay.key-secret:placeholder_secret}")
    private String globalKeySecret;

    @Value("${razorpay.enabled:false}")
    private boolean razorpayEnabled;

    /**
     * Creates a Razorpay order for an invoice.
     * Returns the order details to be used by the frontend Razorpay JS SDK.
     */
    @Transactional
    public Map<String, Object> createOrder(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new RuntimeException("Invoice is already paid");
        }

        // Resolve keys (now globally set via application properties)
        String keyId = globalKeyId;
        String keySecret = globalKeySecret;

        if (!razorpayEnabled || keyId.contains("placeholder")) {
            // Return a mock order for dev/testing
            log.info("💳 [RAZORPAY DISABLED] Mock order for invoice {} — amount ₹{}", invoiceId, invoice.getTotalAmount());
            return Map.of(
                "orderId", "order_mock_" + invoiceId,
                "amount", invoice.getTotalAmount().multiply(BigDecimal.valueOf(100)).intValue(),
                "currency", "INR",
                "keyId", "rzp_test_demo",
                "invoiceId", invoiceId,
                "guestName", invoice.getGuest().getFullName(),
                "guestEmail", invoice.getGuest().getEmail(),
                "mock", true
            );
        }

        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            JSONObject opts = new JSONObject();
            opts.put("amount", invoice.getTotalAmount().multiply(BigDecimal.valueOf(100)).intValue()); // paise
            opts.put("currency", "INR");
            opts.put("receipt", "pgcrm_inv_" + invoiceId.substring(0, 8));
            opts.put("notes", new JSONObject(Map.of("invoiceId", invoiceId, "guestId", invoice.getGuest().getId())));

            Order order = client.orders.create(opts);
            String razorpayOrderId = order.get("id");

            invoice.setRazorpayOrderId(razorpayOrderId);
            invoiceRepository.save(invoice);

            return Map.of(
                "orderId", razorpayOrderId,
                "amount", invoice.getTotalAmount().multiply(BigDecimal.valueOf(100)).intValue(),
                "currency", "INR",
                "keyId", keyId,
                "invoiceId", invoiceId,
                "guestName", invoice.getGuest().getFullName(),
                "guestEmail", invoice.getGuest().getEmail()
            );
        } catch (RazorpayException e) {
            throw new RuntimeException("Razorpay order creation failed: " + e.getMessage());
        }
    }

    @Transactional
    public Invoice verifyAndCapture(String invoiceId, String razorpayOrderId,
                                    String razorpayPaymentId, String signature) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        String keySecret = globalKeySecret;

        if (!razorpayEnabled || keySecret.contains("placeholder")) {
            log.info("💳 [RAZORPAY DISABLED] Marking invoice {} as PAID (mock)", invoiceId);
        } else {
            verifySignature(razorpayOrderId, razorpayPaymentId, signature, keySecret);
        }

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setRazorpayOrderId(razorpayOrderId);
        invoice.setRazorpayPaymentId(razorpayPaymentId);
        invoice.setPaidAt(LocalDateTime.now());
        return invoiceRepository.save(invoice);
    }

    private void verifySignature(String orderId, String paymentId, String signature, String secret) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            if (!computed.equals(signature)) {
                throw new SignatureVerificationException("Signature verification failed");
            }
        } catch (SignatureVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new SignatureVerificationException("Signature verification error: " + e.getMessage());
        }
    }

    /**
     * Records an offline (manual) payment by the manager.
     * Marks the invoice as PAID with the given method (CASH, UPI, BANK_TRANSFER, etc.)
     */
    @Transactional
    public Invoice recordManualPayment(String invoiceId, BigDecimal amount, String method) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new RuntimeException("Invoice is already paid");
        }

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaymentMethod(method);
        invoice.setPaidAt(LocalDateTime.now());
        log.info("Manual payment recorded for invoice {} — ₹{} via {}", invoiceId, amount, method);
        return invoiceRepository.save(invoice);
    }
}
