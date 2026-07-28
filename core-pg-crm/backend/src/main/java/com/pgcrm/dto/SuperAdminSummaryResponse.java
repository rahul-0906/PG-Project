package com.pgcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperAdminSummaryResponse {
    private long totalBuildings;
    private long totalRooms;
    private long totalBeds;
    private long occupiedBeds;
    private long vacantBeds;
    private double occupancyRate;

    private long totalGuests;
    private Map<String, Long> userCountByRole;

    private BigDecimal totalRevenueCollected;
    private BigDecimal totalPendingArrears;
    private long totalAuditLogsCount;
    private String systemHealthStatus;
}
