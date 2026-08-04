package com.pgcrm.service;

import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.BedStatus;
import com.pgcrm.entity.enums.KycStatus;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.exception.BedUnavailableException;
import com.pgcrm.exception.DuplicateEmailException;
import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.repository.BedRepository;
import com.pgcrm.repository.GuestRepository;
import com.pgcrm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GuestService} — covers all three check-in scenarios
 * plus validation edge cases and utility methods.
 *
 * <p><strong>Check-in scenarios tested:</strong>
 * <ol>
 *   <li>Scenario C (new guest) — no existing User</li>
 *   <li>Scenario A (returning guest) — existing User, inactive Guest</li>
 *   <li>Scenario A variant — existing User, no Guest profile yet</li>
 *   <li>Scenario B (duplicate) — existing User with active Guest → {@link DuplicateEmailException}</li>
 * </ol>
 * </p>
 *
 * <p>Other coverage:
 * <ul>
 *   <li>Bed not found → {@link ResourceNotFoundException}</li>
 *   <li>Bed already occupied → {@link BedUnavailableException}</li>
 *   <li>Empty bedId list → {@link IllegalArgumentException}</li>
 *   <li>Email non-blocking: SMTP failure does NOT roll back check-in</li>
 *   <li>{@link GuestService#getByUserId}: found / not found</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GuestService")
class GuestServiceTest {

    @Mock private GuestRepository     guestRepository;
    @Mock private UserRepository      userRepository;
    @Mock private BedRepository       bedRepository;
    @Mock private PasswordEncoder     passwordEncoder;
    @Mock private EmailService        emailService;
    @Mock private AuditService        auditService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private GuestService guestService;

    // ── Shared Fixtures ───────────────────────────────────────────────────────

    private Bed      bed;
    private Building building;

    /** Helper: build a valid check-in call using the standard test bed. */
    private Guest doCheckIn(String email) {
        return guestService.checkIn(
                List.of("bed1"), "John Doe", email,
                "9000000000", null, BigDecimal.valueOf(5000),
                LocalDate.now(), LocalTime.of(10, 0),
                null, false, true, true, true, true
        );
    }

    @BeforeEach
    void setUp() {
        building = new Building();
        building.setId("b1");

        BuildingConfig config = new BuildingConfig();
        config.setBreakfastCutoffTime(LocalTime.of(22, 0));
        config.setDinnerCutoffTime(LocalTime.of(14, 0));
        building.setBuildingConfig(config);

        Floor floor = new Floor();
        floor.setBuilding(building);

        Room room = new Room();
        room.setFloor(floor);

        bed = new Bed();
        bed.setId("bed1");
        bed.setBedLabel("G-01");
        bed.setStatus(BedStatus.VACANT);
        bed.setRoom(room);
    }

    // ── Scenario C: Brand-New Guest ───────────────────────────────────────────

    @Nested
    @DisplayName("Scenario C — Brand-New Guest")
    class NewGuestTests {

        @Test
        @DisplayName("creates User + Guest, marks bed OCCUPIED, sends welcome email")
        void checkIn_newGuest_createsUserAndGuestAndMarksBedOccupied() {
            when(bedRepository.findById("bed1")).thenReturn(Optional.of(bed));
            when(userRepository.findByEmailIgnoreCase("new@pgcrm.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");

            User savedUser = User.builder().id("u1").email("new@pgcrm.com").build();
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(guestRepository.save(any(Guest.class))).thenAnswer(inv -> inv.getArgument(0));

            Guest result = guestService.checkIn(
                    List.of("bed1"), "John Doe", "new@pgcrm.com",
                    "9000000000", null, BigDecimal.valueOf(5000),
                    LocalDate.now(), LocalTime.of(10, 0),
                    null, false, true, true, true, true
            );

            assertThat(result).isNotNull();
            assertThat(result.getFullName()).isEqualTo("John Doe");
            assertThat(result.getEmail()).isEqualTo("new@pgcrm.com");
            assertThat(bed.getStatus()).isEqualTo(BedStatus.OCCUPIED);

            verify(userRepository).save(any(User.class));
            verify(guestRepository, atLeastOnce()).save(any(Guest.class));
            verify(emailService).sendGuestWelcomeEmail(any(Guest.class), anyString());
        }

        @Test
        @DisplayName("defaults whatsappNumber to phone when null")
        void checkIn_newGuest_whatsappDefaultsToPhone() {
            when(bedRepository.findById("bed1")).thenReturn(Optional.of(bed));
            when(userRepository.findByEmailIgnoreCase("new@pgcrm.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userRepository.save(any(User.class)))
                    .thenReturn(User.builder().id("u1").email("new@pgcrm.com").build());
            when(guestRepository.save(any(Guest.class))).thenAnswer(inv -> inv.getArgument(0));

            Guest result = guestService.checkIn(
                    List.of("bed1"), "Jane Doe", "new@pgcrm.com",
                    "9111111111", null /* whatsapp null */, BigDecimal.ZERO,
                    LocalDate.now(), LocalTime.of(10, 0),
                    null, false, true, true, true, true
            );

            assertThat(result.getWhatsappNumber()).isEqualTo("9111111111");
        }

        @Test
        @DisplayName("SMTP failure does NOT throw — check-in succeeds with email warning only")
        void checkIn_newGuest_emailFailureIsNonFatal() {
            when(bedRepository.findById("bed1")).thenReturn(Optional.of(bed));
            when(userRepository.findByEmailIgnoreCase("new@pgcrm.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userRepository.save(any(User.class)))
                    .thenReturn(User.builder().id("u1").email("new@pgcrm.com").build());
            when(guestRepository.save(any(Guest.class))).thenAnswer(inv -> inv.getArgument(0));
            // Simulate SMTP failure
            doThrow(new RuntimeException("SMTP unavailable"))
                    .when(emailService).sendGuestWelcomeEmail(any(), anyString());

            // Must NOT throw — email failures are logged, not propagated
            assertThatNoException().isThrownBy(() -> guestService.checkIn(
                    List.of("bed1"), "John Doe", "new@pgcrm.com",
                    "9000000000", null, BigDecimal.ZERO,
                    LocalDate.now(), LocalTime.of(10, 0),
                    null, false, true, true, true, true
            ));
        }
    }

    // ── Scenario A: Returning Guest ───────────────────────────────────────────

    @Nested
    @DisplayName("Scenario A — Returning Guest")
    class ReturningGuestTests {

        @Test
        @DisplayName("reactivates existing User + inactive Guest, sends welcome-back email")
        void checkIn_returningGuest_inactiveGuestReactivated() {
            User existingUser = User.builder()
                    .id("u1").email("returning@pgcrm.com").active(false).build();

            Guest inactiveGuest = Guest.builder()
                    .id("g1").user(existingUser).email("returning@pgcrm.com")
                    .active(false).build();

            when(bedRepository.findById("bed1")).thenReturn(Optional.of(bed));
            when(userRepository.findByEmailIgnoreCase("returning@pgcrm.com"))
                    .thenReturn(Optional.of(existingUser));
            when(guestRepository.findByUserId("u1")).thenReturn(Optional.of(inactiveGuest));
            when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guestRepository.save(any(Guest.class))).thenAnswer(inv -> inv.getArgument(0));

            Guest result = guestService.checkIn(
                    List.of("bed1"), "Returning Doe", "returning@pgcrm.com",
                    "9000000000", null, BigDecimal.valueOf(5000),
                    LocalDate.now(), LocalTime.of(10, 0),
                    null, false, true, true, true, true
            );

            assertThat(result).isNotNull();
            assertThat(result.isActive()).isTrue();
            assertThat(existingUser.isActive()).isTrue();
            assertThat(existingUser.isMustChangePassword()).isTrue();
            assertThat(bed.getStatus()).isEqualTo(BedStatus.OCCUPIED);

            verify(emailService).sendReturningGuestWelcomeEmail(any(Guest.class), anyString());
            verify(emailService, never()).sendGuestWelcomeEmail(any(), any());
        }

        @Test
        @DisplayName("creates new Guest profile when User exists but has no Guest profile")
        void checkIn_returningGuest_noGuestProfile_createsNew() {
            User existingUser = User.builder()
                    .id("u1").email("noguest@pgcrm.com").active(true).build();

            when(bedRepository.findById("bed1")).thenReturn(Optional.of(bed));
            when(userRepository.findByEmailIgnoreCase("noguest@pgcrm.com"))
                    .thenReturn(Optional.of(existingUser));
            when(guestRepository.findByUserId("u1")).thenReturn(Optional.empty()); // no profile
            when(passwordEncoder.encode(anyString())).thenReturn("hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(guestRepository.save(any(Guest.class))).thenAnswer(inv -> inv.getArgument(0));

            Guest result = guestService.checkIn(
                    List.of("bed1"), "No Profile Doe", "noguest@pgcrm.com",
                    "9000000000", null, BigDecimal.ZERO,
                    LocalDate.now(), LocalTime.of(10, 0),
                    null, false, true, true, true, true
            );

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("noguest@pgcrm.com");
        }
    }

    // ── Scenario B: Duplicate Active Guest ────────────────────────────────────

    @Nested
    @DisplayName("Scenario B — Duplicate Active Guest")
    class DuplicateGuestTests {

        @Test
        @DisplayName("throws DuplicateEmailException when guest with email is already active")
        void checkIn_activeGuestExists_throwsDuplicateEmail() {
            User existingUser = User.builder().id("u1").email("dupe@pgcrm.com").active(true).build();
            Guest activeGuest = Guest.builder()
                    .id("g1").user(existingUser).email("dupe@pgcrm.com")
                    .active(true).build();

            when(bedRepository.findById("bed1")).thenReturn(Optional.of(bed));
            when(userRepository.findByEmailIgnoreCase("dupe@pgcrm.com"))
                    .thenReturn(Optional.of(existingUser));
            when(guestRepository.findByUserId("u1")).thenReturn(Optional.of(activeGuest));

            assertThatThrownBy(() -> guestService.checkIn(
                    List.of("bed1"), "John Doe", "dupe@pgcrm.com",
                    "9000000000", null, BigDecimal.ZERO,
                    LocalDate.now(), LocalTime.of(10, 0),
                    null, false, true, true, true, true
            )).isInstanceOf(DuplicateEmailException.class)
              .hasMessageContaining("already checked into");

            // Bed must remain VACANT — no side effects
            assertThat(bed.getStatus()).isEqualTo(BedStatus.VACANT);
            verify(guestRepository, never()).save(any());
        }
    }

    // ── Validation Errors ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation Errors")
    class ValidationTests {

        @Test
        @DisplayName("throws ResourceNotFoundException when bed ID does not exist")
        void checkIn_bedNotFound_throwsResourceNotFound() {
            when(bedRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> guestService.checkIn(
                    List.of("nonexistent"), "John", "e@pgcrm.com",
                    "9000000000", null, BigDecimal.ZERO,
                    LocalDate.now(), LocalTime.of(10, 0),
                    null, false, true, true, true, true
            )).isInstanceOf(ResourceNotFoundException.class)
              .hasMessageContaining("Bed not found");
        }

        @Test
        @DisplayName("throws BedUnavailableException when bed is already OCCUPIED")
        void checkIn_bedOccupied_throwsBedUnavailable() {
            bed.setStatus(BedStatus.OCCUPIED);
            when(bedRepository.findById("bed1")).thenReturn(Optional.of(bed));

            assertThatThrownBy(() -> guestService.checkIn(
                    List.of("bed1"), "John", "e@pgcrm.com",
                    "9000000000", null, BigDecimal.ZERO,
                    LocalDate.now(), LocalTime.of(10, 0),
                    null, false, true, true, true, true
            )).isInstanceOf(BedUnavailableException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when bedIds list is empty")
        void checkIn_emptyBedList_throwsIllegalArgument() {
            assertThatThrownBy(() -> guestService.checkIn(
                    List.of() /* empty */, "John", "e@pgcrm.com",
                    "9000000000", null, BigDecimal.ZERO,
                    LocalDate.now(), LocalTime.of(10, 0),
                    null, false, true, true, true, true
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("At least one bed");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when bedIds is null")
        void checkIn_nullBedList_throwsIllegalArgument() {
            assertThatThrownBy(() -> guestService.checkIn(
                    null, "John", "e@pgcrm.com",
                    "9000000000", null, BigDecimal.ZERO,
                    LocalDate.now(), LocalTime.of(10, 0),
                    null, false, true, true, true, true
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── getByUserId() ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getByUserId()")
    class GetByUserIdTests {

        @Test
        @DisplayName("returns guest for a valid user ID")
        void getByUserId_found_returnsGuest() {
            Guest g = Guest.builder().id("g1").fullName("Test").build();
            when(guestRepository.findByUserId("u1")).thenReturn(Optional.of(g));

            Guest result = guestService.getByUserId("u1");

            assertThat(result.getId()).isEqualTo("g1");
            assertThat(result.getFullName()).isEqualTo("Test");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when no guest profile exists for userId")
        void getByUserId_notFound_throwsResourceNotFound() {
            when(guestRepository.findByUserId("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> guestService.getByUserId("ghost"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
