package com.pgcrm.service;

import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.entity.enums.BedStatus;
import com.pgcrm.entity.enums.KycStatus;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.repository.*;
import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.exception.BedUnavailableException;
import com.pgcrm.exception.DuplicateEmailException;
import com.pgcrm.dto.GuestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuestService {

    private final GuestRepository guestRepository;
    private final UserRepository userRepository;
    private final BedRepository bedRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    private static final String TEMP_PASS_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RNG = new SecureRandom();

    @Transactional
    public Guest checkIn(String bedId, String fullName, String email,
                         String phone, String whatsappNumber, BigDecimal advanceDeposit,
                         LocalDate checkInDate, String vehicleRegistration) {

        Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + bedId));

        if (bed.getStatus() != BedStatus.VACANT) {
            throw new BedUnavailableException("Bed is not vacant: " + bed.getBedLabel());
        }

        java.util.Optional<User> existingUserOpt = userRepository.findByEmailIgnoreCase(email);
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            log.info("[DEBUG-CHECKIN] Found existing user: id={}, email={}, active={}", existingUser.getId(), existingUser.getEmail(), existingUser.isActive());
            Guest guest = guestRepository.findByUserId(existingUser.getId()).orElse(null);
            if (guest != null) {
                log.info("[DEBUG-CHECKIN] Found guest for user: id={}, name={}, active={}", guest.getId(), guest.getFullName(), guest.isActive());
            } else {
                log.info("[DEBUG-CHECKIN] No guest profile found for user id: {}", existingUser.getId());
            }

            if (guest == null || !guest.isActive()) {
                // Scenario A: Returning Guest
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
                    // Update Guest details
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

                // Update User details
                String tempPassword = generateTempPassword(10);
                existingUser.setFullName(fullName);
                existingUser.setPhone(phone);
                existingUser.setPassword(passwordEncoder.encode(tempPassword));
                existingUser.setFirstLogin(true);
                existingUser.setMustChangePassword(true);
                existingUser.setActive(true);
                userRepository.save(existingUser);

                guest = guestRepository.save(guest);

                // Update bed status
                bed.setStatus(BedStatus.OCCUPIED);
                bedRepository.save(bed);

                // Send welcome back email (non-blocking)
                final Guest finalGuest = guest;
                try {
                    emailService.sendReturningGuestWelcomeEmail(finalGuest, tempPassword);
                } catch (Exception e) {
                    log.warn("Welcome back email failed for {}: {}", email, e.getMessage());
                }

                // Audit log
                auditService.log(AuditAction.GUEST_CHECKIN, "Guest", guest.getId(),
                    String.format("Returning Guest '%s' checked back into bed '%s'", fullName, bed.getBedLabel()),
                    String.format("{\"bedId\":\"%s\",\"checkInDate\":\"%s\"}", bedId, checkInDate));

                return guest;
            } else {
                // Scenario B: Already Active Guest
                throw new DuplicateEmailException("A guest with this email is already checked into the system.");
            }
        }

        // Generate secure temp password
        String tempPassword = generateTempPassword(10);

        // Create user account with temp password, flagged for forced change
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(tempPassword))
                .role(Role.GUEST)
                .fullName(fullName)
                .phone(phone)
                .active(true)
                .firstLogin(true)
                .mustChangePassword(true)
                .build();
        user = userRepository.save(user);

        // Create guest profile
        Guest guest = Guest.builder()
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
                .build();
        guest = guestRepository.save(guest);

        // Mark bed as occupied
        bed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(bed);

        // Send welcome email with credentials (non-blocking)
        final Guest finalGuest = guest;
        try {
            emailService.sendGuestWelcomeEmail(finalGuest, tempPassword);
        } catch (Exception e) {
            log.warn("Welcome email failed for {}: {}", email, e.getMessage());
        }

        // Audit log
        auditService.log(AuditAction.GUEST_CHECKIN, "Guest", guest.getId(),
            String.format("Guest '%s' checked into bed '%s'", fullName, bed.getBedLabel()),
            String.format("{\"bedId\":\"%s\",\"checkInDate\":\"%s\"}", bedId, checkInDate));

        return guest;
    }

    // Backward-compatible overload (no vehicle reg)
    @Transactional
    public Guest checkIn(String bedId, String fullName, String email,
                         String phone, String whatsappNumber, BigDecimal advanceDeposit,
                         LocalDate checkInDate) {
        return checkIn(bedId, fullName, email, phone, whatsappNumber,
                       advanceDeposit, checkInDate, null);
    }

    public Guest getByUserId(String userId) {
        return guestRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest profile not found for user: " + userId));
    }

    private String generateTempPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(TEMP_PASS_CHARS.charAt(RNG.nextInt(TEMP_PASS_CHARS.length())));
        }
        return sb.toString();
    }

    @Transactional
    public Guest save(Guest guest) {
        return guestRepository.save(guest);
    }

    @Transactional
    public GuestResponse switchBed(String guestId, String newBedId) {
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + guestId));
        
        if (!guest.isActive()) {
            throw new IllegalArgumentException("Guest is not active: " + guest.getFullName());
        }

        Bed newBed = bedRepository.findById(newBedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found: " + newBedId));

        if (newBed.getStatus() != BedStatus.VACANT) {
            throw new BedUnavailableException("Bed is not vacant: " + newBed.getBedLabel());
        }

        Bed oldBed = guest.getBed();
        String oldBedLabel = "None";
        if (oldBed != null) {
            oldBedLabel = oldBed.getBedLabel();
            oldBed.setStatus(BedStatus.VACANT);
            bedRepository.save(oldBed);
        }

        newBed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(newBed);

        guest.setBed(newBed);
        Guest savedGuest = guestRepository.save(guest);

        BigDecimal newRent = newBed.getRoom().getBaseRent();

        // Audit Logging
        auditService.log(AuditAction.GUEST_BED_SWITCH, "Guest", savedGuest.getId(),
                String.format("Guest %s switched from Bed %s to Bed %s. Rent adjusted to ₹%s.",
                        savedGuest.getFullName(), oldBedLabel, newBed.getBedLabel(), newRent),
                String.format("{\"oldBedId\":\"%s\",\"newBedId\":\"%s\",\"newRent\":%s}",
                        oldBed != null ? oldBed.getId() : null, newBed.getId(), newRent));

        // Send email notification to guest
        try {
            emailService.sendBedSwitchEmail(savedGuest, oldBedLabel, newBed.getBedLabel(), newRent);
        } catch (Exception e) {
            log.warn("Failed to send bed switch email: {}", e.getMessage());
        }

        // Send in-app notification to guest
        try {
            notificationService.sendInApp(savedGuest, 
                    String.format("Your room/bed assignment has been updated from Bed %s to Bed %s. Your new monthly base rent is ₹%s.",
                            oldBedLabel, newBed.getBedLabel(), newRent));
        } catch (Exception e) {
            log.warn("Failed to send bed switch in-app notification: {}", e.getMessage());
        }

        return GuestResponse.fromEntity(savedGuest);
    }
}
