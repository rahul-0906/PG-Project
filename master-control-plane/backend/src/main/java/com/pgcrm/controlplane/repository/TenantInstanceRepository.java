package com.pgcrm.controlplane.repository;

import com.pgcrm.controlplane.entity.TenantInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantInstanceRepository extends JpaRepository<TenantInstance, UUID> {
    Optional<TenantInstance> findByDomainName(String domainName);
    Optional<TenantInstance> findByRazorpayOrderId(String razorpayOrderId);
    long countByStatus(com.pgcrm.controlplane.entity.TenantStatus status);
}
