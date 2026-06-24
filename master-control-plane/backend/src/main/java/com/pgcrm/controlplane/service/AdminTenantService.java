package com.pgcrm.controlplane.service;

import com.pgcrm.controlplane.dto.TenantSummaryResponse;
import java.util.List;

public interface AdminTenantService {
    List<TenantSummaryResponse> getAllTenantsSummary();
    void updateTenantStatus(java.util.UUID tenantId, com.pgcrm.controlplane.model.enums.TenantStatus newStatus);
}
