package com.pgcrm.entity.enums;

/**
 * Enumeration of possible occupancy states for a {@link com.pgcrm.entity.Bed}.
 *
 * <p>The bed status drives UI rendering in the room-picker (check-in / bed-switch
 * workflows) and determines which beds are available for new guest assignments.
 * Status transitions are managed exclusively by the {@code BedService} and
 * {@code GuestService} to enforce data integrity.</p>
 *
 * <p><strong>Valid Transitions:</strong></p>
 * <ul>
 *   <li>{@code VACANT → OCCUPIED} — Guest is checked in and assigned this bed.</li>
 *   <li>{@code OCCUPIED → VACANT} — Guest checks out; bed is released.</li>
 *   <li>{@code VACANT → MAINTENANCE} — Manager takes the bed offline for repair.</li>
 *   <li>{@code MAINTENANCE → VACANT} — Repair complete; bed is returned to service.</li>
 * </ul>
 */
public enum BedStatus {

    /** The bed is unoccupied and available for new guest assignment. */
    VACANT,

    /** The bed is currently assigned to an active guest. */
    OCCUPIED,

    /** The bed is temporarily offline due to a maintenance or repair requirement. */
    MAINTENANCE
}
