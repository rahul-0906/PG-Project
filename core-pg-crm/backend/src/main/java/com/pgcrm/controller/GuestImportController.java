package com.pgcrm.controller;

import com.pgcrm.dto.ImportSummaryDTO;
import com.pgcrm.service.BulkImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller exposing endpoints for bulk importing guests and rooms.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class GuestImportController {

    private final BulkImportService bulkImportService;

    /**
     * Endpoint to parse an Excel sheet containing guest profiles and rooms,
     * onboarding them in a single click transaction.
     */
    @PostMapping(value = "/api/manager/guests/bulk-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportSummaryDTO> bulkImport(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute(required = false) String branchId,
            @RequestParam(value = "buildingId", required = false) String buildingId) {

        log.info("📥 Bulk guest import request received. File: '{}', Size: {} bytes",
                file.getOriginalFilename(), file.getSize());

        String targetBuildingId = (buildingId != null && !buildingId.isEmpty()) ? buildingId : branchId;
        if (targetBuildingId == null || targetBuildingId.isEmpty()) {
            log.error("Bulk import failed: Target building ID context is missing.");
            throw new IllegalArgumentException("Building ID must be specified for bulk import. Scope your request or provide the buildingId parameter.");
        }

        ImportSummaryDTO summary = bulkImportService.importGuests(file, targetBuildingId);
        return ResponseEntity.ok(summary);
    }
}
