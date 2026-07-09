package com.pgcrm.repository;

import com.pgcrm.entity.MeterReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@link MeterReading} entity.
 */
@Repository
public interface MeterReadingRepository extends JpaRepository<MeterReading, String> {

    /** Finds meter readings for a room. */
    List<MeterReading> findByRoomId(String roomId);

    /** Finds meter readings for a room of a specific type (e.g. MIGRATION_BASELINE). */
    List<MeterReading> findByRoomIdAndType(String roomId, String type);
}
