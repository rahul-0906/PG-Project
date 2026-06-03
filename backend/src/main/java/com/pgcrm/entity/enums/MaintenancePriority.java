package com.pgcrm.entity.enums;

/**
 * Enumeration of urgency levels for a {@link com.pgcrm.entity.MaintenanceTicket}.
 *
 * <p>Priority is assigned by the PG Manager after reviewing a submitted ticket.
 * It determines the sort order in the manager dashboard's maintenance queue:
 * {@code HIGH} tickets are surfaced first, followed by {@code MEDIUM} and then {@code LOW}.</p>
 *
 * <p>The default priority for all newly raised tickets is {@link #MEDIUM},
 * as configured in {@link com.pgcrm.entity.MaintenanceTicket}.</p>
 */
public enum MaintenancePriority {

    /**
     * Non-urgent issue that can be scheduled for routine maintenance.
     * Example: scuffed paint, loose door handle, minor fixture replacement.
     */
    LOW,

    /**
     * Standard-urgency issue requiring attention within a reasonable timeframe.
     * Default priority assigned to all newly submitted maintenance tickets.
     * Example: malfunctioning ceiling fan, blocked drain, flickering light.
     */
    MEDIUM,

    /**
     * Critical issue requiring immediate attention, potentially affecting guest safety
     * or causing significant inconvenience.
     * Example: water leakage, electrical fault, broken lock.
     */
    HIGH
}
