package com.pgcrm.controlplane.dto;

import com.pgcrm.controlplane.model.enums.SubscriptionPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantOnboardingRequest {
    private String pgName;
    private String pgShortTitle;
    private String customDomain;
    private String routerIp;
    private String whatsappNumber;
    private String contactEmail;
    private String razorpayKey;
    private String razorpaySecret;
    private String whatsappToken;
    private String whatsappKey;
    private SubscriptionPlan planType;
    private java.math.BigDecimal amcFee;
}
