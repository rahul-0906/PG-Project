package com.pgcrm.repository;

import com.pgcrm.entity.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantSettingsRepository extends JpaRepository<TenantSettings, String> {

    /**
     * Retrieves the single tenant settings record for the current schema.
     */
    default Optional<TenantSettings> findCurrentSettings() {
        return findAll().stream().findFirst();
    }
}
