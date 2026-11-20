package com.pgcrm.multitenancy.dto;

public record CreateTenantResult(
        String tenantId,
        String slug,
        String schemaName,
        String displayName,
        String status,
        String ownerEmail,
        String ownerTemporaryPassword
) {}
