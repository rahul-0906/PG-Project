package com.pgcrm.service;

import com.pgcrm.entity.AuditLog;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service responsible for writing structured audit log entries to the {@code audit_logs} table.
 *
 * <p>Provides a lightweight, fire-and-forget audit trail mechanism that records every
 * significant business event — guest check-in, check-out, invoice generation, payment capture,
 * bed switches, and administrative changes — without interrupting the main transactional flow.</p>
 *
 * <p><strong>Actor Resolution:</strong> The actor is automatically resolved from the current
 * Spring Security {@link SecurityContextHolder}. If the operation is invoked by a background
 * scheduler or an unauthenticated context, the {@code actorId} is set to {@code null}
 * and the {@code actorRole} defaults to {@code "SYSTEM"}.</p>
 *
 * <p><strong>Failure Isolation:</strong> All persistence calls are wrapped in a
 * {@code try/catch} block. An audit log write failure is logged at ERROR level but
 * <strong>never propagates</strong> to the caller, ensuring that a database connectivity
 * blip during audit persistence does not roll back or block the primary business transaction.</p>
 *
 * @see AuditLog
 * @see AuditAction
 * @see AuditLogRepository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Records a business event to the audit trail without supplementary metadata.
     *
     * <p>Convenience overload that delegates to {@link #log(AuditAction, String, String, String, String)}
     * with {@code metadata} set to {@code null}.</p>
     *
     * @param action      the semantic action type that occurred.
     * @param entityType  the simple class name of the affected entity (e.g., {@code "Guest"}, {@code "Invoice"}).
     * @param entityId    the UUID of the affected entity instance.
     * @param description a human-readable narrative of the event (stored verbatim in the log).
     */
    public void log(final AuditAction action, final String entityType,
                    final String entityId, final String description) {
        log(action, entityType, entityId, description, null);
    }

    /**
     * Records a business event to the audit trail with optional supplementary metadata.
     *
     * <p>Resolves the acting user from the current security context. If no authenticated
     * user is present (e.g., a scheduled job), {@code actorId} is {@code null} and
     * {@code actorRole} is {@code "SYSTEM"}. The {@code "ROLE_"} prefix injected by
     * Spring Security is stripped from the authority string before storage.</p>
     *
     * <p>This method is thread-safe: each invocation reads from the thread-local
     * {@link SecurityContextHolder} and constructs a new {@link AuditLog} builder independently.</p>
     *
     * @param action      the semantic action type that occurred.
     * @param entityType  the simple class name of the affected entity.
     * @param entityId    the UUID of the affected entity instance.
     * @param description a human-readable narrative of the event.
     * @param metadata    optional JSON string with additional context
     *                    (e.g., {@code {"bedId":"...","checkInDate":"..."}});
     *                    may be {@code null}.
     */
    public void log(final AuditAction action, final String entityType,
                    final String entityId, final String description, final String metadata) {
        try {
            String actorId   = null;
            String actorRole = "SYSTEM";

            final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                actorId   = auth.getName();
                actorRole = auth.getAuthorities().stream()
                        .findFirst()
                        .map(a -> a.getAuthority().replace("ROLE_", ""))
                        .orElse("UNKNOWN");
            }

            final AuditLog entry = AuditLog.builder()
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
            // Audit failures must never interrupt the primary business transaction.
            log.error("Failed to write audit log: {}", e.getMessage());
        }
    }
}
