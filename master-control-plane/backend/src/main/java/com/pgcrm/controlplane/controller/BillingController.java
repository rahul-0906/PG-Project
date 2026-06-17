package com.pgcrm.controlplane.controller;

import com.pgcrm.controlplane.dto.AmcRenewalRequest;
import com.pgcrm.controlplane.dto.AmcStatusResponse;
import com.pgcrm.controlplane.dto.RazorpayOrderResponse;
import java.util.UUID;
import com.pgcrm.controlplane.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/renew-amc")
    public ResponseEntity<RazorpayOrderResponse> renewAmc(@RequestBody AmcRenewalRequest request) {
        log.info("Request received to renew AMC for tenant ID: {}", request.getTenantInstanceId());
        RazorpayOrderResponse response = billingService.initiateAmcRenewal(request.getTenantInstanceId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-amc")
    public ResponseEntity<Void> verifyAmc(
            @RequestBody String payload,
            @RequestHeader("x-razorpay-signature") String signature) {
        log.info("Webhook received to verify AMC payment signature: {}", signature);
        billingService.verifyAmcWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status/{tenantInstanceId}")
    public ResponseEntity<AmcStatusResponse> getStatus(@PathVariable UUID tenantInstanceId) {
        log.info("REST request to fetch AMC status for tenant instance: {}", tenantInstanceId);
        return ResponseEntity.ok(billingService.getAmcStatus(tenantInstanceId));
    }

    @GetMapping("/status")
    public ResponseEntity<AmcStatusResponse> getStatusByDomain(@RequestParam String domainName) {
        log.info("REST request to fetch AMC status for domain: {}", domainName);
        return ResponseEntity.ok(billingService.getAmcStatusByDomain(domainName));
    }
}
