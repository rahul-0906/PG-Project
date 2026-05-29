package com.pgcrm.controller;

import com.pgcrm.entity.*;
import com.pgcrm.repository.*;
import com.pgcrm.service.BuildingSetupService;
import com.pgcrm.dto.UserResponse;
import com.pgcrm.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
public class PgOwnerController {

    private final BuildingRepository buildingRepository;
    private final BedRepository bedRepository;
    private final GuestRepository guestRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final BuildingSetupService buildingSetupService;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        long totalBeds = bedRepository.countTotal();
        long vacantBeds = bedRepository.countVacant();
        long activeGuests = guestRepository.countActive();
        return ResponseEntity.ok(Map.of(
                "totalBeds", totalBeds,
                "vacantBeds", vacantBeds,
                "occupiedBeds", totalBeds - vacantBeds,
                "activeGuests", activeGuests
        ));
    }

    @GetMapping("/branches")
    public ResponseEntity<List<Building>> getBranches() {
        return ResponseEntity.ok(buildingRepository.findAll());
    }

    /** Creates a new building with floors, blocks, rooms, and beds in one transaction. */
    @PostMapping("/buildings")
    public ResponseEntity<?> createBuilding(@RequestBody BuildingSetupService.BuildingSetupRequest req) {
        try {
            BuildingSetupService.BuildingCreationResult result = buildingSetupService.createBuilding(req);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/buildings/{buildingId}")
    public ResponseEntity<?> getBuildingLayout(@PathVariable String buildingId) {
        try {
            return ResponseEntity.ok(buildingSetupService.getBuildingLayout(buildingId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/buildings/{buildingId}")
    public ResponseEntity<?> updateBuilding(@PathVariable String buildingId, @RequestBody BuildingSetupService.BuildingEditRequest req) {
        try {
            BuildingSetupService.BuildingCreationResult result = buildingSetupService.updateBuilding(buildingId, req);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/managers")
    public ResponseEntity<UserResponse> createManager(@RequestBody Map<String, String> body) {
        String defaultPassword = "Manager@123";
        User manager = User.builder()
                .email(body.get("email"))
                .fullName(body.get("fullName"))
                .branchId(body.get("branchId"))
                .role(com.pgcrm.entity.enums.Role.PG_MANAGER)
                .password(passwordEncoder.encode(defaultPassword))
                .phone(body.getOrDefault("phone", ""))
                .active(true)
                .firstLogin(true)
                .mustChangePassword(true)
                .build();
        return ResponseEntity.ok(UserResponse.fromEntity(userRepository.save(manager)));
    }

    @GetMapping("/managers")
    public ResponseEntity<List<UserResponse>> getManagers() {
        List<User> managers = userRepository.findByRole(com.pgcrm.entity.enums.Role.PG_MANAGER);
        List<UserResponse> responses = managers.stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/managers/{id}")
    public ResponseEntity<UserResponse> updateManager(@PathVariable String id, @RequestBody Map<String, String> body) {
        User manager = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found: " + id));
        if (body.containsKey("fullName")) manager.setFullName(body.get("fullName"));
        if (body.containsKey("email")) manager.setEmail(body.get("email"));
        if (body.containsKey("branchId")) manager.setBranchId(body.get("branchId"));
        if (body.containsKey("active")) manager.setActive(Boolean.parseBoolean(body.get("active")));
        return ResponseEntity.ok(UserResponse.fromEntity(userRepository.save(manager)));
    }

    @GetMapping("/config")
    public ResponseEntity<String> getConfig() {
        return ResponseEntity.ok("Use /api/system/config");
    }
}
