package com.pgcrm.controlplane.service;

import com.pgcrm.controlplane.dto.ClientDetailResponse;
import com.pgcrm.controlplane.dto.DashboardMetricsResponse;

import java.util.List;
import java.util.UUID;

public interface AdminDashboardService {
    DashboardMetricsResponse getMetrics();
    List<ClientDetailResponse> getClients();
    void activateTenant(UUID tenantId);
}
