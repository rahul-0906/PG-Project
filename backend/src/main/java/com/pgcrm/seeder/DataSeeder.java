package com.pgcrm.seeder;

import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.BedStatus;
import com.pgcrm.entity.enums.KycStatus;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DataSeeder — Runs at startup.
 * Creates the default Platform Admin account if not present.
 * Creates Tenant A with a full 100-bed layout and one demo user per role.
 * Guarded by Tenant.demoSeeded flag to never re-seed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final BlockRepository blockRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final GuestRepository guestRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedDefaultBusiness();
    }

    private void seedDefaultBusiness() {
        if (buildingRepository.count() > 0) return; // already seeded

        // 3. Demo Users
        User owner = createUser("owner@pgcrm.com", "PG Owner", Role.PG_OWNER, null);
        log.info("✅ PG_OWNER seeded: owner@pgcrm.com / Owner@123");

        // 4. Building
        Building building = Building.builder()
                .name("Main Building").address("123 Main St").build();
        building = buildingRepository.save(building);

        // 5. Manager (assigned to this building)
        User manager = createUser("manager@pgcrm.com", "PG Manager",
                Role.PG_MANAGER, building.getId());
        log.info("✅ PG_MANAGER seeded: manager@pgcrm.com / Manager@123");

        // 6. Ground Floor — no blocks, one 4-sharing room (4 beds)
        Floor groundFloor = createFloor(building, 0, "Ground Floor");
        Room groundRoom = createRoom(groundFloor, null, "G-01", 4, BigDecimal.valueOf(7500));
        createBeds(groundRoom, 4, "G");

        // 7. Floors 1, 2, 3 — 4 blocks each, each block: 2×2-sharing + 1×4-sharing
        String[] blockNames = {"A", "B", "C", "D"};
        for (int floorNum = 1; floorNum <= 3; floorNum++) {
            Floor floor = createFloor(building, floorNum, "Floor " + floorNum);
            for (int b = 0; b < 4; b++) {
                Block block = Block.builder()
                        .floor(floor).name("Block " + blockNames[b]).build();
                block = blockRepository.save(block);

                // 2 × 2-sharing rooms
                for (int r = 1; r <= 2; r++) {
                    String roomNum = floorNum + blockNames[b] + "-2S" + r;
                    Room room = createRoom(floor, block, roomNum, 2, BigDecimal.valueOf(9000));
                    createBeds(room, 2, floorNum + blockNames[b] + r + "");
                }
                // 1 × 4-sharing room
                String roomNum4 = floorNum + blockNames[b] + "-4S";
                Room room4 = createRoom(floor, block, roomNum4, 4, BigDecimal.valueOf(7500));
                createBeds(room4, 4, floorNum + blockNames[b] + "4");
            }
        }

        // 8. Demo Guest user + Guest entity
        User guestUser = createUser("guest@pgcrm.com", "Demo Guest", Role.GUEST, null);
        // Find a vacant bed from the ground floor room and assign the demo guest to it
        Bed demoBed = bedRepository.findVacant().stream()
                .filter(bed -> bed.getBedLabel().startsWith("G"))
                .findFirst()
                .orElse(bedRepository.findVacant().get(0));
        demoBed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(demoBed);
        Guest demoGuest = Guest.builder()
                .user(guestUser)
                .bed(demoBed)
                .fullName("Demo Guest")
                .email("guest@pgcrm.com")
                .phone("9000000001")
                .whatsappNumber("9000000001")
                .advanceDeposit(BigDecimal.valueOf(8000))
                .checkInDate(LocalDate.of(2026, 1, 1))
                .kycStatus(KycStatus.VERIFIED)
                .build(); // active defaults to true via @Builder.Default
        guestRepository.save(demoGuest);
        log.info("✅ GUEST seeded: guest@pgcrm.com / Guest@123 (bed={})", demoBed.getBedLabel());

        long totalBeds = bedRepository.countTotal();
        log.info("✅ Default Business seeded with {} beds (expected 100)", totalBeds);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  DEMO CREDENTIALS");
        log.info("  PG Owner       : owner@pgcrm.com  / Owner@123");
        log.info("  PG Manager     : manager@pgcrm.com / Manager@123");
        log.info("  Guest          : guest@pgcrm.com  / Guest@123");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private User createUser(String email, String name, Role role, String branchId) {
        if (userRepository.existsByEmail(email)) return userRepository.findByEmailIgnoreCase(email).get();
        String defaultPass = switch (role) {
            case PG_OWNER -> "Owner@123";
            case PG_MANAGER -> "Manager@123";
            case GUEST -> "Guest@123";
            default -> "Admin@123";
        };
        return userRepository.save(User.builder()
                .email(email).password(passwordEncoder.encode(defaultPass))
                .role(role).fullName(name).branchId(branchId).active(true).build());
    }

    private Floor createFloor(Building building, int num, String label) {
        return floorRepository.save(Floor.builder()
                .building(building).floorNumber(num).floorLabel(label).build());
    }

    private Room createRoom(Floor floor, Block block, String number, int sharing, BigDecimal rent) {
        return roomRepository.save(Room.builder()
                .floor(floor).block(block)
                .roomNumber(number).sharingType(sharing).baseRent(rent).build());
    }

    private void createBeds(Room room, int count, String prefix) {
        for (int i = 1; i <= count; i++) {
            bedRepository.save(Bed.builder()
                    .room(room).bedLabel(prefix + i).build());
        }
    }
}
