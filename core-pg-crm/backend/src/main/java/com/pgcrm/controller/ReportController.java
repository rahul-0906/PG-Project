package com.pgcrm.controller;

import com.pgcrm.entity.AuditLog;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.repository.AuditLogRepository;
import com.pgcrm.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AuditLogRepository auditLogRepository;

    // ── Revenue ───────────────────────────────────────────────────

    @GetMapping("/revenue")
    public ResponseEntity<List<Map<String, Object>>> getRevenue(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") int year) {
        return ResponseEntity.ok(reportService.getMonthlyRevenueSummary(year));
    }

    // ── Occupancy ─────────────────────────────────────────────────

    @GetMapping("/occupancy")
    public ResponseEntity<List<Map<String, Object>>> getOccupancy(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") int year) {
        return ResponseEntity.ok(reportService.getOccupancyReport(year));
    }

    // ── Guest Turnover ────────────────────────────────────────────

    @GetMapping("/guests")
    public ResponseEntity<List<Map<String, Object>>> getGuestTurnover(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") int year) {
        return ResponseEntity.ok(reportService.getGuestTurnoverReport(year));
    }

    // ── Payment Summary ───────────────────────────────────────────

    @GetMapping("/payments")
    public ResponseEntity<Map<String, Object>> getPaymentSummary(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") int year) {
        return ResponseEntity.ok(reportService.getPaymentSummary(year));
    }

    // ── Audit Trail ───────────────────────────────────────────────

    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLog>> getAuditTrail(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDateTime.now().minusYears(1);
        LocalDateTime toDt   = to   != null ? to.atTime(23, 59, 59) : LocalDateTime.now();

        Page<AuditLog> result = auditLogRepository.findByFilters(action, fromDt, toDt, pageable);
        return ResponseEntity.ok(result);
    }

    /** CSV export of audit log for year-end reporting */
    @GetMapping("/audit/export")
    public ResponseEntity<byte[]> exportAuditCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDateTime.now().minusYears(1);
        LocalDateTime toDt   = to   != null ? to.atTime(23, 59, 59) : LocalDateTime.now();

        List<AuditLog> logs = auditLogRepository.findForExport(fromDt, toDt);

        try (StringWriter sw = new StringWriter();
             CSVPrinter csv = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                     .setHeader("Timestamp", "Action", "Entity", "Description", "Actor Role", "Metadata")
                     .build())) {

            for (AuditLog log : logs) {
                csv.printRecord(
                    log.getTimestamp(),
                    log.getAction(),
                    log.getEntityType() + ":" + log.getEntityId(),
                    log.getDescription(),
                    log.getActorRole(),
                    log.getMetadata()
                );
            }

            byte[] bytes = sw.toString().getBytes();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(bytes);
        } catch (Exception e) {
            throw new RuntimeException("CSV export failed: " + e.getMessage());
        }
    }
}
