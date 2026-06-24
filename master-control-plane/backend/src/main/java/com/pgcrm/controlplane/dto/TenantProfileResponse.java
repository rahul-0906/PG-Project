package com.pgcrm.controlplane.dto;

import com.pgcrm.controlplane.model.enums.TenantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantProfileResponse {
    private UUID id;
    private String pgName;
    private String customDomain;
    private TenantStatus status;
    private String contactEmail;
}
