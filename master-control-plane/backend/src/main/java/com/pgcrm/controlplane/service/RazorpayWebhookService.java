package com.pgcrm.controlplane.service;

public interface RazorpayWebhookService {
    void processWebhook(String payload, String signature);
}
