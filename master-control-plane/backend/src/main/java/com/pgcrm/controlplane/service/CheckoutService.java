package com.pgcrm.controlplane.service;

import com.pgcrm.controlplane.dto.CheckoutRegistrationRequest;
import com.pgcrm.controlplane.dto.RazorpayOrderResponse;

public interface CheckoutService {
    RazorpayOrderResponse initiateOrder(CheckoutRegistrationRequest request);
    void reconcileWebhook(String payload, String signatureHeader);
}
