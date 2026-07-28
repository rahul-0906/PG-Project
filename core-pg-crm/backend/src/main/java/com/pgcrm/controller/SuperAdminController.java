package com.pgcrm.controller;

import com.pgcrm.dto.FinancialOverrideRequest;
import com.pgcrm.dto.SuperAdminSummaryResponse;
import com.pgcrm.dto.UserRoleUpdateRequest;
import com.pgcrm.entity.AuditLog;
import com.pgcrm.entity.User;
import com.pgcrm.service.SuperAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final com.pgcrm.service.BuildingSetupService buildingSetupService;

    @GetMapping("/summary")
    public ResponseEntity<SuperAdminSummaryResponse> getSummary() {
        return ResponseEntity.ok(superAdminService.getSummary());
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(superAdminService.getAllUsers());
    }

    @PostMapping("/owners")
    public ResponseEntity<User> createPgOwner(@Valid @RequestBody com.pgcrm.dto.CreatePgOwnerRequest request) {
        return ResponseEntity.ok(superAdminService.createPgOwner(request));
    }

    @PostMapping("/buildings")
    public ResponseEntity<?> createBuilding(@RequestBody com.pgcrm.service.BuildingSetupService.BuildingSetupRequest req) {
        return ResponseEntity.ok(buildingSetupService.createBuilding(req));
    }

    @PutMapping("/users/role")
    public ResponseEntity<User> updateUserRole(@Valid @RequestBody UserRoleUpdateRequest request,
                                               Authentication authentication) {
        String actorId = authentication != null ? authentication.getName() : "SUPER_ADMIN";
        return ResponseEntity.ok(superAdminService.updateUserRole(request, actorId));
    }

    @PostMapping("/financial-override")
    public ResponseEntity<Map<String, Object>> applyFinancialOverride(@Valid @RequestBody FinancialOverrideRequest request,
                                                                      Authentication authentication) {
        String actorId = authentication != null ? authentication.getName() : "SUPER_ADMIN";
        superAdminService.applyFinancialOverride(request, actorId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Financial override applied successfully by Super Admin",
                "overrideType", request.getOverrideType(),
                "amount", request.getAmount()
        ));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(superAdminService.getAuditLogs(search, PageRequest.of(page, size)));
    }

    @PostMapping("/guests/{guestId}/anonymize")
    public ResponseEntity<Map<String, Object>> anonymizeGuest(@PathVariable String guestId,
                                                             Authentication authentication) {
        String actorId = authentication != null ? authentication.getName() : "SUPER_ADMIN";
        superAdminService.anonymizeGuest(guestId, actorId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Guest PII anonymized successfully",
                "guestId", guestId
        ));
    }

    @DeleteMapping({"/users/{userId}", "/owners/{userId}"})
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable String userId,
                                                          Authentication authentication) {
        String actorId = authentication != null ? authentication.getName() : "SUPER_ADMIN";
        superAdminService.deleteUser(userId, actorId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "User account deleted successfully",
                "userId", userId
        ));
    }
}
