package com.pgcrm.controller;

import com.pgcrm.entity.*;
import com.pgcrm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @PostMapping("/managers")
    public ResponseEntity<User> createManager(@RequestBody Map<String, String> body) {
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
        return ResponseEntity.ok(userRepository.save(manager));
    }

    @GetMapping("/managers")
    public ResponseEntity<List<User>> getManagers() {
        return ResponseEntity.ok(userRepository.findByRole(com.pgcrm.entity.enums.Role.PG_MANAGER));
    }

    @GetMapping("/config")
    public ResponseEntity<String> getConfig() {
        return ResponseEntity.ok("Use /api/system/config");
    }
}
