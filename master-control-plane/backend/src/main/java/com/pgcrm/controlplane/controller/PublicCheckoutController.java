package com.pgcrm.controlplane.controller;

import com.pgcrm.controlplane.dto.CheckoutRegistrationRequest;
import com.pgcrm.controlplane.dto.RazorpayOrderResponse;
import com.pgcrm.controlplane.service.CheckoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/public/checkout")
@RequiredArgsConstructor
public class PublicCheckoutController {

    private final CheckoutService checkoutService;

    /**
     * Public endpoint to initiate registration and generate a Razorpay order.
     */
    @PostMapping("/initiate-order")
    public ResponseEntity<RazorpayOrderResponse> initiateOrder(@Valid @RequestBody CheckoutRegistrationRequest request) {
        log.info("REST request to initiate order for subdomain: {}", request.getDomainName());
        RazorpayOrderResponse response = checkoutService.initiateOrder(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Webhook endpoint to reconcile payment captures and automate client VM setup.
     */
    @PostMapping("/webhook/reconcile")
    public ResponseEntity<Map<String, String>> reconcileWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signatureHeader) {
        
        log.info("REST request to reconcile webhook. Signature received: {}", signatureHeader != null);
        
        if (signatureHeader == null) {
            log.warn("Webhook request missing X-Razorpay-Signature header!");
            Map<String, String> response = new HashMap<>();
            response.put("error", "Missing signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            checkoutService.reconcileWebhook(payload, signatureHeader);
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Payment reconciled and tenant activation queued");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Signature verification failed: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Invalid signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (IllegalStateException e) {
            log.error("Conflict or state verification failed: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (Exception e) {
            log.error("Unhandled error during webhook reconciliation: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Internal server error during reconciliation");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Exception handler for request validation failures.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, String> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return errors;
    }

    /**
     * Exception handler for conflict states (e.g. domain taken).
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(IllegalStateException.class)
    public Map<String, String> handleConflictExceptions(IllegalStateException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("error", ex.getMessage());
        return errors;
    }
}
