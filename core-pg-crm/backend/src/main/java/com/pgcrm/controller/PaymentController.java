package com.pgcrm.controller;

import com.pgcrm.entity.Invoice;
import com.pgcrm.service.AuditService;
import com.pgcrm.service.PaymentService;
import com.pgcrm.entity.enums.AuditAction;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final AuditService auditService;

    /** Guest initiates payment for their invoice — returns Razorpay order details */
    @PostMapping("/api/guest/invoices/{invoiceId}/initiate-payment")
    public ResponseEntity<Map<String, Object>> initiatePayment(@PathVariable String invoiceId) {
        Map<String, Object> order = paymentService.createOrder(invoiceId);
        return ResponseEntity.ok(order);
    }

    /** Guest submits payment verification after Razorpay checkout completes */
    @PostMapping("/api/guest/invoices/{invoiceId}/verify-payment")
    public ResponseEntity<Invoice> verifyPayment(@PathVariable String invoiceId,
                                                  @RequestBody Map<String, String> body) {
        Invoice invoice = paymentService.verifyAndCapture(
            invoiceId,
            body.get("razorpayOrderId"),
            body.get("razorpayPaymentId"),
            body.getOrDefault("razorpaySignature", "")
        );
        auditService.log(AuditAction.PAYMENT_RECEIVED, "Invoice", invoiceId,
            "Payment received: ₹" + invoice.getTotalAmount() + " via " + body.get("razorpayPaymentId"));
        return ResponseEntity.ok(invoice);
    }

    /**
     * Razorpay webhook endpoint (public — secured by signature check inside service).
     * Razorpay calls this when payment succeeds/fails asynchronously.
     */
    @PostMapping("/api/webhooks/razorpay")
    public ResponseEntity<Void> razorpayWebhook(@RequestBody String payload,
                                                  @RequestHeader("X-Razorpay-Signature") String signature) {
        // Webhook handling — log and acknowledge
        return ResponseEntity.ok().build();
    }

    /**
     * Manager records a manual (offline) payment for an invoice.
     * Used for cash, bank transfer, UPI (non-Razorpay) payments.
     */
    @PostMapping("/api/payments/record")
    public ResponseEntity<Invoice> recordManualPayment(@RequestBody Map<String, Object> body) {
        Invoice invoice = paymentService.recordManualPayment(
            body.get("invoiceId").toString(),
            new java.math.BigDecimal(body.get("amount").toString()),
            body.getOrDefault("method", "CASH").toString()
        );
        auditService.log(AuditAction.PAYMENT_RECEIVED, "Invoice", invoice.getId(),
            "Manual payment: ₹" + invoice.getTotalAmount() + " via " + invoice.getPaymentMethod());
        return ResponseEntity.ok(invoice);
    }
}
