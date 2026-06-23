package com.pgcrm.controlplane.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmcStatusResponse {
    private UUID tenantInstanceId;
    private String pgBrandName;
    private String domainName;
    private String ownerName;
    private String clientEmail;
    private LocalDate amcExpiryDate;
    private String licenseState;
    private java.util.List<PaymentHistoryItem> paymentHistory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentHistoryItem {
        private String date;
        private String amount;
        private String description;
    }
}
