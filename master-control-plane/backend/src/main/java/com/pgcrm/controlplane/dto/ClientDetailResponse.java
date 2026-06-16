package com.pgcrm.controlplane.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDetailResponse {
    private UUID clientId;
    private String ownerName;
    private String email;
    private String phone;
    private String pgBrandName;
    private List<TenantInstanceDetail> instances;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantInstanceDetail {
        private UUID tenantId;
        private String domainName;
        private String vpsIpAddress;
        private Integer allocatedPort;
        private String status;
        private LocalDate amcStartDate;
        private LocalDate amcExpiryDate;
        private String licenseState;
        private Boolean setupFeePaid;
    }
}
