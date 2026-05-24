package com.pgcrm.repository;

import com.pgcrm.entity.MaintenanceTicket;
import com.pgcrm.entity.enums.MaintenanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, String> {
    List<MaintenanceTicket> findByBuildingId(String buildingId);
    List<MaintenanceTicket> findByStatus(MaintenanceStatus status);
    List<MaintenanceTicket> findByBuildingIdAndStatus(String buildingId, MaintenanceStatus status);
    long countByBuildingIdAndStatus(String buildingId, MaintenanceStatus status);
    long countByStatus(MaintenanceStatus status);
}

