package com.pgcrm.repository;

import com.pgcrm.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {
    List<Room> findByBlockId(String blockId);
    List<Room> findByFloorId(String floorId);
}
