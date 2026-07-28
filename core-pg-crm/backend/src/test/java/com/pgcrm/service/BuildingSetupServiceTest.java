package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.*;
import com.pgcrm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BuildingSetupServiceTest {

    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private FloorRepository floorRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private BedRepository bedRepository;
    @Mock
    private BuildingConfigRepository buildingConfigRepository;
    @Mock
    private SystemConfigProperties systemConfig;

    @InjectMocks
    private BuildingSetupService buildingSetupService;

    private Building building;

    @BeforeEach
    void setUp() {
        building = new Building();
        building.setId("b1");
        building.setName("Main Building");
    }

    @Test
    void testGetBuildingLayout_Success() {
        // Arrange
        Floor floor = new Floor();
        floor.setId("f1");
        floor.setFloorLabel("Ground Floor");
        floor.setFloorNumber(0);
        floor.setBuilding(building);

        Block block = new Block();
        block.setId("blk1");
        block.setName("Block A");
        block.setFloor(floor);

        Room room = new Room();
        room.setId("r1");
        room.setRoomNumber("101");
        room.setFloor(floor);
        room.setBlock(block);
        room.setBaseRent(BigDecimal.valueOf(8000));

        Bed bed = new Bed();
        bed.setId("bed1");
        bed.setBedLabel("G-01");
        bed.setRoom(room);
        room.setBeds(new ArrayList<>(List.of(bed)));

        when(buildingRepository.findById("b1")).thenReturn(Optional.of(building));
        when(buildingConfigRepository.findById("b1")).thenReturn(Optional.empty());
        when(floorRepository.findByBuildingId("b1")).thenReturn(new ArrayList<>(List.of(floor)));
        when(blockRepository.findByFloorId("f1")).thenReturn(new ArrayList<>(List.of(block)));
        when(roomRepository.findByBlockId("blk1")).thenReturn(new ArrayList<>(List.of(room)));
        when(roomRepository.findByFloorId("f1")).thenReturn(new ArrayList<>()); // standalone rooms

        // Act
        BuildingSetupService.BuildingEditRequest layout = buildingSetupService.getBuildingLayout("b1");

        // Assert
        assertNotNull(layout);
        assertEquals("Main Building", layout.getName());
        assertEquals(1, layout.getFloors().size());
        assertEquals("Ground Floor", layout.getFloors().get(0).getLabel());
        assertEquals(1, layout.getFloors().get(0).getBlocks().size());
        assertEquals("Block A", layout.getFloors().get(0).getBlocks().get(0).getName());
    }

    @Test
    void testCreateBuilding_Success() {
        // Arrange
        BuildingSetupService.BuildingSetupRequest req = new BuildingSetupService.BuildingSetupRequest();
        req.setName("New PG");
        req.setAddress("Bangalore");
        req.setFloors(new ArrayList<>());

        SystemConfigProperties.Pricing pricing = new SystemConfigProperties.Pricing();
        pricing.setBreakfast(BigDecimal.valueOf(40));
        pricing.setLunch(BigDecimal.valueOf(60));
        pricing.setDinner(BigDecimal.valueOf(50));
        pricing.setWashingMachine(BigDecimal.valueOf(50));
        pricing.setOmelette(BigDecimal.valueOf(10));
        pricing.setBoiledEgg(BigDecimal.valueOf(5));
        
        when(systemConfig.getPricing()).thenReturn(pricing);

        when(buildingRepository.save(any(Building.class))).thenAnswer(i -> {
            Building saved = i.getArgument(0);
            saved.setId("new-b1");
            return saved;
        });

        // Act
        BuildingSetupService.BuildingCreationResult result = buildingSetupService.createBuilding(req);

        // Assert
        assertNotNull(result);
        assertEquals("new-b1", result.getBuildingId());
        verify(buildingRepository, times(1)).save(any(Building.class));
        verify(buildingConfigRepository, times(1)).save(any(BuildingConfig.class));
    }
}
