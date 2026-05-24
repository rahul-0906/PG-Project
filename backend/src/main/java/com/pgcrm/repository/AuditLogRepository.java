package com.pgcrm.repository;

import com.pgcrm.entity.AuditLog;
import com.pgcrm.entity.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:action IS NULL OR a.action = :action) " +
           "AND a.timestamp BETWEEN :from AND :to " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findByFilters(
        @Param("action")   AuditAction action,
        @Param("from")     LocalDateTime from,
        @Param("to")       LocalDateTime to,
        Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "a.timestamp BETWEEN :from AND :to ORDER BY a.timestamp DESC")
    java.util.List<AuditLog> findForExport(
        @Param("from")     LocalDateTime from,
        @Param("to")       LocalDateTime to);
}
