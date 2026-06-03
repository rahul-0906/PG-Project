package com.pgcrm.entity.enums;

/**
 * Enumeration of application roles that determine a {@link com.pgcrm.entity.User}'s
 * access level and feature set within the PG CRM system.
 *
 * <p>Each role maps to a distinct set of Spring Security permissions enforced at
 * both the controller level (via {@code @PreAuthorize}) and the API gateway filter layer.
 * The role is persisted on the {@link com.pgcrm.entity.User} entity as an
 * {@code EnumType.STRING} column and embedded in the JWT claims for stateless
 * authorisation on every request.</p>
 *
 * <p><strong>Access Hierarchy:</strong></p>
 * <ul>
 *   <li>{@link #PG_OWNER} — Super-admin; unrestricted access to all buildings,
 *       all managers, and all financial data. Manages building setup, creates
 *       manager accounts, and views the consolidated owner dashboard.</li>
 *   <li>{@link #PG_MANAGER} — Operational role; scoped to one or more specific
 *       buildings identified by {@link com.pgcrm.entity.User#getBranchId()}.
 *       Manages guest check-in/checkout, invoicing, EB billing, and maintenance.</li>
 *   <li>{@link #GUEST} — End-user role; access strictly limited to the guest portal.
 *       Can view their own invoice history, daily logs, and maintenance tickets.</li>
 * </ul>
 */
public enum Role {

    /**
     * Platform Owner / Super-Admin.
     * Has unrestricted access to all buildings, managers, guests, and financial reports.
     * Responsible for initial building setup and manager account provisioning.
     */
    PG_OWNER,

    /**
     * PG Manager / Branch Operator.
     * Operational administrator scoped to one or more assigned buildings.
     * Cannot access data for buildings not listed in their {@code branchId}.
     */
    PG_MANAGER,

    /**
     * Resident Guest.
     * Can only access their own portal data: invoices, daily meal logs,
     * maintenance requests, and profile information.
     */
    GUEST
}
