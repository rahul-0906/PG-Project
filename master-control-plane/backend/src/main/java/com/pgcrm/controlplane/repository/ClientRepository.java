package com.pgcrm.controlplane.repository;

import com.pgcrm.controlplane.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {
    Optional<Client> findByEmail(String email);

    @Query("SELECT COUNT(DISTINCT c) FROM Client c JOIN c.tenantInstances t WHERE t.status = :status")
    long countActiveClients(@Param("status") com.pgcrm.controlplane.entity.TenantStatus status);

    @Query("SELECT DISTINCT c FROM Client c LEFT JOIN FETCH c.tenantInstances t LEFT JOIN FETCH t.subscription")
    java.util.List<Client> findAllClientsWithDetails();
}
