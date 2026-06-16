package com.pgcrm.controller;

import com.pgcrm.config.MetaWhatsAppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final MetaWhatsAppConfig metaWhatsAppConfig;

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.challenge") String challenge,
            @RequestParam("hub.verify_token") String verifyToken) {
        
        log.info("Received Meta WhatsApp Webhook verification request. mode={}, verifyToken={}", mode, verifyToken);
        
        if ("subscribe".equals(mode) && metaWhatsAppConfig.getVerifyToken().equals(verifyToken)) {
            log.info("Webhook verification successful. Returning challenge.");
            return ResponseEntity.ok(challenge);
        } else {
            log.warn("Webhook verification failed. Invalid verify token or mode.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
}
