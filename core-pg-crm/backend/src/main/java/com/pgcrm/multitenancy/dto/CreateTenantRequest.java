package com.pgcrm.multitenancy.dto;

import java.time.LocalDate;
import java.util.List;

public record CreateTenantRequest(
        String slug,
        String displayName,
        String plan,
        LocalDate renewalDate,
        List<String> domains,
        String ownerEmail,
        String ownerName,
        String initialPassword
) {}
