package com.pgcrm.controlplane.controller;

import com.pgcrm.controlplane.service.RazorpayWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RazorpayWebhookService razorpayWebhookService;

    @PostMapping("/razorpay")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("x-razorpay-signature") String signature) {
        
        log.info("Received Razorpay webhook request in controller");
        try {
            razorpayWebhookService.processWebhook(payload, signature);
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (SecurityException e) {
            log.error("Webhook authentication failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage());
            // Return 200 OK anyway for other unhandled exceptions to prevent retries
            return ResponseEntity.ok("Webhook handled internally");
        }
    }
}
