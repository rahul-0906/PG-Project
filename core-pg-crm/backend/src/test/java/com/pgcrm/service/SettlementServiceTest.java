package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.BedStatus;
import com.pgcrm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SettlementServiceTest {

    @Mock
    private GuestRepository guestRepository;
    @Mock
    private DailyLogRepository dailyLogRepository;
    @Mock
    private EbBillGuestRepository ebBillGuestRepository;
    @Mock
    private SystemConfigProperties systemConfig;
    @Mock
    private NotificationService notificationService;
    @Mock
    private BedRepository bedRepository;
    @Mock
    private BuildingConfigRepository buildingConfigRepository;
    @Mock
    private PricingService pricingService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SettlementService settlementService;

    private Guest guest;
    private Bed bed;
    private Room room;
    private Building building;
    private User user;

    @BeforeEach
    void setUp() {
        building = new Building();
        building.setId("b1");

        Floor floor = new Floor();
        floor.setBuilding(building);

        room = new Room();
        room.setFloor(floor);
        room.setBaseRent(BigDecimal.valueOf(9000));

        bed = new Bed();
        bed.setId("bed1");
        bed.setBedLabel("G-01");
        bed.setStatus(BedStatus.OCCUPIED);
        bed.setRoom(room);

        user = new User();
        user.setId("user1");
        user.setEmail("john@example.com");

        guest = new Guest();
        guest.setId("guest1");
        guest.setFullName("John Doe");
        guest.setEmail("john@example.com");
        guest.setUser(user);
        guest.setBeds(new ArrayList<>(List.of(bed)));
        guest.setCheckInDate(LocalDate.of(2026, 1, 1));
        guest.setAdvanceDeposit(BigDecimal.valueOf(8000));
    }

    @Test
    void testInitiateCheckout_Success() {
        SystemConfigProperties.Rules rules = new SystemConfigProperties.Rules();
        rules.setNoticePeriodDays(30);
        when(systemConfig.getRules()).thenReturn(rules);
        
        when(guestRepository.findById("guest1")).thenReturn(Optional.of(guest));
        when(guestRepository.save(any(Guest.class))).thenAnswer(i -> i.getArgument(0));

        Guest result = settlementService.initiateCheckout("guest1");

        assertNotNull(result);
        assertEquals(LocalDate.now(), result.getNoticeDate());
        assertEquals(LocalDate.now().plusDays(30), result.getExitDate());
        verify(guestRepository, times(1)).save(guest);
        verify(notificationService, times(1)).sendBoth(eq(guest), anyString());
    }

    @Test
    void testConfirmCheckout_Success() {
        // Prepare guest with notice date set
        guest.setNoticeDate(LocalDate.now().minusDays(30));
        guest.setExitDate(LocalDate.now());

        when(guestRepository.findById("guest1")).thenReturn(Optional.of(guest));
        
        SystemConfigProperties.Rules rules = new SystemConfigProperties.Rules();
        rules.setFoodIncludedInRent(true);
        rules.setHasWashingMachine(false);
        when(systemConfig.getRules()).thenReturn(rules);

        when(guestRepository.save(any(Guest.class))).thenAnswer(i -> i.getArgument(0));
        when(bedRepository.save(any(Bed.class))).thenAnswer(i -> i.getArgument(0));

        SettlementService.SettlementResult result = settlementService.confirmCheckout("guest1");

        assertNotNull(result);
        assertFalse(guest.isActive());
        assertNull(guest.getBed());
        assertEquals(BedStatus.VACANT, bed.getStatus());
        assertFalse(user.isActive());

        verify(guestRepository, times(1)).save(guest);
        verify(userRepository, times(1)).save(user);
        verify(bedRepository, times(1)).save(bed);
        verify(notificationService, times(1)).sendBoth(eq(guest), anyString());
    }
}
