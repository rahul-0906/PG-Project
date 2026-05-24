package com.pgcrm.service;

import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.entity.enums.BedStatus;
import com.pgcrm.entity.enums.KycStatus;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.repository.*;
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

    private static final String TEMP_PASS_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RNG = new SecureRandom();

    @Transactional
    public Guest checkIn(String bedId, String fullName, String email,
                         String phone, String whatsappNumber, BigDecimal advanceDeposit,
                         LocalDate checkInDate, String vehicleRegistration) {

        Bed bed = bedRepository.findById(bedId)
                .orElseThrow(() -> new RuntimeException("Bed not found: " + bedId));

        if (bed.getStatus() != BedStatus.VACANT) {
            throw new RuntimeException("Bed is not vacant: " + bed.getBedLabel());
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
                .orElseThrow(() -> new RuntimeException("Guest profile not found for user: " + userId));
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
}
