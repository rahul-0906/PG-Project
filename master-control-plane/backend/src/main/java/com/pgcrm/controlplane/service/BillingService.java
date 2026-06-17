package com.pgcrm.controlplane.service;

import com.pgcrm.controlplane.dto.AmcStatusResponse;
import com.pgcrm.controlplane.dto.RazorpayOrderResponse;
import java.util.UUID;

public interface BillingService {
    RazorpayOrderResponse initiateAmcRenewal(UUID tenantInstanceId);
    void verifyAmcWebhook(String payload, String signatureHeader);
    AmcStatusResponse getAmcStatus(UUID tenantInstanceId);
    AmcStatusResponse getAmcStatusByDomain(String domainName);
}
