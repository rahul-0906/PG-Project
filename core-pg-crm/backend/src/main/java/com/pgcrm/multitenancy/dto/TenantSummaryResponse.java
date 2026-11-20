package com.pgcrm.multitenancy.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TenantSummaryResponse(
        String id,
        String slug,
        String displayName,
        String schemaName,
        String plan,
        String status,
        LocalDate renewalDate,
        LocalDateTime createdAt
) {}
