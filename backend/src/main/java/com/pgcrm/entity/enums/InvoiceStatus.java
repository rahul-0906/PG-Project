package com.pgcrm.entity.enums;

/**
 * Enumeration of lifecycle states for a guest's monthly {@link com.pgcrm.entity.Invoice}.
 *
 * <p>An invoice progresses through these states in a defined sequence driven by guest
 * payment actions, manager confirmations, and the scheduled payment-reminder job.
 * The current status is persisted as a {@code STRING} in the {@code invoices} table
 * and is used to drive UI badge rendering, scheduled task logic, and API filtering.</p>
 *
 * <p><strong>Status Transition Diagram:</strong></p>
 * <pre>
 *   GENERATED ──────────────────────────────────► PAID
 *       │ (guest initiates cash payment)               ▲
 *       ▼                                              │
 *   PENDING_CASH_VERIFICATION ─(manager confirms)──────┘
 *       │
 *   GENERATED ──(due date passes without payment)──► OVERDUE ──► PAID
 * </pre>
 *
 * <p>A database check constraint on the {@code invoices} table enforces that only
 * valid status values (including {@code PENDING_CASH_VERIFICATION}) are persisted.
 * See Flyway migration {@code V9__update_invoice_status_check.sql} for the constraint definition.</p>
 */
public enum InvoiceStatus {

    /** Invoice has been generated and is awaiting guest payment. Initial state. */
    GENERATED,

    /** Payment has been received and confirmed (online or cash). Terminal state. */
    PAID,

    /**
     * The invoice due date has passed without a confirmed payment.
     * Transitioned by the {@code PaymentReminderScheduler} on the configured overdue interval.
     */
    OVERDUE,

    /**
     * The guest has initiated a cash handover request, and the invoice is awaiting
     * confirmation by the PG Manager. Transient state between {@code GENERATED} and {@code PAID}.
     * Added in Flyway migration {@code V9__update_invoice_status_check.sql}.
     */
    PENDING_CASH_VERIFICATION
}
