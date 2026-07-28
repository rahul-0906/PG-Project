package com.pgcrm.service;

import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.BedStatus;
import com.pgcrm.entity.enums.KycStatus;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.exception.BedUnavailableException;
import com.pgcrm.exception.DuplicateEmailException;
import com.pgcrm.repository.BedRepository;
import com.pgcrm.repository.GuestRepository;
import com.pgcrm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GuestServiceTest {

    @Mock
    private GuestRepository guestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BedRepository bedRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private GuestService guestService;

    private Bed bed;
    private Room room;
    private Floor floor;
    private Building building;

    @BeforeEach
    void setUp() {
        building = new Building();
        building.setId("b1");
        
        BuildingConfig config = new BuildingConfig();
        config.setBreakfastCutoffTime(LocalTime.of(22, 0));
        config.setDinnerCutoffTime(LocalTime.of(14, 0));
        building.setBuildingConfig(config);

        floor = new Floor();
        floor.setBuilding(building);

        room = new Room();
        room.setFloor(floor);

        bed = new Bed();
        bed.setId("bed1");
        bed.setBedLabel("G-01");
        bed.setStatus(BedStatus.VACANT);
        bed.setRoom(room);
    }

    @Test
    void testCheckIn_NewGuest_Success() {
        when(bedRepository.findById("bed1")).thenReturn(Optional.of(bed));
        when(userRepository.findByEmailIgnoreCase("guest@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");

        // Save User & Guest mock
        User savedUser = new User();
        savedUser.setId("user1");
        savedUser.setEmail("guest@example.com");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        when(guestRepository.save(any(Guest.class))).thenAnswer(i -> i.getArgument(0));

        Guest result = guestService.checkIn(
                List.of("bed1"), "John Doe", "guest@example.com",
                "9000000000", "9000000000", BigDecimal.valueOf(5000),
                LocalDate.now().plusDays(2), LocalTime.of(10, 0), "KA01-1234",
                false, true, true, true, true
        );

        assertNotNull(result);
        assertEquals("John Doe", result.getFullName());
        assertEquals("guest@example.com", result.getEmail());
        assertEquals(BedStatus.OCCUPIED, bed.getStatus());
        
        verify(userRepository, times(1)).save(any(User.class));
        verify(guestRepository, atLeastOnce()).save(any(Guest.class));
        verify(emailService, times(1)).sendGuestWelcomeEmail(any(Guest.class), anyString());
    }

    @Test
    void testCheckIn_BedAlreadyOccupied_ThrowsException() {
        bed.setStatus(BedStatus.OCCUPIED);
        when(bedRepository.findById("bed1")).thenReturn(Optional.of(bed));

        assertThrows(BedUnavailableException.class, () -> {
            guestService.checkIn(
                List.of("bed1"), "John Doe", "guest@example.com",
                "9000000000", "9000000000", BigDecimal.valueOf(5000),
                LocalDate.now().plusDays(2), LocalTime.of(10, 0), "KA01-1234",
                false, true, true, true, true
            );
        });
    }

    @Test
    void testGetByUserId_Success() {
        Guest activeGuest = new Guest();
        activeGuest.setId("guest1");
        activeGuest.setFullName("John Doe");

        when(guestRepository.findByUserId("user1")).thenReturn(Optional.of(activeGuest));

        Guest result = guestService.getByUserId("user1");
        assertNotNull(result);
        assertEquals("John Doe", result.getFullName());
    }
}
