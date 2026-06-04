package com.pgcrm.service;

import com.pgcrm.dto.GuestResponse;
import com.pgcrm.entity.Bed;
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
    public Guest checkIn(final String bedId, final String fullName, final String email,
                         final String phone, final String whatsappNumber,
                         final BigDecimal advanceDeposit, final LocalDate checkInDate,
                         final String vehicleRegistration) {

        final Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + bedId));

        if (bed.getStatus() != BedStatus.VACANT) {
            throw new BedUnavailableException("Bed is not vacant: " + bed.getBedLabel());
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
                            .bed(bed)
                            .fullName(fullName)
                            .email(email)
                            .phone(phone)
                            .whatsappNumber(whatsappNumber != null ? whatsappNumber : phone)
                            .vehicleRegistration(vehicleRegistration)
                            .kycStatus(KycStatus.PENDING)
                            .checkInDate(checkInDate != null ? checkInDate : LocalDate.now())
                            .advanceDeposit(advanceDeposit != null ? advanceDeposit : BigDecimal.ZERO)
                            .build();
                } else {
                    // Refresh the existing guest profile for re-check-in.
                    guest.setFullName(fullName);
                    guest.setEmail(email);
                    guest.setPhone(phone);
                    guest.setWhatsappNumber(whatsappNumber != null ? whatsappNumber : phone);
                    guest.setVehicleRegistration(vehicleRegistration);
                    guest.setBed(bed);
                    guest.setAdvanceDeposit(advanceDeposit != null ? advanceDeposit : BigDecimal.ZERO);
                    guest.setCheckInDate(checkInDate != null ? checkInDate : LocalDate.now());
                    guest.setExpectedCheckOutDate(null);
                    guest.setNoticeDate(null);
                    guest.setExitDate(null);
                    guest.setActualCheckOutDate(null);
                    guest.setActive(true);
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
                bed.setStatus(BedStatus.OCCUPIED);
                bedRepository.save(bed);

                // Dispatch welcome-back email — failure is non-fatal.
                final Guest finalGuest = guest;
                try {
                    emailService.sendReturningGuestWelcomeEmail(finalGuest, tempPassword);
                } catch (Exception e) {
                    log.warn("Welcome back email failed for {}: {}", email, e.getMessage());
                }

                auditService.log(AuditAction.GUEST_CHECKIN, "Guest", guest.getId(),
                        String.format("Returning Guest '%s' checked back into bed '%s'", fullName, bed.getBedLabel()),
                        String.format("{\"bedId\":\"%s\",\"checkInDate\":\"%s\"}", bedId, checkInDate));

                return guest;
            } else {
                // ── Scenario B: Already Active Guest ─────────────────────────
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
                .bed(bed)
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .whatsappNumber(whatsappNumber != null ? whatsappNumber : phone)
                .vehicleRegistration(vehicleRegistration)
                .kycStatus(KycStatus.PENDING)
                .checkInDate(checkInDate != null ? checkInDate : LocalDate.now())
                .advanceDeposit(advanceDeposit != null ? advanceDeposit : BigDecimal.ZERO)
                .build());

        bed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(bed);

        // Dispatch welcome email — failure is non-fatal.
        final Guest finalGuest = guest;
        try {
            emailService.sendGuestWelcomeEmail(finalGuest, tempPassword);
        } catch (Exception e) {
            log.warn("Welcome email failed for {}: {}", email, e.getMessage());
        }

        auditService.log(AuditAction.GUEST_CHECKIN, "Guest", guest.getId(),
                String.format("Guest '%s' checked into bed '%s'", fullName, bed.getBedLabel()),
                String.format("{\"bedId\":\"%s\",\"checkInDate\":\"%s\"}", bedId, checkInDate));

        return guest;
    }

    /**
     * Backward-compatible overload of {@link #checkIn(String, String, String, String, String, BigDecimal, LocalDate, String)}
     * for callers that do not supply a vehicle registration number.
     *
     * @param bedId          the UUID of the target {@link Bed}.
     * @param fullName       the guest's full display name.
     * @param email          the guest's email address.
     * @param phone          the guest's primary phone number.
     * @param whatsappNumber the guest's WhatsApp number; defaults to {@code phone} if null.
     * @param advanceDeposit the advance security deposit amount; defaults to {@link BigDecimal#ZERO} if null.
     * @param checkInDate    the check-in date; defaults to today if null.
     * @return the saved {@link Guest} entity.
     */
    @Transactional
    public Guest checkIn(final String bedId, final String fullName, final String email,
                         final String phone, final String whatsappNumber,
                         final BigDecimal advanceDeposit, final LocalDate checkInDate) {
        return checkIn(bedId, fullName, email, phone, whatsappNumber, advanceDeposit, checkInDate, null);
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
                .orElseThrow(() -> new ResourceNotFoundException("Guest profile not found for user: " + userId));
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
        final Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + guestId));

        if (!guest.isActive()) {
            throw new IllegalArgumentException("Guest is not active: " + guest.getFullName());
        }

        final Bed newBed = bedRepository.findById(newBedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + newBedId));

        if (newBed.getStatus() != BedStatus.VACANT) {
            throw new BedUnavailableException("Bed is not vacant: " + newBed.getBedLabel());
        }

        final Bed    oldBed      = guest.getBed();
        final String oldBedLabel = (oldBed != null) ? oldBed.getBedLabel() : "None";

        if (oldBed != null) {
            oldBed.setStatus(BedStatus.VACANT);
            bedRepository.save(oldBed);
        }

        newBed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(newBed);

        guest.setBed(newBed);
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
