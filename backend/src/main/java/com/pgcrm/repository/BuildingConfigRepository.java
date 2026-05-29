package com.pgcrm.repository;

import com.pgcrm.entity.BuildingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BuildingConfigRepository extends JpaRepository<BuildingConfig, String> {
}
