package com.pgcrm.seeder;

import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.*;
import com.pgcrm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * DataSeeder — Runs at startup.
 * Creates the default Platform Admin account if not present.
 * Creates Tenant A with a full 100-bed layout and demo users per role.
 * Seeds rich transaction history: multiple guests, daily meal logs, invoices,
 * maintenance tickets, EB bills, and audit trail logs.
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

    private Building building;
    private User manager;

    private final InvoiceRepository invoiceRepository;
    private final DailyLogRepository dailyLogRepository;
    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final EbBillRepository ebBillRepository;
    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedDefaultBusiness();
        
        // Ensure all existing guests from previous seed runs have default preferences set to true
        for (Guest g : guestRepository.findAll()) {
            if (!g.isBreakfastPreference() && !g.isLunchPreference() && !g.isDinnerPreference()) {
                g.setBreakfastPreference(true);
                g.setLunchPreference(true);
                g.setDinnerPreference(true);
                g.setVegPreference(true);
                guestRepository.save(g);
                log.info("Updated default meal preferences to TRUE for guest: {}", g.getFullName());
            }
        }
    }

    @Getter @Setter @NoArgsConstructor
    public static class PgLayoutConfig {
        private List<BuildingConfig> buildings;
    }

    @Getter @Setter @NoArgsConstructor
    public static class BuildingConfig {
        private String name;
        private String address;
        private List<FloorConfig> floors;
    }

    @Getter @Setter @NoArgsConstructor
    public static class FloorConfig {
        private int number;
        private String label;
        private List<RoomConfig> rooms;
        private List<BlockConfig> blocks;
    }

    @Getter @Setter @NoArgsConstructor
    public static class BlockConfig {
        private String name;
        private List<RoomConfig> roomConfigurations;
    }

    @Getter @Setter @NoArgsConstructor
    public static class RoomConfig {
        private int sharing;
        private int count;
        private BigDecimal baseRent;
    }

    private boolean seedLayoutFromYaml() {
        try {
            ClassPathResource resource = new ClassPathResource("pg-layout.yml");
            java.io.File externalFile = new java.io.File("pg-layout.yml");
            InputStream is;
            if (externalFile.exists()) {
                log.info("⚙️ Loading layout configuration from external file: {}", externalFile.getAbsolutePath());
                is = new java.io.FileInputStream(externalFile);
            } else if (resource.exists()) {
                log.info("⚙️ Loading layout configuration from classpath pg-layout.yml");
                is = resource.getInputStream();
            } else {
                log.info("ℹ️ pg-layout.yml not found in working directory or classpath, using default hardcoded layout.");
                return false;
            }

            PgLayoutConfig config;
            try (InputStream input = is) {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                config = mapper.readValue(input, PgLayoutConfig.class);
            }

            if (config == null || config.getBuildings() == null || config.getBuildings().isEmpty()) {
                log.warn("⚠️ Invalid or empty pg-layout.yml, using default hardcoded layout.");
                return false;
            }

            log.info("⚙️ Seeding layout dynamically from pg-layout.yml...");

            int buildingIndex = 1;
            for (BuildingConfig bConfig : config.getBuildings()) {
                String buildingName = bConfig.getName() != null ? bConfig.getName() : "Main Building";
                String buildingAddress = bConfig.getAddress() != null ? bConfig.getAddress() : "123 Main St";

                // Check if building already exists to prevent duplicate layout insertion
                Building currentBuilding;
                java.util.Optional<Building> existingOpt = buildingRepository.findByNameIgnoreCase(buildingName);
                if (existingOpt.isPresent()) {
                    log.info("ℹ️ Building '{}' already exists in the database. Skipping layout seeding.", buildingName);
                    currentBuilding = existingOpt.get();
                } else {
                    currentBuilding = Building.builder()
                            .name(buildingName)
                            .address(buildingAddress)
                            .build();
                    currentBuilding = buildingRepository.save(currentBuilding);
                    log.info("✅ Building seeded: {}", buildingName);

                    // Seed floors, blocks, rooms, and beds for this new building
                    if (bConfig.getFloors() != null) {
                        for (FloorConfig fConfig : bConfig.getFloors()) {
                            Floor floor = createFloor(currentBuilding, fConfig.getNumber(), fConfig.getLabel());

                            // Seed direct rooms if any
                            if (fConfig.getRooms() != null) {
                                int roomIndex = 1;
                                for (RoomConfig rConfig : fConfig.getRooms()) {
                                    int sharing = rConfig.getSharing();
                                    int count = rConfig.getCount();
                                    BigDecimal baseRent = rConfig.getBaseRent();
                                    for (int i = 0; i < count; i++) {
                                        String floorNumPrefix = fConfig.getNumber() == 0 ? "G" : String.valueOf(fConfig.getNumber());
                                        String roomNum = floorNumPrefix + "-" + String.format("%02d", roomIndex);
                                        Room room = createRoom(floor, null, roomNum, sharing, baseRent);
                                        createBeds(room, sharing, floorNumPrefix);
                                        roomIndex++;
                                    }
                                }
                            }

                            // Seed blocks if any
                            if (fConfig.getBlocks() != null) {
                                for (BlockConfig blConfig : fConfig.getBlocks()) {
                                    Block block = Block.builder()
                                            .floor(floor)
                                            .name(blConfig.getName())
                                            .build();
                                    block = blockRepository.save(block);

                                    // Extract block code/letter (e.g. "A" from "Block A")
                                    String blockName = blConfig.getName();
                                    String blockCode = blockName;
                                    if (blockName.contains(" ")) {
                                        blockCode = blockName.substring(blockName.lastIndexOf(" ") + 1);
                                    }

                                    if (blConfig.getRoomConfigurations() != null) {
                                        for (RoomConfig rConfig : blConfig.getRoomConfigurations()) {
                                            int sharing = rConfig.getSharing();
                                            int count = rConfig.getCount();
                                            BigDecimal baseRent = rConfig.getBaseRent();

                                            for (int r = 1; r <= count; r++) {
                                                String roomNum;
                                                String bedPrefix;

                                                if (count == 1) {
                                                    roomNum = fConfig.getNumber() + blockCode + "-" + sharing + "S";
                                                    bedPrefix = fConfig.getNumber() + blockCode + sharing;
                                                } else {
                                                    roomNum = fConfig.getNumber() + blockCode + "-" + sharing + "S" + r;
                                                    bedPrefix = fConfig.getNumber() + blockCode + r;
                                                }

                                                Room room = createRoom(floor, block, roomNum, sharing, baseRent);
                                                createBeds(room, sharing, bedPrefix);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dynamically register a manager user for this building if they don't already exist
                String managerEmail = "manager" + (buildingIndex == 1 ? "" : buildingIndex) + "@pgcrm.com";
                User currentManager;
                java.util.Optional<User> existingManagerOpt = userRepository.findByEmailIgnoreCase(managerEmail);
                if (existingManagerOpt.isPresent()) {
                    log.info("ℹ️ Manager '{}' already exists in the database. Skipping creation.", managerEmail);
                    currentManager = existingManagerOpt.get();
                } else {
                    currentManager = createUser(managerEmail, "PG Manager for " + buildingName,
                            Role.PG_MANAGER, currentBuilding.getId());
                    log.info("✅ Manager seeded: {} (assigned to building {})", managerEmail, buildingName);
                }

                // Capture the first building & manager for backward compatibility with demo guest/invoice seeders
                if (buildingIndex == 1) {
                    this.building = currentBuilding;
                    this.manager = currentManager;
                }

                buildingIndex++;
            }

            log.info("✅ PG Layout successfully seeded dynamically from pg-layout.yml");
            return true;
        } catch (Exception e) {
            log.error("❌ Failed to parse pg-layout.yml: " + e.getMessage(), e);
            return false;
        }
    }

    private void seedDefaultBusiness() {
        // Create owner if not present
        User owner;
        java.util.Optional<User> ownerOpt = userRepository.findByEmailIgnoreCase("owner@pgcrm.com");
        if (ownerOpt.isPresent()) {
            owner = ownerOpt.get();
        } else {
            owner = createUser("owner@pgcrm.com", "PG Owner", Role.PG_OWNER, null);
            log.info("✅ PG_OWNER seeded: owner@pgcrm.com / Owner@123");
        }

        // Try seeding layout from YAML. If fails or not found, fall back to default hardcoded layout.
        if (!seedLayoutFromYaml()) {
            // Check if Main Building already exists in the database
            java.util.Optional<Building> existingOpt = buildingRepository.findByNameIgnoreCase("Main Building");
            if (existingOpt.isPresent()) {
                building = existingOpt.get();
                manager = userRepository.findByEmailIgnoreCase("manager@pgcrm.com").orElse(null);
                log.info("ℹ️ Default Building 'Main Building' already exists. Skipping fallback seeding.");
            } else {
                // 4. Building
                building = Building.builder()
                        .name("Main Building").address("123 Main St").build();
                building = buildingRepository.save(building);

                // 5. Manager (assigned to this building)
                manager = createUser("manager@pgcrm.com", "PG Manager",
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
            }
        }

        // Only seed demo transaction data (guests, logs, invoices, tickets, audit logs) for initial setup
        if (guestRepository.count() == 0) {
            log.info("⚙️ Seeding demo transaction data (guests, logs, invoices, tickets, audit logs) for initial setup...");

            // 8. Demo Guest user + Guest entity
            User guestUser = createUser("guest@pgcrm.com", "Demo Guest", Role.GUEST, null);
            // Find a vacant bed from the ground floor room and assign the demo guest to it
            Bed demoBed = extractAndOccupyBed("G");
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
                    .build();
            Guest g1 = guestRepository.save(demoGuest);
            log.info("✅ GUEST seeded: guest@pgcrm.com / Guest@123 (bed={})", demoBed != null ? demoBed.getBedLabel() : "Unassigned");

            // 9. Additional guests
            // Guest 2: John Doe
            User u2 = createUser("john.doe@pgcrm.com", "John Doe", Role.GUEST, null);
            Bed b2 = extractAndOccupyBed("1");
            Guest g2 = guestRepository.save(Guest.builder()
                    .user(u2)
                    .bed(b2)
                    .fullName("John Doe")
                    .email("john.doe@pgcrm.com")
                    .phone("9000000002")
                    .whatsappNumber("9000000002")
                    .advanceDeposit(BigDecimal.valueOf(8000))
                    .checkInDate(LocalDate.of(2026, 2, 15))
                    .kycStatus(KycStatus.VERIFIED)
                    .build());

            // Guest 3: Jane Smith
            User u3 = createUser("jane.smith@pgcrm.com", "Jane Smith", Role.GUEST, null);
            Bed b3 = extractAndOccupyBed("1");
            Guest g3 = guestRepository.save(Guest.builder()
                    .user(u3)
                    .bed(b3)
                    .fullName("Jane Smith")
                    .email("jane.smith@pgcrm.com")
                    .phone("9000000003")
                    .whatsappNumber("9000000003")
                    .advanceDeposit(BigDecimal.valueOf(8000))
                    .checkInDate(LocalDate.of(2026, 3, 1))
                    .kycStatus(KycStatus.VERIFIED)
                    .build());

            // Guest 4: Rahul Kumar
            User u4 = createUser("rahul.k@pgcrm.com", "Rahul Kumar", Role.GUEST, null);
            Bed b4 = extractAndOccupyBed("2");
            Guest g4 = guestRepository.save(Guest.builder()
                    .user(u4)
                    .bed(b4)
                    .fullName("Rahul Kumar")
                    .email("rahul.k@pgcrm.com")
                    .phone("9000000004")
                    .whatsappNumber("9000000004")
                    .advanceDeposit(BigDecimal.valueOf(8000))
                    .checkInDate(LocalDate.of(2026, 4, 10))
                    .kycStatus(KycStatus.PENDING)
                    .build());

            // Guest 5: Priya Sharma
            User u5 = createUser("priya.s@pgcrm.com", "Priya Sharma", Role.GUEST, null);
            Bed b5 = extractAndOccupyBed("2");
            Guest g5 = guestRepository.save(Guest.builder()
                    .user(u5)
                    .bed(b5)
                    .fullName("Priya Sharma")
                    .email("priya.s@pgcrm.com")
                    .phone("9000000005")
                    .whatsappNumber("9000000005")
                    .advanceDeposit(BigDecimal.valueOf(8000))
                    .checkInDate(LocalDate.of(2026, 5, 1))
                    .kycStatus(KycStatus.VERIFIED)
                    .build());

            // Guest 6: Amit Patel
            User u6 = createUser("amit.patel@pgcrm.com", "Amit Patel", Role.GUEST, null);
            Bed b6 = extractAndOccupyBed("3");
            Guest g6 = guestRepository.save(Guest.builder()
                    .user(u6)
                    .bed(b6)
                    .fullName("Amit Patel")
                    .email("amit.patel@pgcrm.com")
                    .phone("9000000006")
                    .whatsappNumber("9000000006")
                    .advanceDeposit(BigDecimal.valueOf(8000))
                    .checkInDate(LocalDate.of(2026, 5, 15))
                    .kycStatus(KycStatus.REJECTED)
                    .build());

            // Guest 7: Vikram Singh
            User u7 = createUser("vikram.s@pgcrm.com", "Vikram Singh", Role.GUEST, null);
            Bed b7 = extractAndOccupyBed("3");
            Guest g7 = guestRepository.save(Guest.builder()
                    .user(u7)
                    .bed(b7)
                    .fullName("Vikram Singh")
                    .email("vikram.s@pgcrm.com")
                    .phone("9000000007")
                    .whatsappNumber("9000000007")
                    .advanceDeposit(BigDecimal.valueOf(8000))
                    .checkInDate(LocalDate.of(2026, 5, 20))
                    .kycStatus(KycStatus.PENDING)
                    .build());

            // Guest 8: Sneha Reddy
            User u8 = createUser("sneha.r@pgcrm.com", "Sneha Reddy", Role.GUEST, null);
            Bed b8 = extractAndOccupyBed("3");
            Guest g8 = guestRepository.save(Guest.builder()
                    .user(u8)
                    .bed(b8)
                    .fullName("Sneha Reddy")
                    .email("sneha.r@pgcrm.com")
                    .phone("9000000008")
                    .whatsappNumber("9000000008")
                    .advanceDeposit(BigDecimal.valueOf(8000))
                    .checkInDate(LocalDate.of(2026, 5, 22))
                    .kycStatus(KycStatus.VERIFIED)
                    .build());

            List<Guest> seededGuests = List.of(g1, g2, g3, g4, g5, g6, g7, g8);
            for (Guest g : seededGuests) {
                g.setBreakfastPreference(true);
                g.setLunchPreference(true);
                g.setDinnerPreference(true);
                g.setVegPreference(true);
                guestRepository.save(g);
            }
            log.info("✅ 8 guests successfully seeded with default preferences.");

            // 10. Seed Daily Logs (meals and laundry)
            Random random = new Random();
            LocalDate today = LocalDate.now();
            for (Guest guest : seededGuests) {
                LocalDate start = guest.getCheckInDate();
                LocalDate limit = today.minusDays(60);
                if (start.isBefore(limit)) {
                    start = limit;
                }
                while (!start.isAfter(today)) {
                    dailyLogRepository.save(DailyLog.builder()
                            .guest(guest)
                            .logDate(start)
                            .breakfastOpted(random.nextBoolean())
                            .lunchOpted(random.nextBoolean())
                            .dinnerOpted(random.nextBoolean())
                            .isVeg(random.nextInt(10) < 7)
                            .omeletteCount(random.nextInt(3))
                            .boiledEggCount(random.nextInt(3))
                            .washingMachineCount(random.nextInt(3) == 0 ? 1 : 0)
                            .build());
                    start = start.plusDays(1);
                }
            }
            log.info("✅ Daily Logs (meal selections and laundry) seeded for all guests.");

            // 11. Seed Invoices
            // g1: Jan(PAID), Feb(PAID), Mar(PAID), Apr(PAID), May(PENDING - generated on June 1 or 2)
            createInvoiceForGuest(g1, 1, 2026, InvoiceStatus.PAID);
            createInvoiceForGuest(g1, 2, 2026, InvoiceStatus.PAID);
            createInvoiceForGuest(g1, 3, 2026, InvoiceStatus.PAID);
            createInvoiceForGuest(g1, 4, 2026, InvoiceStatus.PAID);

            // g2: Feb(PAID), Mar(PAID), Apr(PAID), May(PENDING - generated on June 1 or 2)
            createInvoiceForGuest(g2, 2, 2026, InvoiceStatus.PAID);
            createInvoiceForGuest(g2, 3, 2026, InvoiceStatus.PAID);
            createInvoiceForGuest(g2, 4, 2026, InvoiceStatus.PAID);

            // g3: Mar(PAID), Apr(PAID), May(PENDING - generated on June 1 or 2)
            createInvoiceForGuest(g3, 3, 2026, InvoiceStatus.PAID);
            createInvoiceForGuest(g3, 4, 2026, InvoiceStatus.PAID);

            // g4: Apr(PAID), May(PENDING - generated on June 1 or 2)
            createInvoiceForGuest(g4, 4, 2026, InvoiceStatus.PAID);

            // g5, g6, g7, g8: May(PENDING - generated on June 1 or 2)

            log.info("✅ Monthly invoices seeded for all guests (May 2026 left pending).");

            // 12. Seed Maintenance Tickets
            maintenanceTicketRepository.save(MaintenanceTicket.builder()
                    .raisedByGuest(g1)
                    .buildingId(building.getId())
                    .title("AC remote not responding")
                    .location("Room G-01")
                    .description("AC remote not responding despite changing batteries.")
                    .status(MaintenanceStatus.RESOLVED)
                    .priority(MaintenancePriority.LOW)
                    .createdAt(LocalDateTime.of(2026, 4, 15, 10, 0))
                    .resolvedAt(LocalDateTime.of(2026, 4, 15, 16, 30))
                    .build());

            maintenanceTicketRepository.save(MaintenanceTicket.builder()
                    .raisedByGuest(g2)
                    .buildingId(building.getId())
                    .title("Geyser leaking water")
                    .location("Bathroom 1st Floor")
                    .description("Geyser is leaking water and not heating properly.")
                    .status(MaintenanceStatus.OPEN)
                    .priority(MaintenancePriority.HIGH)
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .build());

            maintenanceTicketRepository.save(MaintenanceTicket.builder()
                    .raisedByGuest(g3)
                    .buildingId(building.getId())
                    .title("Wardrobe lock key bent")
                    .location("Room 1B-2S1")
                    .description("Wardrobe lock key is bent and hard to rotate.")
                    .status(MaintenanceStatus.RESOLVED)
                    .priority(MaintenancePriority.MEDIUM)
                    .createdAt(LocalDateTime.of(2026, 5, 2, 9, 15))
                    .resolvedAt(LocalDateTime.of(2026, 5, 3, 14, 0))
                    .build());

            maintenanceTicketRepository.save(MaintenanceTicket.builder()
                    .raisedByGuest(g4)
                    .buildingId(building.getId())
                    .title("Wi-Fi signal drop")
                    .location("Block A common area")
                    .description("Wi-Fi signal strength drops frequently in the evenings.")
                    .status(MaintenanceStatus.IN_PROGRESS)
                    .priority(MaintenancePriority.MEDIUM)
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build());

            maintenanceTicketRepository.save(MaintenanceTicket.builder()
                    .raisedByGuest(g5)
                    .buildingId(building.getId())
                    .title("Water leakage in restroom")
                    .location("Room 2B-2S1")
                    .description("Water leakage from flush tank in restroom.")
                    .status(MaintenanceStatus.OPEN)
                    .priority(MaintenancePriority.LOW)
                    .createdAt(LocalDateTime.now())
                    .build());
            log.info("✅ Maintenance tickets seeded.");

            // 13. Seed EB Bills
            List<Block> blocks = blockRepository.findAll();
            for (Block block : blocks) {
                EbBill ebBill = EbBill.builder()
                        .block(block)
                        .billingPeriodStart(LocalDate.of(2026, 4, 1))
                        .billingPeriodEnd(LocalDate.of(2026, 4, 30))
                        .totalAmount(BigDecimal.valueOf(1800.00))
                        .splitMethod("EQUAL")
                        .guestShares(new ArrayList<>())
                        .build();

                List<Guest> blockGuests = guestRepository.findAll().stream()
                        .filter(g -> g.getBed() != null && g.getBed().getRoom().getBlock() != null &&
                                g.getBed().getRoom().getBlock().getId().equals(block.getId()))
                        .toList();

                if (!blockGuests.isEmpty()) {
                    BigDecimal share = BigDecimal.valueOf(1800.00 / blockGuests.size());
                    for (Guest bg : blockGuests) {
                        ebBill.getGuestShares().add(EbBillGuest.builder()
                                .ebBill(ebBill)
                                .guest(bg)
                                .shareAmount(share)
                                .build());
                    }
                }
                ebBillRepository.save(ebBill);
            }
            log.info("✅ EB Bills seeded for all active blocks.");

            // 14. Seed Audit Trail Logs
            auditLogRepository.save(AuditLog.builder()
                    .actorId(owner.getId())
                    .actorRole("PG_OWNER")
                    .action(AuditAction.BUILDING_CREATED)
                    .entityType("Building")
                    .entityId(building.getId())
                    .description("Created Main Building.")
                    .timestamp(LocalDateTime.of(2026, 1, 1, 9, 0))
                    .build());

            auditLogRepository.save(AuditLog.builder()
                    .actorId(owner.getId())
                    .actorRole("PG_OWNER")
                    .action(AuditAction.MANAGER_CREATED)
                    .entityType("User")
                    .entityId(manager.getId())
                    .description("Created manager account: manager@pgcrm.com")
                    .timestamp(LocalDateTime.of(2026, 1, 1, 9, 30))
                    .build());

            auditLogRepository.save(AuditLog.builder()
                    .actorId(manager.getId())
                    .actorRole("PG_MANAGER")
                    .action(AuditAction.GUEST_CHECKIN)
                    .entityType("Guest")
                    .entityId(g1.getId())
                    .description("Checked in guest: Demo Guest to bed " + (g1.getBed() != null ? g1.getBed().getBedLabel() : "Unassigned"))
                    .timestamp(LocalDateTime.of(2026, 1, 1, 14, 0))
                    .build());

            auditLogRepository.save(AuditLog.builder()
                    .actorId(manager.getId())
                    .actorRole("PG_MANAGER")
                    .action(AuditAction.GUEST_CHECKIN)
                    .entityType("Guest")
                    .entityId(g2.getId())
                    .description("Checked in guest: John Doe to bed " + (b2 != null ? b2.getBedLabel() : "Unassigned"))
                    .timestamp(LocalDateTime.of(2026, 2, 15, 11, 0))
                    .build());

            auditLogRepository.save(AuditLog.builder()
                    .actorId(manager.getId())
                    .actorRole("PG_MANAGER")
                    .action(AuditAction.GUEST_CHECKIN)
                    .entityType("Guest")
                    .entityId(g3.getId())
                    .description("Checked in guest: Jane Smith to bed " + (b3 != null ? b3.getBedLabel() : "Unassigned"))
                    .timestamp(LocalDateTime.of(2026, 3, 1, 10, 30))
                    .build());

            auditLogRepository.save(AuditLog.builder()
                    .actorId(manager.getId())
                    .actorRole("PG_MANAGER")
                    .action(AuditAction.INVOICE_GENERATED)
                    .entityType("Invoice")
                    .description("Batch generated invoices for May 2026")
                    .timestamp(LocalDateTime.of(2026, 5, 1, 0, 5))
                    .build());
            log.info("✅ Audit trail logs successfully seeded.");
        } else {
            log.info("ℹ️ Database already has guest records. Skipping demo guest and transaction seeding.");
        }

        long totalBeds = bedRepository.countTotal();
        log.info("✅ Default Business seeded with {} beds (expected 100)", totalBeds);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  DEMO CREDENTIALS");
        log.info("  PG Owner       : owner@pgcrm.com  / Owner@123");
        log.info("  PG Manager     : manager@pgcrm.com / Manager@123");
        log.info("  Guest          : guest@pgcrm.com  / Guest@123");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private Bed extractAndOccupyBed(String preferredPrefix) {
        List<Bed> vacantBeds = bedRepository.findVacant();
        if (vacantBeds.isEmpty()) {
            return null;
        }
        Bed bed = vacantBeds.stream()
                .filter(b -> b.getBedLabel().startsWith(preferredPrefix))
                .findFirst()
                .orElse(vacantBeds.get(0));
        bed.setStatus(BedStatus.OCCUPIED);
        return bedRepository.save(bed);
    }

    private void createInvoiceForGuest(Guest guest, int month, int year, InvoiceStatus status) {
        BigDecimal baseRent = guest.getBed() != null ? guest.getBed().getRoom().getBaseRent() : BigDecimal.valueOf(7500);

        Invoice invoice = Invoice.builder()
                .guest(guest)
                .month(month)
                .year(year)
                .status(status)
                .dueDate(LocalDate.of(year, month, 10))
                .generatedAt(LocalDateTime.of(year, month, 1, 9, 0))
                .lineItems(new ArrayList<>())
                .build();

        if (status == InvoiceStatus.PAID) {
            invoice.setPaidAt(LocalDateTime.of(year, month, 5, 14, 30));
            invoice.setPaymentMethod("UPI");
        }

        InvoiceLineItem rentItem = InvoiceLineItem.builder()
                .invoice(invoice)
                .type(InvoiceLineType.RENT)
                .description("Monthly Room Rent")
                .amount(baseRent)
                .build();
        invoice.getLineItems().add(rentItem);

        InvoiceLineItem ebItem = InvoiceLineItem.builder()
                .invoice(invoice)
                .type(InvoiceLineType.EB)
                .description("EB Bill Share")
                .amount(BigDecimal.valueOf(450.00))
                .build();
        invoice.getLineItems().add(ebItem);

        InvoiceLineItem foodItem = InvoiceLineItem.builder()
                .invoice(invoice)
                .type(InvoiceLineType.FOOD)
                .description("Daily Meal Charges")
                .amount(BigDecimal.valueOf(1200.00))
                .build();
        invoice.getLineItems().add(foodItem);

        InvoiceLineItem laundryItem = InvoiceLineItem.builder()
                .invoice(invoice)
                .type(InvoiceLineType.LAUNDRY)
                .description("Washing Machine Usage")
                .amount(BigDecimal.valueOf(150.00))
                .build();
        invoice.getLineItems().add(laundryItem);

        BigDecimal total = rentItem.getAmount()
                .add(ebItem.getAmount())
                .add(foodItem.getAmount())
                .add(laundryItem.getAmount());
        invoice.setTotalAmount(total);

        invoiceRepository.save(invoice);
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
