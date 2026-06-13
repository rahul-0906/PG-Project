package com.pgcrm.service;

import com.pgcrm.dto.GuestResponse;
import com.pgcrm.entity.Bed;
import com.pgcrm.entity.Building;
import com.pgcrm.entity.Guest;
import com.pgcrm.entity.User;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.entity.enums.BedStatus;
import com.pgcrm.entity.enums.KycStatus;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.exception.BedUnavailableException;
import com.pgcrm.exception.DuplicateEmailException;
import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.repository.BedRepository;
import com.pgcrm.repository.GuestRepository;
import com.pgcrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for the full guest lifecycle — check-in, check-out, bed switching,
 * and profile retrieval — in the PG CRM application.
 *
 * <p><strong>Check-in Scenarios:</strong></p>
 * <ol>
 *   <li><strong>New Guest:</strong> No existing {@link User} account. A new account is created
 *       with a temporary password and {@code Role.GUEST}, and a new {@link Guest} profile
 *       is linked to it. A welcome email is dispatched.</li>
 *   <li><strong>Returning Guest (previously checked out):</strong> An existing {@link User}
 *       account is found by email, but the linked {@link Guest} is either {@code null} (no
 *       profile yet) or {@code active = false} (previously checked out). The user account
 *       is reactivated with a new temp password and the guest profile is refreshed.
 *       A welcome-back email is dispatched.</li>
 *   <li><strong>Duplicate Active Guest:</strong> An existing {@link User} is found and their
 *       linked {@link Guest} is already {@code active = true}. A {@link DuplicateEmailException}
 *       is thrown to prevent double check-in.</li>
 * </ol>
 *
 * <p><strong>Email Dispatch:</strong> All email sends are wrapped in isolated {@code try/catch}
 * blocks. A transient SMTP failure will be logged at {@code WARN} level but will <em>not</em>
 * roll back the database transaction — the guest check-in is considered successful
 * regardless of email delivery.</p>
 *
 * @see GuestRepository
 * @see BedRepository
 * @see AuditService
 * @see NotificationService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GuestService {

    /** Ambiguity-reduced character set for generated temporary passwords. */
    private static final String TEMP_PASS_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    /** Cryptographically secure random number generator for temporary password generation. */
    private static final SecureRandom RNG = new SecureRandom();

    private final GuestRepository        guestRepository;
    private final UserRepository         userRepository;
    private final BedRepository          bedRepository;
    private final PasswordEncoder        passwordEncoder;
    private final EmailService           emailService;
    private final AuditService           auditService;
    private final NotificationService    notificationService;

    /**
     * Checks a guest into the specified bed, handling all three check-in scenarios.
     *
     * <p>The full check-in flow:</p>
     * <ol>
     *   <li>Validates the target bed exists and is {@link BedStatus#VACANT}.</li>
     *   <li>Checks for an existing {@link User} account with the provided email.</li>
     *   <li>Routes to Scenario A (returning guest) or creates a brand-new account (new guest).</li>
     *   <li>Creates or refreshes the {@link Guest} profile.</li>
     *   <li>Marks the bed as {@link BedStatus#OCCUPIED}.</li>
     *   <li>Dispatches a welcome email (non-blocking).</li>
     *   <li>Records a {@link AuditAction#GUEST_CHECKIN} audit log entry.</li>
     * </ol>
     *
     * @param bedId               the UUID of the target {@link Bed}.
     * @param fullName            the guest's full display name.
     * @param email               the guest's email address (used as the login credential).
     * @param phone               the guest's primary phone number.
     * @param whatsappNumber      the guest's WhatsApp number; defaults to {@code phone} if null.
     * @param advanceDeposit      the advance security deposit amount; defaults to {@link BigDecimal#ZERO} if null.
     * @param checkInDate         the check-in date; defaults to today if null.
     * @param vehicleRegistration the guest's vehicle registration number (optional; may be null).
     * @return the saved {@link Guest} entity.
     * @throws ResourceNotFoundException  if the bed does not exist.
     * @throws BedUnavailableException    if the bed is not in {@link BedStatus#VACANT} status.
     * @throws DuplicateEmailException    if a guest with this email is already actively checked in.
     */
    @Transactional
    public Guest checkIn(final List<String> bedIds, final String fullName, final String email,
                         final String phone, final String whatsappNumber,
                         final BigDecimal advanceDeposit, final LocalDate checkInDate,
                         final String vehicleRegistration, final boolean isBookEntireRoom,
                         final boolean isVeg, final boolean breakfastPreference,
                         final boolean lunchPreference, final boolean dinnerPreference) {

        log.info("Starting check-in operation for guest email: {} | Beds: {}", email, bedIds);
        if (bedIds == null || bedIds.isEmpty()) {
            log.warn("Check-in failed: No bed IDs provided for guest email: {}", email);
            throw new IllegalArgumentException("At least one bed must be selected for check-in.");
        }

        final String primaryBedId = bedIds.get(0);
        final Bed primaryBed = bedRepository.findById(primaryBedId)
                .orElseThrow(() -> {
                    log.warn("Check-in failed: Bed not found for ID: {}", primaryBedId);
                    return new ResourceNotFoundException("Bed not found: " + primaryBedId);
                });

        final Building building = primaryBed.getRoom().getFloor().getBuilding();

        // Resolve and validate all beds to check in
        final List<String> targetBedIds = new java.util.ArrayList<>();
        if (isBookEntireRoom) {
            // Fetch all beds in the room
            final List<Bed> roomBeds = bedRepository.findByRoomId(primaryBed.getRoom().getId());
            for (final Bed b : roomBeds) {
                targetBedIds.add(b.getId());
            }
        } else {
            targetBedIds.addAll(bedIds);
        }

        final List<Bed> targetBeds = new java.util.ArrayList<>();
        for (final String bid : targetBedIds) {
            final Bed b = bedRepository.findById(bid)
                    .orElseThrow(() -> {
                        log.warn("Check-in failed: Bed not found for ID: {}", bid);
                        return new ResourceNotFoundException("Bed not found: " + bid);
                    });
            if (b.getStatus() != BedStatus.VACANT) {
                log.warn("Check-in failed: Bed {} is not vacant (status: {})", b.getBedLabel(), b.getStatus());
                throw new BedUnavailableException("Bed is not vacant: " + b.getBedLabel());
            }
            targetBeds.add(b);
        }

        final Optional<User> existingUserOpt = userRepository.findByEmailIgnoreCase(email);
        if (existingUserOpt.isPresent()) {
            final User  existingUser = existingUserOpt.get();
            log.info("[DEBUG-CHECKIN] Found existing user: id={}, email={}, active={}",
                    existingUser.getId(), existingUser.getEmail(), existingUser.isActive());

            Guest guest = guestRepository.findByUserId(existingUser.getId()).orElse(null);
            if (guest != null) {
                log.info("[DEBUG-CHECKIN] Found guest for user: id={}, name={}, active={}",
                        guest.getId(), guest.getFullName(), guest.isActive());
            } else {
                log.info("[DEBUG-CHECKIN] No guest profile found for user id: {}", existingUser.getId());
            }

            if (guest == null || !guest.isActive()) {
                // ── Scenario A: Returning Guest ───────────────────────────────
                if (guest == null) {
                    guest = Guest.builder()
                            .user(existingUser)
                            .fullName(fullName)
                            .email(email)
                            .phone(phone)
                            .whatsappNumber(whatsappNumber != null ? whatsappNumber : phone)
                            .vehicleRegistration(vehicleRegistration)
                            .kycStatus(KycStatus.PENDING)
                            .checkInDate(checkInDate != null ? checkInDate : LocalDate.now())
                            .advanceDeposit(advanceDeposit != null ? advanceDeposit : BigDecimal.ZERO)
                            .building(building)
                            .isBookEntireRoom(isBookEntireRoom)
                            .vegPreference(isVeg)
                            .breakfastPreference(breakfastPreference)
                            .lunchPreference(lunchPreference)
                            .dinnerPreference(dinnerPreference)
                            .build();
                    guest.setBeds(targetBeds);
                } else {
                    // Refresh the existing guest profile for re-check-in.
                    guest.setFullName(fullName);
                    guest.setEmail(email);
                    guest.setPhone(phone);
                    guest.setWhatsappNumber(whatsappNumber != null ? whatsappNumber : phone);
                    guest.setVehicleRegistration(vehicleRegistration);
                    guest.setBeds(targetBeds);
                    guest.setBuilding(building);
                    guest.setBookEntireRoom(isBookEntireRoom);
                    guest.setAdvanceDeposit(advanceDeposit != null ? advanceDeposit : BigDecimal.ZERO);
                    guest.setCheckInDate(checkInDate != null ? checkInDate : LocalDate.now());
                    guest.setExpectedCheckOutDate(null);
                    guest.setNoticeDate(null);
                    guest.setExitDate(null);
                    guest.setActualCheckOutDate(null);
                    guest.setActive(true);
                    guest.setVegPreference(isVeg);
                    guest.setBreakfastPreference(breakfastPreference);
                    guest.setLunchPreference(lunchPreference);
                    guest.setDinnerPreference(dinnerPreference);
                }

                // Reactivate the user account with fresh credentials.
                final String tempPassword = generateTempPassword(10);
                existingUser.setFullName(fullName);
                existingUser.setPhone(phone);
                existingUser.setPassword(passwordEncoder.encode(tempPassword));
                existingUser.setFirstLogin(true);
                existingUser.setMustChangePassword(true);
                existingUser.setActive(true);
                userRepository.save(existingUser);

                guest = guestRepository.save(guest);

                for (final Bed b : targetBeds) {
                    b.setStatus(BedStatus.OCCUPIED);
                    bedRepository.save(b);
                }

                // Dispatch welcome-back email — failure is non-fatal.
                final Guest finalGuest = guest;
                try {
                    emailService.sendReturningGuestWelcomeEmail(finalGuest, tempPassword);
                } catch (Exception e) {
                    log.warn("Welcome back email failed for {}: {}", email, e.getMessage());
                }

                auditService.log(AuditAction.GUEST_CHECKIN, "Guest", guest.getId(),
                        String.format("Returning Guest '%s' checked back into room beds '%s'", fullName, primaryBed.getBedLabel()),
                        String.format("{\"bedIds\":\"%s\",\"checkInDate\":\"%s\"}", targetBedIds, checkInDate));

                log.info("Check-in successful (Returning Guest) for email: {}", email);
                return guest;
            } else {
                // ── Scenario B: Already Active Guest ─────────────────────────
                log.warn("Check-in failed: Duplicate active guest with email: {}", email);
                throw new DuplicateEmailException("A guest with this email is already checked into the system.");
            }
        }

        // ── Scenario C: Brand-New Guest ───────────────────────────────────────
        final String tempPassword = generateTempPassword(10);

        final User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(tempPassword))
                .role(Role.GUEST)
                .fullName(fullName)
                .phone(phone)
                .active(true)
                .firstLogin(true)
                .mustChangePassword(true)
                .build());

        Guest guest = guestRepository.save(Guest.builder()
                .user(user)
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .whatsappNumber(whatsappNumber != null ? whatsappNumber : phone)
                .vehicleRegistration(vehicleRegistration)
                .kycStatus(KycStatus.PENDING)
                .checkInDate(checkInDate != null ? checkInDate : LocalDate.now())
                .advanceDeposit(advanceDeposit != null ? advanceDeposit : BigDecimal.ZERO)
                .building(building)
                .isBookEntireRoom(isBookEntireRoom)
                .vegPreference(isVeg)
                .breakfastPreference(breakfastPreference)
                .lunchPreference(lunchPreference)
                .dinnerPreference(dinnerPreference)
                .build());
        guest.setBeds(targetBeds);
        guest = guestRepository.save(guest);

        for (final Bed b : targetBeds) {
            b.setStatus(BedStatus.OCCUPIED);
            bedRepository.save(b);
        }

        // Dispatch welcome email — failure is non-fatal.
        final Guest finalGuest = guest;
        try {
            emailService.sendGuestWelcomeEmail(finalGuest, tempPassword);
        } catch (Exception e) {
            log.warn("Welcome email failed for {}: {}", email, e.getMessage());
        }

        auditService.log(AuditAction.GUEST_CHECKIN, "Guest", guest.getId(),
                String.format("Guest '%s' checked into room beds '%s'", fullName, primaryBed.getBedLabel()),
                String.format("{\"bedIds\":\"%s\",\"checkInDate\":\"%s\"}", targetBedIds, checkInDate));

        log.info("Check-in successful (New Guest) for email: {} (ID: {})", email, guest.getId());
        return guest;
    }

    @Transactional
    public Guest checkIn(final String bedId, final String fullName, final String email,
                         final String phone, final String whatsappNumber,
                         final BigDecimal advanceDeposit, final LocalDate checkInDate,
                         final String vehicleRegistration, final boolean isBookEntireRoom) {
        return checkIn(java.util.List.of(bedId), fullName, email, phone, whatsappNumber, advanceDeposit, checkInDate, vehicleRegistration, isBookEntireRoom, true, true, true, true);
    }

    @Transactional
    public Guest checkIn(final String bedId, final String fullName, final String email,
                         final String phone, final String whatsappNumber,
                         final BigDecimal advanceDeposit, final LocalDate checkInDate,
                         final String vehicleRegistration) {
        return checkIn(java.util.List.of(bedId), fullName, email, phone, whatsappNumber, advanceDeposit, checkInDate, vehicleRegistration, false, true, true, true, true);
    }

    @Transactional
    public Guest checkIn(final String bedId, final String fullName, final String email,
                         final String phone, final String whatsappNumber,
                         final BigDecimal advanceDeposit, final LocalDate checkInDate) {
        return checkIn(java.util.List.of(bedId), fullName, email, phone, whatsappNumber, advanceDeposit, checkInDate, null, false, true, true, true, true);
    }

    /**
     * Retrieves a guest profile by the linked user account ID.
     *
     * @param userId the UUID of the {@link User} account linked to the guest.
     * @return the matching {@link Guest} entity.
     * @throws ResourceNotFoundException if no guest profile exists for the given user ID.
     */
    public Guest getByUserId(final String userId) {
        return guestRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.warn("Retrieve by user ID failed: Guest profile not found for user ID: {}", userId);
                    return new ResourceNotFoundException("Guest profile not found for user: " + userId);
                });
    }

    /**
     * Persists a modified guest entity to the database.
     *
     * <p>Used by other services (e.g., {@code SettlementService}) that need to save
     * changes to a guest record without coupling to the repository directly.</p>
     *
     * @param guest the {@link Guest} entity to save.
     * @return the saved {@link Guest} entity.
     */
    @Transactional
    public Guest save(final Guest guest) {
        log.info("Persisting guest entity for ID: {}", guest.getId());
        return guestRepository.save(guest);
    }

    /**
     * Switches a guest from their current bed to a new vacant bed.
     *
     * <p>The operation:</p>
     * <ol>
     *   <li>Validates the guest is currently active.</li>
     *   <li>Validates the target bed is {@link BedStatus#VACANT}.</li>
     *   <li>Marks the old bed as {@link BedStatus#VACANT} and the new bed as {@link BedStatus#OCCUPIED}.</li>
     *   <li>Updates the guest's bed reference.</li>
     *   <li>Records a {@link AuditAction#GUEST_BED_SWITCH} audit log entry.</li>
     *   <li>Dispatches a bed-switch confirmation email and an in-app notification (both non-fatal).</li>
     * </ol>
     *
     * @param guestId  the UUID of the {@link Guest} to be switched.
     * @param newBedId the UUID of the target {@link Bed} to switch the guest to.
     * @return a {@link GuestResponse} DTO reflecting the guest's updated state.
     * @throws ResourceNotFoundException if the guest or the new bed is not found.
     * @throws IllegalArgumentException  if the guest is not currently active.
     * @throws BedUnavailableException   if the target bed is not {@link BedStatus#VACANT}.
     */
    @Transactional
    public GuestResponse switchBed(final String guestId, final String newBedId) {
        log.info("Starting bed switch operation for guest ID: {} | Target Bed ID: {}", guestId, newBedId);
        final Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> {
                    log.warn("Bed switch failed: Guest not found for ID: {}", guestId);
                    return new ResourceNotFoundException("Guest not found: " + guestId);
                });

        if (!guest.isActive()) {
            log.warn("Bed switch failed: Guest ID {} ({}) is not active", guestId, guest.getFullName());
            throw new IllegalArgumentException("Guest is not active: " + guest.getFullName());
        }

        final Bed newBed = bedRepository.findById(newBedId)
                .orElseThrow(() -> {
                    log.warn("Bed switch failed: Bed not found for ID: {}", newBedId);
                    return new ResourceNotFoundException("Bed not found: " + newBedId);
                });

        if (newBed.getStatus() != BedStatus.VACANT) {
            log.warn("Bed switch failed: Target Bed {} is not vacant", newBed.getBedLabel());
            throw new BedUnavailableException("Bed is not vacant: " + newBed.getBedLabel());
        }

        final Bed    oldBed      = guest.getBed();
        final String oldBedLabel = (oldBed != null) ? oldBed.getBedLabel() : "None";

        if (oldBed != null) {
            if (guest.isBookEntireRoom()) {
                final List<Bed> oldRoomBeds = bedRepository.findByRoomId(oldBed.getRoom().getId());
                for (final Bed b : oldRoomBeds) {
                    b.setStatus(BedStatus.VACANT);
                    bedRepository.save(b);
                }
            } else {
                oldBed.setStatus(BedStatus.VACANT);
                bedRepository.save(oldBed);
            }
        }

        newBed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(newBed);

        guest.setBed(newBed);
        guest.setBuilding(newBed.getRoom().getFloor().getBuilding());
        guest.setBookEntireRoom(false); // Reset book entire room on switch
        final Guest       savedGuest = guestRepository.save(guest);
        final BigDecimal  newRent    = newBed.getRoom().getBaseRent();

        auditService.log(AuditAction.GUEST_BED_SWITCH, "Guest", savedGuest.getId(),
                String.format("Guest %s switched from Bed %s to Bed %s. Rent adjusted to ₹%s.",
                        savedGuest.getFullName(), oldBedLabel, newBed.getBedLabel(), newRent),
                String.format("{\"oldBedId\":\"%s\",\"newBedId\":\"%s\",\"newRent\":%s}",
                        oldBed != null ? oldBed.getId() : null, newBed.getId(), newRent));

        // Dispatch email notification — failure is non-fatal.
        try {
            emailService.sendBedSwitchEmail(savedGuest, oldBedLabel, newBed.getBedLabel(), newRent);
        } catch (Exception e) {
            log.warn("Failed to send bed switch email: {}", e.getMessage());
        }

        // Dispatch in-app notification — failure is non-fatal.
        try {
            notificationService.sendInApp(savedGuest,
                    String.format("Your room/bed assignment has been updated from Bed %s to Bed %s. "
                                  + "Your new monthly base rent is ₹%s.",
                            oldBedLabel, newBed.getBedLabel(), newRent));
        } catch (Exception e) {
            log.warn("Failed to send bed switch in-app notification: {}", e.getMessage());
        }

        log.info("Bed switch completed successfully for guest {} | Bed: {}", savedGuest.getFullName(), newBed.getBedLabel());
        return GuestResponse.fromEntity(savedGuest);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Generates a cryptographically secure temporary password of the given length.
     *
     * @param length the desired password length.
     * @return a randomly generated temporary password string.
     */
    private String generateTempPassword(final int length) {
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(TEMP_PASS_CHARS.charAt(RNG.nextInt(TEMP_PASS_CHARS.length())));
        }
        return sb.toString();
    }
}
