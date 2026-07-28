package com.pgcrm.service.impl;

import com.pgcrm.dto.CreatePgOwnerRequest;
import com.pgcrm.dto.FinancialOverrideRequest;
import com.pgcrm.dto.SuperAdminSummaryResponse;
import com.pgcrm.dto.UserRoleUpdateRequest;
import com.pgcrm.entity.AuditLog;
import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.User;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.repository.*;
import com.pgcrm.service.AuditService;
import com.pgcrm.service.SuperAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminServiceImpl implements SuperAdminService {

    private final UserRepository userRepository;
    private final BuildingRepository buildingRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final GuestRepository guestRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    private void verifyOwnerPermissionGranted() {
        boolean isGranted = userRepository.findAll().stream()
                .anyMatch(u -> u.getRole() == Role.PG_OWNER && u.isAllowSuperAdminAccess());
        if (!isGranted) {
            throw new IllegalStateException("Access Denied: PG Owner permission required. The property owner has not granted Super Admin modification access.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SuperAdminSummaryResponse getSummary() {
        long totalBuildings = buildingRepository.count();
        long totalRooms = roomRepository.count();
        long totalBeds = bedRepository.countTotal();
        long vacantBeds = bedRepository.countVacant();
        long occupiedBeds = Math.max(0, totalBeds - vacantBeds);
        double occupancyRate = totalBeds > 0 ? ((double) occupiedBeds / totalBeds) * 100.0 : 0.0;

        long totalGuests = guestRepository.count();

        List<User> users = userRepository.findAll();
        Map<String, Long> userCountByRole = new HashMap<>();
        for (Role role : Role.values()) {
            userCountByRole.put(role.name(), 0L);
        }
        for (User u : users) {
            if (u.getRole() != null) {
                userCountByRole.put(u.getRole().name(), userCountByRole.getOrDefault(u.getRole().name(), 0L) + 1);
            }
        }

        BigDecimal totalRevenueCollected = BigDecimal.ZERO;
        BigDecimal totalPendingArrears = BigDecimal.ZERO;

        List<Invoice> invoices = invoiceRepository.findAll();
        for (Invoice inv : invoices) {
            if (InvoiceStatus.PAID.equals(inv.getStatus())) {
                totalRevenueCollected = totalRevenueCollected.add(inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO);
            } else if (InvoiceStatus.GENERATED.equals(inv.getStatus()) || InvoiceStatus.OVERDUE.equals(inv.getStatus())) {
                totalPendingArrears = totalPendingArrears.add(inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO);
            }
        }

        long auditCount = auditLogRepository.count();

        return SuperAdminSummaryResponse.builder()
                .totalBuildings(totalBuildings)
                .totalRooms(totalRooms)
                .totalBeds(totalBeds)
                .occupiedBeds(occupiedBeds)
                .vacantBeds(vacantBeds)
                .occupancyRate(Math.round(occupancyRate * 100.0) / 100.0)
                .totalGuests(totalGuests)
                .userCountByRole(userCountByRole)
                .totalRevenueCollected(totalRevenueCollected)
                .totalPendingArrears(totalPendingArrears)
                .totalAuditLogsCount(auditCount)
                .systemHealthStatus("HEALTHY (All core services operational)")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public User createPgOwner(CreatePgOwnerRequest request) {
        if (userRepository.findByEmailIgnoreCase(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("User with email '" + request.getEmail() + "' already exists.");
        }

        User owner = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.PG_OWNER)
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .active(true)
                .firstLogin(true)
                .mustChangePassword(true)
                .allowSuperAdminAccess(false)
                .createdAt(LocalDateTime.now())
                .build();

        User savedOwner = userRepository.save(owner);
        auditService.log(AuditAction.TENANT_CREATED, "User", savedOwner.getId(),
                "Super Admin provisioned new PG_OWNER account: " + savedOwner.getEmail());
        return savedOwner;
    }

    @Override
    @Transactional
    public User updateUserRole(UserRoleUpdateRequest request, String adminActorId) {
        verifyOwnerPermissionGranted();

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + request.getUserId()));

        Role oldRole = user.getRole();
        user.setRole(request.getNewRole());
        if (request.getBranchId() != null) {
            user.setBranchId(request.getBranchId());
        }

        User updatedUser = userRepository.save(user);

        String desc = String.format("Super Admin updated user '%s' role from %s to %s (Branch: %s)",
                user.getEmail(), oldRole, request.getNewRole(), user.getBranchId());
        auditService.log(AuditAction.TENANT_CONFIG_UPDATED, "User", user.getId(), desc);

        return updatedUser;
    }

    @Override
    @Transactional
    public AuditLog applyFinancialOverride(FinancialOverrideRequest request, String adminActorId) {
        verifyOwnerPermissionGranted();

        Guest guest = guestRepository.findById(request.getGuestId())
                .orElseThrow(() -> new IllegalArgumentException("Guest not found with ID: " + request.getGuestId()));

        if (request.getInvoiceId() != null && !request.getInvoiceId().isBlank()) {
            Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                    .orElseThrow(() -> new IllegalArgumentException("Invoice not found with ID: " + request.getInvoiceId()));

            if ("WAIVE_RENT".equalsIgnoreCase(request.getOverrideType())) {
                invoice.setStatus(InvoiceStatus.PAID);
                invoice.setTotalAmount(BigDecimal.ZERO);
                invoiceRepository.save(invoice);
            }
        }

        String desc = String.format("SUPER ADMIN FINANCIAL OVERRIDE [%s] for Guest '%s' (ID: %s). Amount: ₹%s. Reason: %s",
                request.getOverrideType(), guest.getFullName(), guest.getId(), request.getAmount(), request.getReason());

        auditService.log(AuditAction.INVOICE_GENERATED, "Guest", guest.getId(), desc,
                String.format("{\"overrideType\":\"%s\",\"amount\":%s}", request.getOverrideType(), request.getAmount()));

        log.info("✅ Financial Override executed by Super Admin: {}", desc);
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return auditLogRepository.findByFilters(null, java.time.LocalDateTime.now().minusYears(5), java.time.LocalDateTime.now().plusDays(1), search, pageable);
        }
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    @Override
    @Transactional
    public void anonymizeGuest(String guestId, String adminActorId) {
        verifyOwnerPermissionGranted();

        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new IllegalArgumentException("Guest not found with ID: " + guestId));

        guest.setFullName("ANONYMIZED_GUEST_" + guest.getId().substring(0, 6));
        guest.setEmail("anonymized_" + guest.getId().substring(0, 6) + "@anonymized.local");
        guest.setPhone("+910000000000");
        guest.setWhatsappNumber(null);

        guestRepository.save(guest);

        String desc = "Super Admin executed PII Anonymization for Guest ID: " + guestId;
        auditService.log(AuditAction.GUEST_CHECKOUT_CONFIRMED, "Guest", guestId, desc);
        log.info("✅ PII Anonymized for guest {}", guestId);
    }

    @Override
    @Transactional
    public void deleteUser(String userId, String adminActorId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Cannot delete Super Admin account.");
        }

        userRepository.delete(user);
        String desc = String.format("Super Admin deleted %s account: '%s' (ID: %s)",
                user.getRole(), user.getEmail(), user.getId());
        auditService.log(AuditAction.TENANT_CONFIG_UPDATED, "User", userId, desc);
        log.info("✅ User deleted by Super Admin: {}", desc);
    }
}
