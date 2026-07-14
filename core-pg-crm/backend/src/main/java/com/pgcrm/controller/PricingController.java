package com.pgcrm.controller;

import com.pgcrm.entity.Room;
import com.pgcrm.entity.BuildingConfig;
import com.pgcrm.repository.BuildingRepository;
import com.pgcrm.repository.FloorRepository;
import com.pgcrm.repository.BlockRepository;
import com.pgcrm.repository.RoomRepository;
import com.pgcrm.repository.BuildingConfigRepository;
import com.pgcrm.service.PricingService;
import com.pgcrm.config.SystemConfigProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Manager Pricing API — view and update per-building price overrides.
 */
@RestController
@RequestMapping("/api/manager/pricing")
@RequiredArgsConstructor
@Slf4j
public class PricingController {

    private final PricingService pricingService;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final BlockRepository blockRepository;
    private final RoomRepository roomRepository;
    private final BuildingConfigRepository buildingConfigRepository;
    private final SystemConfigProperties systemConfig;

    /**
     * GET /api/manager/pricing?buildingId=xxx
     * Returns all effective pricing keys for the given building.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getPricing(
            @RequestParam(required = false) String buildingId,
            @RequestAttribute(required = false) String branchId) {

        // Managers use their assigned building; owners can pass any buildingId
        String effectiveBuildingId = buildingId != null ? buildingId : branchId;

        Map<String, BigDecimal> foodPricing = pricingService.getFullPricingMap(effectiveBuildingId);

        // Room rents: grouped by floor → block → room
        List<Map<String, Object>> buildings = new ArrayList<>();
        buildingRepository.findAll().forEach(building -> {
            if (effectiveBuildingId != null && !building.getId().equals(effectiveBuildingId)) return;
            Map<String, Object> bMap = new LinkedHashMap<>();
            bMap.put("id", building.getId());
            bMap.put("name", building.getName());

            List<Map<String, Object>> floorList = new ArrayList<>();
            floorRepository.findByBuildingId(building.getId()).forEach(floor -> {
                Map<String, Object> fMap = new LinkedHashMap<>();
                fMap.put("id", floor.getId());
                fMap.put("label", floor.getFloorLabel());
                fMap.put("number", floor.getFloorNumber());

                List<Map<String, Object>> blockList = new ArrayList<>();
                blockRepository.findByFloorId(floor.getId()).forEach(block -> {
                    Map<String, Object> blMap = new LinkedHashMap<>();
                    blMap.put("id", block.getId());
                    blMap.put("name", block.getName());

                    List<Map<String, Object>> roomList = new ArrayList<>();
                    roomRepository.findByBlockId(block.getId()).forEach(room -> roomList.add(roomToMap(room)));
                    blMap.put("rooms", roomList);
                    blockList.add(blMap);
                });
                fMap.put("blocks", blockList);

                // Standalone rooms (no block)
                List<Map<String, Object>> standaloneRooms = new ArrayList<>();
                roomRepository.findByFloorId(floor.getId()).stream()
                        .filter(r -> r.getBlock() == null)
                        .forEach(r -> standaloneRooms.add(roomToMap(r)));
                fMap.put("standaloneRooms", standaloneRooms);

                floorList.add(fMap);
            });
            bMap.put("floors", floorList);
            buildings.add(bMap);
        });

        boolean schedulerEnabled = pricingService.isBillingSchedulerEnabled(effectiveBuildingId);

        boolean foodIncludedInRent = false;
        boolean allowMealCancellations = true;
        boolean offerOmelette = true;
        boolean offerBoiledEgg = true;
        String ebSplitMethod = "EQUAL_SPLIT";
        java.time.LocalTime breakfastCutoffTime = systemConfig.getRules().getBreakfastLockoutTime();
        java.time.LocalTime dinnerCutoffTime = systemConfig.getRules().getDinnerLockoutTime();
        boolean isPreviousDay = true;
        String allowedPaymentModes = "BOTH";
        String omeletteLabel = "Omelette";
        String boiledEggLabel = "Boiled Egg";
        String washingMachineLabel = "Washing Machine";

        if (effectiveBuildingId != null) {
            Optional<BuildingConfig> configOpt = buildingConfigRepository.findById(effectiveBuildingId);
            if (configOpt.isPresent()) {
                BuildingConfig cfg = configOpt.get();
                foodIncludedInRent = cfg.isFoodIncludedInRent();
                allowMealCancellations = cfg.isAllowMealCancellations();
                offerOmelette = cfg.isOfferOmelette();
                offerBoiledEgg = cfg.isOfferBoiledEgg();
                if (cfg.getEbSplitMethod() != null) {
                    ebSplitMethod = cfg.getEbSplitMethod().name();
                }
                if (cfg.getBreakfastCutoffTime() != null) {
                    breakfastCutoffTime = cfg.getBreakfastCutoffTime();
                }
                if (cfg.getDinnerCutoffTime() != null) {
                    dinnerCutoffTime = cfg.getDinnerCutoffTime();
                }
                isPreviousDay = cfg.isPreviousDay();
                if (cfg.getAllowedPaymentModes() != null) {
                    allowedPaymentModes = cfg.getAllowedPaymentModes();
                }
                if (cfg.getOmeletteLabel() != null) {
                    omeletteLabel = cfg.getOmeletteLabel();
                }
                if (cfg.getBoiledEggLabel() != null) {
                    boiledEggLabel = cfg.getBoiledEggLabel();
                }
                if (cfg.getWashingMachineLabel() != null) {
                    washingMachineLabel = cfg.getWashingMachineLabel();
                }
            } else {
                foodIncludedInRent = systemConfig.getRules().isFoodIncludedInRent();
                allowMealCancellations = systemConfig.getRules().isAllowMealCancellations();
                ebSplitMethod = systemConfig.getRules().getEbSplitMethod();
            }
        } else {
            foodIncludedInRent = systemConfig.getRules().isFoodIncludedInRent();
            allowMealCancellations = systemConfig.getRules().isAllowMealCancellations();
            ebSplitMethod = systemConfig.getRules().getEbSplitMethod();
        }

        Map<String, Object> pricingDetails = new LinkedHashMap<>();
        pricingDetails.put("foodPricing", foodPricing);
        pricingDetails.put("buildings", buildings);
        pricingDetails.put("billingSchedulerEnabled", schedulerEnabled);
        pricingDetails.put("foodIncludedInRent", foodIncludedInRent);
        pricingDetails.put("allowMealCancellations", allowMealCancellations);
        pricingDetails.put("offerOmelette", offerOmelette);
        pricingDetails.put("offerBoiledEgg", offerBoiledEgg);
        pricingDetails.put("ebSplitMethod", ebSplitMethod);
        pricingDetails.put("breakfastCutoffTime", breakfastCutoffTime.toString());
        pricingDetails.put("dinnerCutoffTime", dinnerCutoffTime.toString());
        pricingDetails.put("isPreviousDay", isPreviousDay);
        pricingDetails.put("allowedPaymentModes", allowedPaymentModes);
        pricingDetails.put("omeletteLabel", omeletteLabel);
        pricingDetails.put("boiledEggLabel", boiledEggLabel);
        pricingDetails.put("washingMachineLabel", washingMachineLabel);
        return ResponseEntity.ok(pricingDetails);
    }

    /**
     * PUT /api/manager/pricing/{key}?buildingId=xxx
     * Updates a single food/addon price for a building.
     */
    @PutMapping("/{key}")
    public ResponseEntity<?> updateFoodPrice(
            @PathVariable String key,
            @RequestParam(required = false) String buildingId,
            @RequestAttribute(required = false) String branchId,
            @RequestAttribute(required = false) String userId,
            @RequestBody Map<String, Object> body) {

        String effectiveBuildingId = buildingId != null ? buildingId : branchId;
        log.info("Manager '{}' requested food price override update for key: {} in building: {}", userId, key, effectiveBuildingId);
        if (effectiveBuildingId == null) {
            log.warn("Building ID is missing during food price update");
            return ResponseEntity.badRequest().body(Map.of("error", "Building ID required"));
        }

        Object valObj = body.get("value");
        if (valObj == null) {
            log.warn("Value field missing in food price update request");
            return ResponseEntity.badRequest().body(Map.of("error", "'value' field required"));
        }

        BigDecimal value;
        try {
            value = new BigDecimal(valObj.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid value '{}' for food price update", valObj);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid numeric value"));
        }

