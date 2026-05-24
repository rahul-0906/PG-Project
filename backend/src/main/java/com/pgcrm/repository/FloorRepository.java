package com.pgcrm.repository;

import com.pgcrm.entity.Floor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FloorRepository extends JpaRepository<Floor, String> {
    List<Floor> findByBuildingId(String buildingId);
}
