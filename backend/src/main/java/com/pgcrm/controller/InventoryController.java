package com.pgcrm.controller;

import com.pgcrm.entity.*;
import com.pgcrm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final BlockRepository blockRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;

    // ── Buildings ─────────────────────────────────────────────────

    @GetMapping("/buildings")
    public ResponseEntity<List<Building>> getBuildings() {
        return ResponseEntity.ok(buildingRepository.findAll());
    }

    @PostMapping("/buildings")
    public ResponseEntity<Building> addBuilding(@RequestBody Map<String, String> body) {
        Building b = Building.builder()
                .name(body.get("name"))
                .address(body.get("address"))
                .build();
        return ResponseEntity.ok(buildingRepository.save(b));
    }

    // ── Floors ────────────────────────────────────────────────────

    @GetMapping("/buildings/{buildingId}/floors")
    public ResponseEntity<List<Floor>> getFloors(@PathVariable String buildingId) {
        return ResponseEntity.ok(floorRepository.findByBuildingId(buildingId));
    }

    @PostMapping("/buildings/{buildingId}/floors")
    public ResponseEntity<Floor> addFloor(@PathVariable String buildingId,
                                           @RequestBody Map<String, Object> body) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new RuntimeException("Building not found"));
        Floor f = Floor.builder()
                .building(building)
                .floorNumber(Integer.parseInt(body.get("floorNumber").toString()))
                .floorLabel(body.getOrDefault("floorLabel", "Floor " + body.get("floorNumber")).toString())
                .build();
        return ResponseEntity.ok(floorRepository.save(f));
    }

    // ── Blocks ────────────────────────────────────────────────────

    @GetMapping("/floors/{floorId}/blocks")
    public ResponseEntity<List<Block>> getBlocks(@PathVariable String floorId) {
        return ResponseEntity.ok(blockRepository.findByFloorId(floorId));
    }

    /** All blocks across the PG — used by EB Bill recording */
    @GetMapping("/blocks")
    public ResponseEntity<List<Block>> getAllBlocks() {
        return ResponseEntity.ok(blockRepository.findAll());
    }

    @PostMapping("/floors/{floorId}/blocks")
    public ResponseEntity<Block> addBlock(@PathVariable String floorId,
                                           @RequestBody Map<String, String> body) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new RuntimeException("Floor not found"));
        Block b = Block.builder()
                .floor(floor)
                .name(body.get("name"))
                .build();
        return ResponseEntity.ok(blockRepository.save(b));
    }

    // ── Rooms & Beds ──────────────────────────────────────────────

    @GetMapping("/blocks/{blockId}/rooms")
    public ResponseEntity<List<Room>> getRooms(@PathVariable String blockId) {
        return ResponseEntity.ok(roomRepository.findByBlockId(blockId));
    }

    @PostMapping("/blocks/{blockId}/rooms")
    public ResponseEntity<Room> addRoom(@PathVariable String blockId,
                                         @RequestBody Map<String, Object> body) {
        Block block = blockRepository.findById(blockId)
                .orElseThrow(() -> new RuntimeException("Block not found"));

        int sharingType = Integer.parseInt(body.get("sharingType").toString());
        BigDecimal rent = new BigDecimal(body.get("baseRent").toString());

        Room room = Room.builder()
                .floor(block.getFloor())
                .block(block)
                .roomNumber(body.get("roomNumber").toString())
                .sharingType(sharingType)
                .baseRent(rent)
                .build();
        room = roomRepository.save(room);

        // Auto-create beds for the room
        for (int i = 1; i <= sharingType; i++) {
            bedRepository.save(Bed.builder()
                    .room(room)
                    .bedLabel(body.get("roomNumber").toString() + "-B" + i)
                    .build());
        }
        return ResponseEntity.ok(room);
    }

    // ── Vacant Beds (for check-in) ────────────────────────────────

    @GetMapping("/vacant-beds")
    public ResponseEntity<List<Bed>> getVacantBeds(@RequestAttribute(required = false) String branchId) {
        if (branchId != null) {
            return ResponseEntity.ok(bedRepository.findVacantByBuildingId(branchId));
        }
        return ResponseEntity.ok(bedRepository.findVacant());
    }

    @GetMapping("/beds")
    public ResponseEntity<List<Bed>> getAllBeds(@RequestAttribute(required = false) String branchId) {
        if (branchId != null) {
            return ResponseEntity.ok(bedRepository.findAllByBuildingId(branchId));
        }
        return ResponseEntity.ok(bedRepository.findAllWithRoomDetails());
    }

    @DeleteMapping("/beds/{bedId}")
    public ResponseEntity<Void> deleteBed(@PathVariable String bedId) {
        Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new RuntimeException("Bed not found"));
        if (bed.getStatus() != com.pgcrm.entity.enums.BedStatus.VACANT) {
            throw new RuntimeException("Cannot delete an occupied bed");
        }
        bedRepository.delete(bed);
        return ResponseEntity.ok().build();
    }

    // ── Full Inventory Tree ───────────────────────────────────────

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        long total  = bedRepository.countTotal();
        long vacant = bedRepository.countVacant();
        List<Building> buildings = buildingRepository.findAll();
        return ResponseEntity.ok(Map.of(
            "totalBeds",    total,
            "vacantBeds",   vacant,
            "occupiedBeds", total - vacant,
            "buildings",    buildings.size()
        ));
    }
}
