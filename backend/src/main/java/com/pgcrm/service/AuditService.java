package com.pgcrm.service;

import com.pgcrm.entity.AuditLog;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log a business event to the audit trail.
     * Auto-resolves tenantId from TenantContext and actor from SecurityContext.
     */
    public void log(AuditAction action, String entityType, String entityId, String description) {
        log(action, entityType, entityId, description, null);
    }

    public void log(AuditAction action, String entityType, String entityId,
                    String description, String metadata) {
        try {
            String actorId = null;
            String actorRole = "SYSTEM";

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                actorId = auth.getName();
                actorRole = auth.getAuthorities().stream()
                    .findFirst().map(a -> a.getAuthority().replace("ROLE_", "")).orElse("UNKNOWN");
            }

            AuditLog entry = AuditLog.builder()
                    .actorId(actorId)
                    .actorRole(actorRole)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .metadata(metadata)
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Never let audit failure break the main flow
            log.error("Failed to write audit log: {}", e.getMessage());
        }
    }
}
