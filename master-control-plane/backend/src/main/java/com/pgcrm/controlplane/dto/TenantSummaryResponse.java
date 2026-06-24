package com.pgcrm.controlplane.dto;

import com.pgcrm.controlplane.model.enums.PaymentStatus;
import com.pgcrm.controlplane.model.enums.SubscriptionPlan;
import com.pgcrm.controlplane.model.enums.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSummaryResponse {
    private UUID tenantId;
    private String pgName;
    private String customDomain;
    private String contactEmail;
    private TenantStatus status;
    private SubscriptionPlan planType;
    private PaymentStatus paymentStatus;
    private LocalDateTime createdAt;
}
