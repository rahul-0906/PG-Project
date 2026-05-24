package com.pgcrm.repository;

import com.pgcrm.entity.Guest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GuestRepository extends JpaRepository<Guest, String> {

    List<Guest> findByActiveTrue();

    Optional<Guest> findByUserId(String userId);

    @Query("""
        SELECT g FROM Guest g
        WHERE g.active = true
        AND g.bed.room.block.id = :blockId
        AND g.checkInDate <= :periodEnd
        AND (g.actualCheckOutDate IS NULL OR g.actualCheckOutDate >= :periodStart)
        """)
    List<Guest> findActiveGuestsInBlock(String blockId, LocalDate periodStart, LocalDate periodEnd);

    @Query("SELECT COUNT(g) FROM Guest g WHERE g.active = true")
    long countActive();

    @Query("""
        SELECT g FROM Guest g
        WHERE g.active = true
        AND g.bed.room.block.floor.building.id = :buildingId
        """)
    List<Guest> findActiveGuestsByBuildingId(String buildingId);

    // ── Reports ───────────────────────────────────────────────────

    @Query("""
        SELECT COUNT(g) FROM Guest g
        WHERE YEAR(g.checkInDate) = :year
          AND MONTH(g.checkInDate) = :month
        """)
    long countCheckInsByMonthYear(
        @Param("year") int year, @Param("month") int month);

    @Query("""
        SELECT COUNT(g) FROM Guest g
        WHERE g.actualCheckOutDate IS NOT NULL
          AND YEAR(g.actualCheckOutDate) = :year
          AND MONTH(g.actualCheckOutDate) = :month
        """)
    long countCheckOutsByMonthYear(
        @Param("year") int year, @Param("month") int month);

    // ── Payment Reminder — find active guests with unpaid invoices ─

    @Query("""
        SELECT g FROM Guest g
        WHERE g.active = true
        """)
    List<Guest> findActive();
}