        Object res = pricingService.upsert(effectiveBuildingId, key, value, userId);
        log.info("Successfully updated food price override for key: {} to value: {} in building: {}", key, value, effectiveBuildingId);
        return ResponseEntity.ok(res);
    }

    /**
     * PUT /api/manager/pricing/config?buildingId=xxx
     * Updates building configurations (rules and split methods).
     */
    @PutMapping("/config")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> updateBuildingConfig(
            @RequestParam(required = false) String buildingId,
            @RequestAttribute(required = false) String branchId,
            @RequestBody Map<String, Object> body) {
        String effectiveBuildingId = buildingId != null ? buildingId : branchId;
        log.info("Manager requested update to building config for building: {}. Fields: {}", effectiveBuildingId, body.keySet());
        if (effectiveBuildingId == null) {
            log.warn("Building ID is missing during building config update");
            return ResponseEntity.badRequest().body(Map.of("error", "Building ID required"));
        }

        BuildingConfig cfg = buildingConfigRepository.findById(effectiveBuildingId)
                .orElseGet(() -> {
                    com.pgcrm.entity.Building building = buildingRepository.findById(effectiveBuildingId)
                            .orElseThrow(() -> new IllegalArgumentException("Building not found: " + effectiveBuildingId));
                    BuildingConfig newCfg = BuildingConfig.builder().building(building).build();
                    building.setBuildingConfig(newCfg);
                    return newCfg;
                });

        if (body.containsKey("foodIncludedInRent")) {
            cfg.setFoodIncludedInRent(Boolean.parseBoolean(body.get("foodIncludedInRent").toString()));
        }
        if (body.containsKey("allowMealCancellations")) {
            cfg.setAllowMealCancellations(Boolean.parseBoolean(body.get("allowMealCancellations").toString()));
        }
        if (body.containsKey("ebSplitMethod")) {
            cfg.setEbSplitMethod(com.pgcrm.entity.enums.EbSplitMethod.valueOf(body.get("ebSplitMethod").toString()));
        }
        if (body.containsKey("breakfastCutoffTime")) {
            cfg.setBreakfastCutoffTime(java.time.LocalTime.parse(body.get("breakfastCutoffTime").toString()));
        }
        if (body.containsKey("dinnerCutoffTime")) {
            cfg.setDinnerCutoffTime(java.time.LocalTime.parse(body.get("dinnerCutoffTime").toString()));
        }
        if (body.containsKey("isPreviousDay")) {
            cfg.setPreviousDay(Boolean.parseBoolean(body.get("isPreviousDay").toString()));
        }
        if (body.containsKey("allowedPaymentModes")) {
            cfg.setAllowedPaymentModes(body.get("allowedPaymentModes").toString());
        }
        if (body.containsKey("offerOmelette")) {
            cfg.setOfferOmelette(Boolean.parseBoolean(body.get("offerOmelette").toString()));
        }
        if (body.containsKey("offerBoiledEgg")) {
            cfg.setOfferBoiledEgg(Boolean.parseBoolean(body.get("offerBoiledEgg").toString()));
        }
        if (body.containsKey("omeletteLabel")) {
            cfg.setOmeletteLabel(body.get("omeletteLabel").toString());
        }
        if (body.containsKey("boiledEggLabel")) {
            cfg.setBoiledEggLabel(body.get("boiledEggLabel").toString());
        }
        if (body.containsKey("washingMachineLabel")) {
            cfg.setWashingMachineLabel(body.get("washingMachineLabel").toString());
        }

        BuildingConfig savedCfg = buildingConfigRepository.save(cfg);
        log.info("Successfully updated building config for building: {}", effectiveBuildingId);
        return ResponseEntity.ok(savedCfg);
    }

    /**
     * PUT /api/manager/pricing/rooms/{roomId}/rent
     * Updates the base rent of a specific room.
     */
    @PutMapping("/rooms/{roomId}/rent")
    public ResponseEntity<?> updateRoomRent(
            @PathVariable String roomId,
            @RequestBody Map<String, Object> body) {
        log.info("Manager requested base rent update for room ID: {}", roomId);

        Optional<Room> opt = roomRepository.findById(roomId);
        if (opt.isEmpty()) {
            log.warn("Room ID: {} not found during rent update", roomId);
            return ResponseEntity.notFound().build();
        }

        Object valObj = body.get("baseRent");
        if (valObj == null) {
            log.warn("baseRent field missing for room ID: {}", roomId);
            return ResponseEntity.badRequest().body(Map.of("error", "'baseRent' field required"));
        }

        BigDecimal rent;
        try {
            rent = new BigDecimal(valObj.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid rent value '{}' for room ID: {}", valObj, roomId);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid numeric value"));
        }

        Room room = opt.get();
        room.setBaseRent(rent);
        Room savedRoom = roomRepository.save(room);
        log.info("Successfully updated rent for room ID: {} to {}", roomId, rent);
        return ResponseEntity.ok(roomToMap(savedRoom));
    }

    /**
     * PUT /api/manager/pricing/sharing/{sharingType}/rent?buildingId=xxx&floorId=yyy
     * Updates the base rent of all rooms in a building (or specific floor) matching the sharingType.
     */
    @PutMapping("/sharing/{sharingType}/rent")
    public ResponseEntity<?> updateSharingRent(
            @PathVariable int sharingType,
            @RequestParam(required = false) String buildingId,
            @RequestParam(required = false) String floorId,
            @RequestAttribute(required = false) String branchId,
            @RequestBody Map<String, Object> body) {

        String effectiveBuildingId = buildingId != null ? buildingId : branchId;
        log.info("Manager requested rent update for sharing type {} in building: {}, floor: {}", sharingType, effectiveBuildingId, floorId);
        if (effectiveBuildingId == null) {
            log.warn("Building ID is missing during sharing rent update");
            return ResponseEntity.badRequest().body(Map.of("error", "Building ID required"));
        }

        Object valObj = body.get("baseRent");
        if (valObj == null) {
            log.warn("baseRent field missing in sharing rent update request");
            return ResponseEntity.badRequest().body(Map.of("error", "'baseRent' field required"));
        }

        BigDecimal rent;
        try {
            rent = new BigDecimal(valObj.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid rent value '{}' for sharing rent update", valObj);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid numeric value"));
        }

        List<Room> rooms;
        if (floorId != null && !floorId.isBlank()) {
            rooms = roomRepository.findByFloorId(floorId);
        } else {
            rooms = roomRepository.findByFloor_Building_Id(effectiveBuildingId);
        }

        List<Room> updated = new ArrayList<>();
        for (Room r : rooms) {
            if (r.getSharingType() == sharingType) {
                r.setBaseRent(rent);
                updated.add(roomRepository.save(r));
            }
        }

        log.info("Successfully updated rent for sharing type {} in building: {}. Rooms updated: {}", sharingType, effectiveBuildingId, updated.size());
        return ResponseEntity.ok(Map.of("updatedCount", updated.size(), "baseRent", rent));
    }

    private Map<String, Object> roomToMap(Room room) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", room.getId());
        m.put("roomNumber", room.getRoomNumber());
        m.put("sharingType", room.getSharingType());
        m.put("baseRent", room.getBaseRent());
        m.put("isAc", room.isAc());
        return m;
    }
}
