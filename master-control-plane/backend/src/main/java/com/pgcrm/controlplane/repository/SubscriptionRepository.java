package com.pgcrm.controlplane.repository;

import com.pgcrm.controlplane.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    @Query("SELECT s FROM Subscription s JOIN FETCH s.tenantInstance t JOIN FETCH t.client c " +
           "WHERE s.licenseState = 'ACTIVE' AND s.amcExpiryDate = :targetDate")
    List<Subscription> findActiveExpiringOn(@Param("targetDate") LocalDate targetDate);

    @Query("SELECT s FROM Subscription s JOIN FETCH s.tenantInstance t JOIN FETCH t.client c " +
           "WHERE s.licenseState = 'ACTIVE' AND s.amcExpiryDate < :today")
    List<Subscription> findActiveExpiredBefore(@Param("today") LocalDate today);
}
