package com.pgcrm.service;

import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.BedStatus;
import com.pgcrm.repository.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles dynamic building creation from the Owner UI.
 * Replaces the role previously served by seeding from pg-layout.yml.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BuildingSetupService {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final BlockRepository blockRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final BuildingConfigRepository buildingConfigRepository;
    private final com.pgcrm.config.SystemConfigProperties systemConfig;

    // ── Request DTO ────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor
    public static class BuildingSetupRequest {
        @com.fasterxml.jackson.annotation.JsonProperty("name")
        private String name;
        @com.fasterxml.jackson.annotation.JsonProperty("address")
        private String address;
        @com.fasterxml.jackson.annotation.JsonProperty("foodIncludedInRent")
        private Boolean foodIncludedInRent;
        @com.fasterxml.jackson.annotation.JsonProperty("allowMealCancellations")
        private Boolean allowMealCancellations;
        @com.fasterxml.jackson.annotation.JsonProperty("breakfastPrice")
        private java.math.BigDecimal breakfastPrice;
        @com.fasterxml.jackson.annotation.JsonProperty("lunchPrice")
        private java.math.BigDecimal lunchPrice;
        @com.fasterxml.jackson.annotation.JsonProperty("dinnerPrice")
        private java.math.BigDecimal dinnerPrice;
        @com.fasterxml.jackson.annotation.JsonProperty("omelettePrice")
        private java.math.BigDecimal omelettePrice;
        @com.fasterxml.jackson.annotation.JsonProperty("boiledEggPrice")
        private java.math.BigDecimal boiledEggPrice;
        @com.fasterxml.jackson.annotation.JsonProperty("washingMachinePrice")
        private java.math.BigDecimal washingMachinePrice;
        @com.fasterxml.jackson.annotation.JsonProperty("ebSplitMethod")
        private String ebSplitMethod;
        @com.fasterxml.jackson.annotation.JsonProperty("breakfastCutoffTime")
        private java.time.LocalTime breakfastCutoffTime;
        @com.fasterxml.jackson.annotation.JsonProperty("dinnerCutoffTime")
        private java.time.LocalTime dinnerCutoffTime;
        @com.fasterxml.jackson.annotation.JsonProperty("isPreviousDay")
        private Boolean isPreviousDay;
        @com.fasterxml.jackson.annotation.JsonProperty("floors")
        private List<FloorSetup> floors = new ArrayList<>();
    }

    @Getter @Setter @NoArgsConstructor
    public static class FloorSetup {
        private int number;           // 0 = Ground, 1 = First, ...
        private String label;         // e.g. "Ground Floor", "1st Floor"
        private List<RoomConfig> rooms = new ArrayList<>();    // standalone rooms (no block)
        private List<BlockSetup> blocks = new ArrayList<>();
    }

    @Getter @Setter @NoArgsConstructor
    public static class BlockSetup {
        private String name;          // e.g. "Block A"
        private List<RoomConfig> roomConfigs = new ArrayList<>();
    }

    @Getter @Setter @NoArgsConstructor
    public static class RoomConfig {
        private int sharing;          // 1, 2, 3, or 4
        private int count;            // number of rooms with this config
        private BigDecimal baseRent;
        private List<String> roomNumbers = new ArrayList<>();
    }

    // ── Response summary ───────────────────────────────────────────

    @Getter @Setter
    public static class BuildingCreationResult {
        private String buildingId;
        private String buildingName;
        private int totalFloors;
        private int totalBlocks;
        private int totalRooms;
        private int totalBeds;
    }

    // ── Core Creation Logic ────────────────────────────────────────

    @Transactional
    public BuildingCreationResult createBuilding(BuildingSetupRequest req) {
        // Validate
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("Building name is required");
        }
        if (buildingRepository.findByNameIgnoreCase(req.getName()).isPresent()) {
            throw new IllegalArgumentException("A building with that name already exists");
        }

        // Create building
        Building building = buildingRepository.save(
            Building.builder()
                .name(req.getName().trim())
                .address(req.getAddress() != null ? req.getAddress().trim() : "")
                .build()
        );
        log.info("✅ Owner created building: {}", building.getName());

        // Create default building config
        com.pgcrm.entity.enums.EbSplitMethod splitMethod = com.pgcrm.entity.enums.EbSplitMethod.EQUAL_SPLIT;
        try {
            if (req.getEbSplitMethod() != null) {
                splitMethod = com.pgcrm.entity.enums.EbSplitMethod.valueOf(req.getEbSplitMethod());
            } else if (systemConfig.getRules().getEbSplitMethod() != null) {
                splitMethod = com.pgcrm.entity.enums.EbSplitMethod.valueOf(systemConfig.getRules().getEbSplitMethod());
            }
        } catch (Exception ignored) {}

        BuildingConfig config = BuildingConfig.builder()
                .building(building)
                .foodIncludedInRent(req.getFoodIncludedInRent() != null ? req.getFoodIncludedInRent() : false)
                .allowMealCancellations(req.getAllowMealCancellations() != null ? req.getAllowMealCancellations() : true)
                .breakfastPrice(req.getBreakfastPrice() != null ? req.getBreakfastPrice() : systemConfig.getPricing().getBreakfast())
                .lunchPrice(req.getLunchPrice() != null ? req.getLunchPrice() : systemConfig.getPricing().getLunch())
                .dinnerPrice(req.getDinnerPrice() != null ? req.getDinnerPrice() : systemConfig.getPricing().getDinner())
                .omelettePrice(req.getOmelettePrice() != null ? req.getOmelettePrice() : systemConfig.getPricing().getOmelette())
                .boiledEggPrice(req.getBoiledEggPrice() != null ? req.getBoiledEggPrice() : systemConfig.getPricing().getBoiledEgg())
                .washingMachinePrice(req.getWashingMachinePrice() != null ? req.getWashingMachinePrice() : systemConfig.getPricing().getWashingMachine())
                .ebSplitMethod(splitMethod)
                .breakfastCutoffTime(req.getBreakfastCutoffTime() != null ? req.getBreakfastCutoffTime() : java.time.LocalTime.of(22, 0))
                .dinnerCutoffTime(req.getDinnerCutoffTime() != null ? req.getDinnerCutoffTime() : java.time.LocalTime.of(14, 0))
                .isPreviousDay(req.getIsPreviousDay() != null ? req.getIsPreviousDay() : true)
                .build();
        buildingConfigRepository.save(config);

        int totalBlocks = 0, totalRooms = 0, totalBeds = 0;

        for (FloorSetup fSetup : req.getFloors()) {
            String floorLabel = fSetup.getLabel() != null ? fSetup.getLabel()
                    : (fSetup.getNumber() == 0 ? "Ground Floor" : fSetup.getNumber() + " Floor");

            Floor floor = floorRepository.save(
                Floor.builder()
                    .building(building)
                    .floorNumber(fSetup.getNumber())
                    .floorLabel(floorLabel)
                    .build()
            );

            // Standalone rooms on this floor
            if (fSetup.getRooms() != null) {
                int rIdx = 1;
                for (RoomConfig rc : fSetup.getRooms()) {
                    for (int i = 0; i < rc.getCount(); i++) {
                        String roomNum;
                        if (rc.getRoomNumbers() != null && rc.getRoomNumbers().size() > i && rc.getRoomNumbers().get(i) != null && !rc.getRoomNumbers().get(i).isBlank()) {
                            roomNum = rc.getRoomNumbers().get(i).trim();
                        } else {
                            String prefix = fSetup.getNumber() == 0 ? "G" : String.valueOf(fSetup.getNumber());
                            roomNum = prefix + "-" + String.format("%02d", rIdx);
                        }

                        Room room = roomRepository.save(Room.builder()
                            .floor(floor).block(null)
                            .roomNumber(roomNum)
                            .sharingType(rc.getSharing())
                            .baseRent(rc.getBaseRent())
                            .build());

                        String bedPrefix;
                        if (rc.getRoomNumbers() != null && rc.getRoomNumbers().size() > i && rc.getRoomNumbers().get(i) != null && !rc.getRoomNumbers().get(i).isBlank()) {
                            bedPrefix = "R-" + roomNum + "-B";
                        } else {
                            String prefix = fSetup.getNumber() == 0 ? "G" : String.valueOf(fSetup.getNumber());
                            bedPrefix = prefix + String.format("%02d", rIdx);
                        }

                        createBeds(room, rc.getSharing(), bedPrefix);
                        totalRooms++;
                        totalBeds += rc.getSharing();
                        rIdx++;
                    }
                }
            }

            // Blocks on this floor
            if (fSetup.getBlocks() != null) {
                for (BlockSetup bSetup : fSetup.getBlocks()) {
                    Block block = blockRepository.save(
                        Block.builder().floor(floor).name(bSetup.getName()).build()
                    );
                    totalBlocks++;

                    // Extract block code letter from name (e.g. "A" from "Block A")
                    String blockCode = bSetup.getName();
                    if (blockCode.contains(" ")) {
                        blockCode = blockCode.substring(blockCode.lastIndexOf(" ") + 1);
                    }

                    if (bSetup.getRoomConfigs() != null) {
                        for (RoomConfig rc : bSetup.getRoomConfigs()) {
                            for (int r = 1; r <= rc.getCount(); r++) {
                                String roomNum;
                                if (rc.getRoomNumbers() != null && rc.getRoomNumbers().size() > (r - 1) && rc.getRoomNumbers().get(r - 1) != null && !rc.getRoomNumbers().get(r - 1).isBlank()) {
                                    roomNum = rc.getRoomNumbers().get(r - 1).trim();
                                } else {
                                    roomNum = fSetup.getNumber() + blockCode + "-" + rc.getSharing() + "S" + (rc.getCount() > 1 ? r : "");
                                }

                                Room room = roomRepository.save(Room.builder()
                                    .floor(floor).block(block)
                                    .roomNumber(roomNum)
                                    .sharingType(rc.getSharing())
                                    .baseRent(rc.getBaseRent())
                                    .build());

                                String bedPrefix;
                                if (rc.getRoomNumbers() != null && rc.getRoomNumbers().size() > (r - 1) && rc.getRoomNumbers().get(r - 1) != null && !rc.getRoomNumbers().get(r - 1).isBlank()) {
                                    bedPrefix = "R-" + roomNum + "-B";
                                } else {
                                    bedPrefix = fSetup.getNumber() + blockCode + (rc.getCount() > 1 ? r : rc.getSharing());
                                }

                                createBeds(room, rc.getSharing(), bedPrefix);
                                totalRooms++;
                                totalBeds += rc.getSharing();
                            }
                        }
                    }
                }
            }
        }

        BuildingCreationResult result = new BuildingCreationResult();
        result.setBuildingId(building.getId());
        result.setBuildingName(building.getName());
        result.setTotalFloors(req.getFloors().size());
        result.setTotalBlocks(totalBlocks);
        result.setTotalRooms(totalRooms);
        result.setTotalBeds(totalBeds);
        log.info("✅ Building '{}' created: {} floors, {} blocks, {} rooms, {} beds",
                building.getName(), result.getTotalFloors(), totalBlocks, totalRooms, totalBeds);
        return result;
    }

    private void createBeds(Room room, int count, String prefix) {
        for (int i = 1; i <= count; i++) {
            bedRepository.save(Bed.builder()
                .room(room)
                .bedLabel(prefix + i)
                .status(BedStatus.VACANT)
                .build());
        }
    }

    // ── Edit DTOs ──────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor
    public static class BuildingEditRequest {
        private String name;
        private String address;
        private Boolean foodIncludedInRent;
        private Boolean allowMealCancellations;
        private java.math.BigDecimal breakfastPrice;
        private java.math.BigDecimal lunchPrice;
        private java.math.BigDecimal dinnerPrice;
        private java.math.BigDecimal omelettePrice;
        private java.math.BigDecimal boiledEggPrice;
        private java.math.BigDecimal washingMachinePrice;
        private String ebSplitMethod;
        private java.time.LocalTime breakfastCutoffTime;
        private java.time.LocalTime dinnerCutoffTime;
        private Boolean isPreviousDay;
        private List<FloorEdit> floors = new ArrayList<>();
    }

    @Getter @Setter @NoArgsConstructor
    public static class FloorEdit {
        private String id;
        private int number;
        private String label;
        private List<BlockEdit> blocks = new ArrayList<>();
        private List<RoomEdit> rooms = new ArrayList<>();
    }

    @Getter @Setter @NoArgsConstructor
    public static class BlockEdit {
        private String id;
        private String name;
        private List<RoomEdit> rooms = new ArrayList<>();
    }

    @Getter @Setter @NoArgsConstructor
    public static class RoomEdit {
        private String id;
        private String roomNumber;
        private int sharing;
        private BigDecimal baseRent;
        private int occupiedBedsCount;
    }

    // ── Edit Layout Logic ──────────────────────────────────────────

    public BuildingEditRequest getBuildingLayout(String buildingId) {
        Building b = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Building not found"));

        BuildingEditRequest req = new BuildingEditRequest();
        req.setName(b.getName());
        req.setAddress(b.getAddress());

        // Fetch and map BuildingConfig details
        BuildingConfig config = buildingConfigRepository.findById(buildingId).orElse(null);
        if (config != null) {
            req.setFoodIncludedInRent(config.isFoodIncludedInRent());
            req.setAllowMealCancellations(config.isAllowMealCancellations());
            req.setBreakfastPrice(config.getBreakfastPrice());
            req.setLunchPrice(config.getLunchPrice());
            req.setDinnerPrice(config.getDinnerPrice());
            req.setOmelettePrice(config.getOmelettePrice());
            req.setBoiledEggPrice(config.getBoiledEggPrice());
            req.setWashingMachinePrice(config.getWashingMachinePrice());
            req.setEbSplitMethod(config.getEbSplitMethod() != null ? config.getEbSplitMethod().name() : null);
            req.setBreakfastCutoffTime(config.getBreakfastCutoffTime());
            req.setDinnerCutoffTime(config.getDinnerCutoffTime());
            req.setIsPreviousDay(config.isPreviousDay());
        }

        List<Floor> floors = floorRepository.findByBuildingId(buildingId);
        floors.sort(java.util.Comparator.comparingInt(Floor::getFloorNumber));

        for (Floor f : floors) {
            FloorEdit fe = new FloorEdit();
            fe.setId(f.getId());
            fe.setNumber(f.getFloorNumber());
            fe.setLabel(f.getFloorLabel());

            // Blocks on this floor
            List<Block> blocks = blockRepository.findByFloorId(f.getId());
            for (Block bl : blocks) {
                BlockEdit be = new BlockEdit();
                be.setId(bl.getId());
                be.setName(bl.getName());

                // Rooms in this block
                List<Room> rooms = roomRepository.findByBlockId(bl.getId());
                for (Room r : rooms) {
                    RoomEdit re = new RoomEdit();
                    re.setId(r.getId());
                    re.setRoomNumber(r.getRoomNumber());
                    re.setSharing(r.getSharingType());
                    re.setBaseRent(r.getBaseRent());
                    List<Bed> beds = bedRepository.findByRoomId(r.getId());
                    long occupied = beds.stream().filter(bed -> bed.getStatus() == BedStatus.OCCUPIED).count();
                    re.setOccupiedBedsCount((int) occupied);
                    be.getRooms().add(re);
                }
                fe.getBlocks().add(be);
            }

            // Standalone rooms (no block)
            List<Room> standaloneRooms = roomRepository.findByFloorId(f.getId()).stream()
                    .filter(r -> r.getBlock() == null)
                    .toList();
            for (Room r : standaloneRooms) {
                RoomEdit re = new RoomEdit();
                re.setId(r.getId());
                re.setRoomNumber(r.getRoomNumber());
                re.setSharing(r.getSharingType());
                re.setBaseRent(r.getBaseRent());
                List<Bed> beds = bedRepository.findByRoomId(r.getId());
                long occupied = beds.stream().filter(bed -> bed.getStatus() == BedStatus.OCCUPIED).count();
                re.setOccupiedBedsCount((int) occupied);
                fe.getRooms().add(re);
            }

            req.getFloors().add(fe);
        }

        return req;
    }

    @Transactional
    public BuildingCreationResult updateBuilding(String buildingId, BuildingEditRequest req) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Building not found"));

        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("Building name is required");
        }
        if (!building.getName().equalsIgnoreCase(req.getName().trim())) {
            if (buildingRepository.findByNameIgnoreCase(req.getName().trim()).isPresent()) {
                throw new IllegalArgumentException("A building with that name already exists");
            }
        }
        building.setName(req.getName().trim());
        building.setAddress(req.getAddress() != null ? req.getAddress().trim() : "");
        buildingRepository.save(building);

        // Update BuildingConfig
        BuildingConfig config = buildingConfigRepository.findById(buildingId)
                .orElseGet(() -> BuildingConfig.builder().building(building).build());

        if (req.getFoodIncludedInRent() != null) config.setFoodIncludedInRent(req.getFoodIncludedInRent());
        if (req.getAllowMealCancellations() != null) config.setAllowMealCancellations(req.getAllowMealCancellations());
        if (req.getBreakfastPrice() != null) config.setBreakfastPrice(req.getBreakfastPrice());
        if (req.getLunchPrice() != null) config.setLunchPrice(req.getLunchPrice());
        if (req.getDinnerPrice() != null) config.setDinnerPrice(req.getDinnerPrice());
        if (req.getOmelettePrice() != null) config.setOmelettePrice(req.getOmelettePrice());
        if (req.getBoiledEggPrice() != null) config.setBoiledEggPrice(req.getBoiledEggPrice());
        if (req.getWashingMachinePrice() != null) config.setWashingMachinePrice(req.getWashingMachinePrice());
        if (req.getBreakfastCutoffTime() != null) config.setBreakfastCutoffTime(req.getBreakfastCutoffTime());
        if (req.getDinnerCutoffTime() != null) config.setDinnerCutoffTime(req.getDinnerCutoffTime());
        if (req.getIsPreviousDay() != null) config.setPreviousDay(req.getIsPreviousDay());

        if (req.getEbSplitMethod() != null) {
            try {
                config.setEbSplitMethod(com.pgcrm.entity.enums.EbSplitMethod.valueOf(req.getEbSplitMethod()));
            } catch (Exception ignored) {}
        }
        buildingConfigRepository.save(config);

        List<String> activeFloorIds = new ArrayList<>();
        List<String> activeBlockIds = new ArrayList<>();
        List<String> activeRoomIds = new ArrayList<>();

        int totalBlocks = 0, totalRooms = 0, totalBeds = 0;

        for (FloorEdit fe : req.getFloors()) {
            Floor floor;
            if (fe.getId() != null && !fe.getId().isBlank()) {
                floor = floorRepository.findById(fe.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Floor not found: " + fe.getId()));
                floor.setFloorNumber(fe.getNumber());
                floor.setFloorLabel(fe.getLabel());
                floor = floorRepository.save(floor);
                activeFloorIds.add(floor.getId());
            } else {
                floor = floorRepository.save(Floor.builder()
                        .building(building)
                        .floorNumber(fe.getNumber())
                        .floorLabel(fe.getLabel())
                        .build());
                activeFloorIds.add(floor.getId());
            }

            // Standalone rooms
            if (fe.getRooms() != null) {
                for (RoomEdit re : fe.getRooms()) {
                    Room room;
                    if (re.getId() != null && !re.getId().isBlank()) {
                        room = roomRepository.findById(re.getId())
                                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + re.getId()));
                        room.setRoomNumber(re.getRoomNumber());
                        room.setBaseRent(re.getBaseRent());
                        if (room.getSharingType() != re.getSharing()) {
                            adjustRoomBeds(room, re.getSharing());
                            room.setSharingType(re.getSharing());
                        }
                        room = roomRepository.save(room);
                        activeRoomIds.add(room.getId());
                    } else {
                        room = roomRepository.save(Room.builder()
                                .floor(floor)
                                .block(null)
                                .roomNumber(re.getRoomNumber())
                                .sharingType(re.getSharing())
                                .baseRent(re.getBaseRent())
                                .build());
                        createBeds(room, re.getSharing(), "R-" + re.getRoomNumber() + "-B");
                        activeRoomIds.add(room.getId());
                    }
                    totalRooms++;
                    totalBeds += re.getSharing();
                }
            }

            // Blocks on this floor
            if (fe.getBlocks() != null) {
                for (BlockEdit be : fe.getBlocks()) {
                    Block block;
                    if (be.getId() != null && !be.getId().isBlank()) {
                        block = blockRepository.findById(be.getId())
                                .orElseThrow(() -> new IllegalArgumentException("Block not found: " + be.getId()));
                        block.setName(be.getName());
                        block = blockRepository.save(block);
                        activeBlockIds.add(block.getId());
                    } else {
                        block = blockRepository.save(Block.builder()
                                .floor(floor)
                                .name(be.getName())
                                .build());
                        activeBlockIds.add(block.getId());
                    }
                    totalBlocks++;

                    // Rooms in this block
                    if (be.getRooms() != null) {
                        for (RoomEdit re : be.getRooms()) {
                            Room room;
                            if (re.getId() != null && !re.getId().isBlank()) {
                                room = roomRepository.findById(re.getId())
                                        .orElseThrow(() -> new IllegalArgumentException("Room not found: " + re.getId()));
                                room.setRoomNumber(re.getRoomNumber());
                                room.setBaseRent(re.getBaseRent());
                                if (room.getSharingType() != re.getSharing()) {
                                    adjustRoomBeds(room, re.getSharing());
                                    room.setSharingType(re.getSharing());
                                }
                                room = roomRepository.save(room);
                                activeRoomIds.add(room.getId());
                            } else {
                                room = roomRepository.save(Room.builder()
                                        .floor(floor)
                                        .block(block)
                                        .roomNumber(re.getRoomNumber())
                                        .sharingType(re.getSharing())
                                        .baseRent(re.getBaseRent())
                                        .build());
                                createBeds(room, re.getSharing(), "R-" + re.getRoomNumber() + "-B");
                                activeRoomIds.add(room.getId());
                            }
                            totalRooms++;
                            totalBeds += re.getSharing();
                        }
                    }
                }
            }
        }

        // Clean up deleted rooms
        List<Room> allRooms = roomRepository.findByFloor_Building_Id(buildingId);
        for (Room r : allRooms) {
            if (!activeRoomIds.contains(r.getId())) {
                List<Bed> beds = bedRepository.findByRoomId(r.getId());
                boolean occupied = beds.stream().anyMatch(bed -> bed.getStatus() == BedStatus.OCCUPIED);
                if (occupied) {
                    throw new IllegalArgumentException("Cannot delete room " + r.getRoomNumber() + " because it contains occupied beds.");
                }
                bedRepository.deleteAll(beds);
                roomRepository.delete(r);
            }
        }

        // Clean up deleted blocks
        List<Block> allBlocks = blockRepository.findByFloor_Building_Id(buildingId);
        for (Block b : allBlocks) {
            if (!activeBlockIds.contains(b.getId())) {
                blockRepository.delete(b);
            }
        }

        // Clean up deleted floors
        List<Floor> allFloors = floorRepository.findByBuildingId(buildingId);
        for (Floor f : allFloors) {
            if (!activeFloorIds.contains(f.getId())) {
                floorRepository.delete(f);
            }
        }

        BuildingCreationResult result = new BuildingCreationResult();
        result.setBuildingId(building.getId());
        result.setBuildingName(building.getName());
        result.setTotalFloors(activeFloorIds.size());
        result.setTotalBlocks(totalBlocks);
        result.setTotalRooms(totalRooms);
        result.setTotalBeds(totalBeds);
        return result;
    }

    private void adjustRoomBeds(Room room, int newSharing) {
        List<Bed> beds = bedRepository.findByRoomId(room.getId());
        int currentCount = beds.size();
        if (currentCount == newSharing) return;

        if (newSharing > currentCount) {
            int diff = newSharing - currentCount;
            for (int i = 1; i <= diff; i++) {
                bedRepository.save(Bed.builder()
                        .room(room)
                        .bedLabel("R-" + room.getRoomNumber() + "-B" + (currentCount + i))
                        .status(BedStatus.VACANT)
                        .build());
            }
        } else {
            long occupiedCount = beds.stream().filter(b -> b.getStatus() == BedStatus.OCCUPIED).count();
            if (occupiedCount > newSharing) {
                throw new IllegalArgumentException("Cannot reduce room " + room.getRoomNumber() 
                        + " to " + newSharing + " sharing because it has " + occupiedCount + " occupied beds.");
            }

            int diff = currentCount - newSharing;
            int removed = 0;
            for (Bed b : beds) {
                if (removed >= diff) break;
                if (b.getStatus() == BedStatus.VACANT) {
                    bedRepository.delete(b);
                    removed++;
                }
            }
            if (removed < diff) {
                throw new IllegalArgumentException("Cannot reduce room " + room.getRoomNumber()
                        + " sharing: insufficient vacant beds to remove.");
            }
        }
    }
}
