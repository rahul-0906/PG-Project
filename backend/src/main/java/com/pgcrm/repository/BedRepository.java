package com.pgcrm.repository;

import com.pgcrm.entity.Bed;
import com.pgcrm.entity.enums.BedStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BedRepository extends JpaRepository<Bed, String> {
    List<Bed> findByRoomId(String roomId);
    List<Bed> findByStatus(BedStatus status);

    @Query("SELECT COUNT(b) FROM Bed b WHERE b.status = 'VACANT'")
    long countVacant();

    @Query("SELECT COUNT(b) FROM Bed b")
    long countTotal();

    @Query("SELECT b FROM Bed b WHERE b.status = 'VACANT' ORDER BY b.bedLabel")
    List<Bed> findVacant();

    @Query("SELECT COUNT(b) FROM Bed b WHERE b.room.block.floor.building.id = :buildingId AND b.status = 'VACANT'")
    long countVacantByBuildingId(String buildingId);

    @Query("SELECT COUNT(b) FROM Bed b WHERE b.room.block.floor.building.id = :buildingId")
    long countTotalByBuildingId(String buildingId);
}
