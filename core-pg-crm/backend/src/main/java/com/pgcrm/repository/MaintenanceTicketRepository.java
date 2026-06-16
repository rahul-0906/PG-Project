package com.pgcrm.repository;

import com.pgcrm.entity.MaintenanceTicket;
import com.pgcrm.entity.enums.MaintenanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@link MaintenanceTicket} entity.
 *
 * <p>Provides query and count operations against the {@code maintenance_tickets} table
 * to support the manager's maintenance management dashboard and the guest portal's
 * ticket submission and history views.</p>
 *
 * <p>All derived query methods leverage Spring Data JPA's query derivation from
 * method names. The {@code buildingId} field on the {@link MaintenanceTicket} entity
 * is a plain string (not a JPA association) to decouple the ticket from the building
 * aggregate and allow efficient single-column index lookups.</p>
 *
 * @see MaintenanceTicket
 * @see MaintenanceStatus
 * @see com.pgcrm.entity.enums.MaintenancePriority
 */
@Repository
public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, String> {

    /**
     * Returns all maintenance tickets for a specific building, regardless of status.
     *
     * <p>Used by the manager dashboard to display the full ticket queue for their
     * assigned building, including open, in-progress, and resolved tickets.</p>
     *
     * @param buildingId the UUID of the building to retrieve tickets for.
     * @return a {@link List} of {@link MaintenanceTicket} records for the building;
     *         empty list if none exist.
     */
    List<MaintenanceTicket> findByBuildingId(String buildingId);

    /**
     * Returns all maintenance tickets with the specified resolution status, across
     * all buildings.
     *
     * <p>Used by the Platform Admin's global maintenance view to filter the ticket
     * list by status (e.g., show all {@code OPEN} tickets across all buildings).</p>
     *
     * @param status the {@link MaintenanceStatus} to filter by.
     * @return a {@link List} of {@link MaintenanceTicket} records with the given status;
     *         empty list if none exist.
     */
    List<MaintenanceTicket> findByStatus(MaintenanceStatus status);

    /**
     * Returns all maintenance tickets for a specific building with the given resolution status.
     *
     * <p>Used by the manager dashboard's status-filtered maintenance queue view
     * (e.g., show only {@code OPEN} tickets for Building A). Combines building scoping
     * with status filtering in a single compound derived query.</p>
     *
     * @param buildingId the UUID of the building to scope the query to.
     * @param status     the {@link MaintenanceStatus} to filter by.
     * @return a {@link List} of {@link MaintenanceTicket} records matching both criteria;
     *         empty list if none exist.
     */
    List<MaintenanceTicket> findByBuildingIdAndStatus(String buildingId, MaintenanceStatus status);

    /**
     * Returns all maintenance tickets raised by a specific guest.
     *
     * <p>Used by the guest portal to display the guest's own ticket history, allowing
     * them to track the status of issues they have reported. Traverses the
     * {@code raisedByGuest.id} association path.</p>
     *
     * @param guestId the UUID of the {@link com.pgcrm.entity.Guest} who raised the ticket.
     * @return a {@link List} of {@link MaintenanceTicket} records raised by the given guest;
     *         empty list if none exist.
     */
    List<MaintenanceTicket> findByRaisedByGuestId(String guestId);

    /**
     * Returns the count of maintenance tickets in a specific building with the given status.
     *
     * <p>Used by the manager dashboard's KPI widget to display statistics such as
     * "3 open tickets in Building A" without loading full entity data.</p>
     *
     * @param buildingId the UUID of the building to count tickets for.
     * @param status     the {@link MaintenanceStatus} to count.
     * @return the number of tickets in the specified building with the given status.
     */
    long countByBuildingIdAndStatus(String buildingId, MaintenanceStatus status);

    /**
     * Returns the total count of maintenance tickets with the given status, across
     * all buildings.
     *
     * <p>Used by the Owner overview dashboard's global KPI widget (e.g., total open
     * tickets across the entire portfolio).</p>
     *
     * @param status the {@link MaintenanceStatus} to count.
     * @return the total number of tickets with the given status across all buildings.
     */
    long countByStatus(MaintenanceStatus status);
}
