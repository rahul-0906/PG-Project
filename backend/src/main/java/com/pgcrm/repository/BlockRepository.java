package com.pgcrm.repository;

import com.pgcrm.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BlockRepository extends JpaRepository<Block, String> {
    List<Block> findByFloorId(String floorId);
    List<Block> findByFloor_Building_Id(String buildingId);
}
