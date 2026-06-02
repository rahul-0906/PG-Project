package com.pgcrm.repository;

import com.pgcrm.entity.DailyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyLogRepository extends JpaRepository<DailyLog, String> {

    Optional<DailyLog> findByGuestIdAndLogDate(String guestId, LocalDate logDate);
    List<DailyLog> findByGuestId(String guestId);

    List<DailyLog> findByGuestIdAndLogDateBetween(String guestId, LocalDate start, LocalDate end);

    /** Kitchen food count: sum all opted meals for a given date in a building */
    @Query("""
        SELECT
            SUM(CASE WHEN d.breakfastOpted THEN 1 ELSE 0 END) as breakfast,
            SUM(CASE WHEN d.lunchOpted THEN 1 ELSE 0 END) as lunch,
            SUM(CASE WHEN d.dinnerOpted THEN 1 ELSE 0 END) as dinner,
            SUM(d.omeletteCount) as omelettes,
            SUM(d.boiledEggCount) as boiledEggs,
            SUM(d.washingMachineCount) as laundry
        FROM DailyLog d
        WHERE d.guest.bed.room.floor.building.id = :buildingId
        AND d.logDate = :date
        """)
    Object[] getFoodCountByBuildingAndDate(String buildingId, LocalDate date);

    /** Global food count for a date (single-tenant mode, no building filter) */
    @Query("""
        SELECT
            SUM(CASE WHEN d.breakfastOpted THEN 1 ELSE 0 END) as breakfast,
            SUM(CASE WHEN d.lunchOpted THEN 1 ELSE 0 END) as lunch,
            SUM(CASE WHEN d.dinnerOpted THEN 1 ELSE 0 END) as dinner,
            SUM(d.omeletteCount) as omelettes,
            SUM(d.boiledEggCount) as boiledEggs,
            SUM(d.washingMachineCount) as laundry
        FROM DailyLog d
        WHERE d.logDate = :date
        """)
    Object[] getFoodCountByDate(LocalDate date);

    List<DailyLog> findByLogDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT d FROM DailyLog d WHERE d.guest.id = :guestId AND (d.omeletteCount > 0 OR d.boiledEggCount > 0 OR d.washingMachineCount > 0) ORDER BY d.logDate DESC")
    List<DailyLog> findAddonsByGuestId(String guestId);
}

