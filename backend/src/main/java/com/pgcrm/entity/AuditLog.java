package com.pgcrm.entity;

import com.pgcrm.entity.enums.AuditAction;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Immutable audit trail record.
 * Written by AuditService after every significant business event.
 * Used by Owner/Manager for year-end audit reports.
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_tenant_time", columnList = "tenant_id, timestamp"),
    @Index(name = "idx_audit_action",      columnList = "action")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** ID of the user who performed the action (null for system/scheduler) */
    @Column(name = "actor_id")
    private String actorId;

    /** Role of the actor */
    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    /** The entity type affected (Guest, Invoice, Bed, etc.) */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /** ID of the entity affected */
    @Column(name = "entity_id")
    private String entityId;

    /** Human-readable description of what happened */
    @Column(nullable = false, length = 500)
    private String description;

    /** JSON metadata (e.g., old/new values, amounts) */
    @Column(name = "metadata", length = 2000)
    private String metadata;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
