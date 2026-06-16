package com.pgcrm.entity.enums;

/**
 * Enumeration of resolution workflow states for a {@link com.pgcrm.entity.MaintenanceTicket}.
 *
 * <p>A maintenance ticket progresses linearly through these states as the manager
 * acknowledges and resolves the reported issue. Status transitions are performed
 * by the manager via the maintenance dashboard and recorded in the ticket's
 * {@link com.pgcrm.entity.MaintenanceTicket#getStatus()} field.</p>
 *
 * <p><strong>State Transition:</strong>
 * {@code OPEN → IN_PROGRESS → RESOLVED}</p>
 *
 * <p>Once a ticket reaches {@link #RESOLVED}, the {@link com.pgcrm.entity.MaintenanceTicket#getResolvedAt()}
 * timestamp is populated and an audit log entry is created via {@code AuditAction#MAINTENANCE_RESOLVED}.</p>
 */
public enum MaintenanceStatus {

    /** Ticket has been raised and is awaiting manager acknowledgement. Initial state. */
    OPEN,

    /** Manager has acknowledged the ticket and repair or maintenance work is underway. */
    IN_PROGRESS,

    /**
     * The reported issue has been fully resolved.
     * Terminal state — the corresponding {@link com.pgcrm.entity.MaintenanceTicket#getResolvedAt()}
     * timestamp is set when transitioning to this state.
     */
    RESOLVED
}
