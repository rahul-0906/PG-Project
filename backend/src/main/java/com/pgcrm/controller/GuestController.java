package com.pgcrm.controller;

import com.pgcrm.entity.*;
import com.pgcrm.repository.*;
import com.pgcrm.service.*;
import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.dto.GuestResponse;
import com.pgcrm.dto.InvoiceResponse;
import com.pgcrm.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.pgcrm.mapper.GuestMapper;
import com.pgcrm.mapper.InvoiceMapper;

@RestController
@RequestMapping("/api/guest")
@RequiredArgsConstructor
public class GuestController {

    private final GuestService guestService;
    private final DailyLogService dailyLogService;
    private final InvoiceRepository invoiceRepository;
    private final NotificationRepository notificationRepository;
    private final SystemConfigProperties systemConfig;
    private final InvoicePdfService invoicePdfService;
    private final DailyLogRepository dailyLogRepository;
    private final GuestMapper guestMapper;
    private final InvoiceMapper invoiceMapper;
    private final BuildingConfigRepository buildingConfigRepository;
    private final PricingService pricingService;
    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;

    @GetMapping("/profile")
    public ResponseEntity<GuestResponse> getProfile(Authentication auth) {
        return ResponseEntity.ok(guestMapper.toResponse(guestService.getByUserId(auth.getName())));
    }

    @PutMapping("/profile")
    public ResponseEntity<GuestResponse> updateProfile(Authentication auth,
                                                @RequestBody Map<String, String> body) {
        Guest guest = guestService.getByUserId(auth.getName());
        // Only editable basic fields
        if (body.containsKey("phone"))               guest.setPhone(body.get("phone"));
        if (body.containsKey("whatsappNumber"))      guest.setWhatsappNumber(body.get("whatsappNumber"));
        if (body.containsKey("vehicleRegistration")) guest.setVehicleRegistration(body.get("vehicleRegistration"));
        // fullName update — guests may have a legitimate name correction request
        if (body.containsKey("fullName") && body.get("fullName") != null && !body.get("fullName").isBlank())
            guest.setFullName(body.get("fullName"));
        guest = guestService.save(guest);
        return ResponseEntity.ok(guestMapper.toResponse(guest));
    }

    @PostMapping("/profile/request-email-change")
    public ResponseEntity<?> requestEmailChange(Authentication auth,
                                                                 @RequestBody Map<String, String> body) {
        String newEmail = body.get("newEmail");
        if (newEmail == null || newEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "New email is required"));
        }
        newEmail = newEmail.trim();

        // Check if email is already in use by another user
        Optional<User> existingUser = userRepository.findByEmailIgnoreCase(newEmail);
        if (existingUser.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already in use by another account"));
        }

        Guest guest = guestService.getByUserId(auth.getName());
        if (newEmail.equalsIgnoreCase(guest.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "New email is same as current email"));
        }

        // Generate 6-digit code
        String code = String.format("%06d", new java.util.Random().nextInt(1000000));
        emailVerificationService.storeCode(auth.getName(), newEmail, code);

        // Send email
        emailService.sendEmailVerificationCode(newEmail, code, guest.getFullName());

        return ResponseEntity.ok(Map.of("message", "Verification code sent to " + newEmail));
    }

    @PostMapping("/profile/verify-email-change")
    public ResponseEntity<?> verifyEmailChange(Authentication auth,
                                                            @RequestBody Map<String, String> body) {
        String newEmail = body.get("newEmail");
        String code = body.get("code");
        if (newEmail == null || newEmail.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and verification code are required"));
        }
        newEmail = newEmail.trim();
        code = code.trim();

        boolean verified = emailVerificationService.verifyCode(auth.getName(), newEmail, code);
        if (!verified) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired verification code"));
        }

        Guest guest = guestService.getByUserId(auth.getName());
        User user = guest.getUser();

        // Update email in Guest and User
        guest.setEmail(newEmail);
        if (user != null) {
            user.setEmail(newEmail);
            userRepository.save(user);
        }
        guest = guestService.save(guest);

        // Audit Log
        auditService.log(com.pgcrm.entity.enums.AuditAction.GUEST_CHECKIN, "Guest", guest.getId(),
            "Guest " + guest.getFullName() + " changed email to " + newEmail);

        return ResponseEntity.ok(guestMapper.toResponse(guest));
    }

    @GetMapping("/daily-log/{date}")
    public ResponseEntity<DailyLog> getLog(Authentication auth, @PathVariable String date) {
        Guest guest = guestService.getByUserId(auth.getName());
        return ResponseEntity.ok(dailyLogService.getLog(guest.getId(), LocalDate.parse(date)));
    }

    @PutMapping("/daily-log/{date}")
    public ResponseEntity<DailyLog> upsertLog(Authentication auth,
                                               @PathVariable String date,
                                               @RequestBody DailyLog body) {
        Guest guest = guestService.getByUserId(auth.getName());
        return ResponseEntity.ok(dailyLogService.upsertLog(guest.getId(), LocalDate.parse(date), body));
    }

    @GetMapping("/daily-log/month/{yearMonth}")
    public ResponseEntity<List<DailyLog>> getMonthlyLogs(Authentication auth, @PathVariable String yearMonth) {
        Guest guest = guestService.getByUserId(auth.getName());
        LocalDate start = LocalDate.parse(yearMonth + "-01");
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return ResponseEntity.ok(dailyLogService.getMonthlyLogs(guest.getId(), start, end));
    }

    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceResponse>> getInvoices(Authentication auth) {
        Guest guest = guestService.getByUserId(auth.getName());
        List<Invoice> invoices = invoiceRepository.findByGuestId(guest.getId());
        List<InvoiceResponse> responses = invoices.stream()
                .map(invoiceMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping(value = "/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadInvoicePdf(Authentication auth, @PathVariable String id) {
        // Validate ownership
        Guest guest = guestService.getByUserId(auth.getName());
        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
        if (!inv.getGuest().getId().equals(guest.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        
        byte[] pdfBytes = invoicePdfService.generateInvoicePdf(id);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", "Invoice-" + id.substring(0, 8) + ".pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> getNotifications(Authentication auth) {
        String userId = auth.getName();
        return ResponseEntity.ok(notificationRepository.findByUserIdOrderBySentAtDesc(userId));
    }

    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable String id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
        return ResponseEntity.ok().build();
    }

    @PutMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllRead(Authentication auth) {
        String userId = auth.getName();
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalse(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(Authentication auth) {
        Guest guest = guestService.getByUserId(auth.getName());
        String buildingId = (guest.getBed() != null && guest.getBed().getRoom() != null
                && guest.getBed().getRoom().getFloor() != null)
                ? guest.getBed().getRoom().getFloor().getBuilding().getId() : null;

        boolean foodIncludedInRent = systemConfig.getRules().isFoodIncludedInRent();
        boolean allowMealCancellations = systemConfig.getRules().isAllowMealCancellations();

        if (buildingId != null) {
            Optional<BuildingConfig> configOpt = buildingConfigRepository.findById(buildingId);
            if (configOpt.isPresent()) {
                foodIncludedInRent = configOpt.get().isFoodIncludedInRent();
                allowMealCancellations = configOpt.get().isAllowMealCancellations();
            }
        }

        List<Invoice> invoices = invoiceRepository.findByGuestId(guest.getId());
        long unread = notificationRepository.countByUserIdAndReadFalse(guest.getUser().getId());

        return ResponseEntity.ok(Map.of(
                "guestName", guest.getFullName(),
                "bedLabel", guest.getBed() != null ? guest.getBed().getBedLabel() : "N/A",
                "checkInDate", guest.getCheckInDate() != null ? guest.getCheckInDate().toString() : "",
                "totalInvoices", invoices.size(),
                "unreadNotifications", unread,
                "foodIncludedInRent", foodIncludedInRent,
                "allowMealCancellations", allowMealCancellations
        ));
    }

    /**
     * Returns the tenant-level feature flags visible to the guest.
     * Used by DailyLog to show/hide meals based on what is configured.
     */
    @GetMapping("/tenant-config")
    public ResponseEntity<Map<String, Object>> getTenantConfig(Authentication auth) {
        Guest guest = guestService.getByUserId(auth.getName());
        String buildingId = (guest.getBed() != null && guest.getBed().getRoom() != null
                && guest.getBed().getRoom().getFloor() != null)
                ? guest.getBed().getRoom().getFloor().getBuilding().getId() : null;

        PricingService.EffectivePricing pricing = pricingService.getEffectivePricing(buildingId);

        boolean foodIncludedInRent = systemConfig.getRules().isFoodIncludedInRent();
        boolean allowMealCancellations = systemConfig.getRules().isAllowMealCancellations();
        java.time.LocalTime breakfastCutoffTime = systemConfig.getRules().getBreakfastLockoutTime();
        java.time.LocalTime dinnerCutoffTime = systemConfig.getRules().getDinnerLockoutTime();
        boolean isPreviousDay = true;
        String allowedPaymentModes = "BOTH";

        if (buildingId != null) {
            Optional<BuildingConfig> configOpt = buildingConfigRepository.findById(buildingId);
            if (configOpt.isPresent()) {
                BuildingConfig cfg = configOpt.get();
                foodIncludedInRent = cfg.isFoodIncludedInRent();
                allowMealCancellations = cfg.isAllowMealCancellations();
                if (cfg.getBreakfastCutoffTime() != null) {
                    breakfastCutoffTime = cfg.getBreakfastCutoffTime();
                }
                if (cfg.getDinnerCutoffTime() != null) {
                    dinnerCutoffTime = cfg.getDinnerCutoffTime();
                }
                isPreviousDay = cfg.isPreviousDay();
                if (cfg.getAllowedPaymentModes() != null) {
                    allowedPaymentModes = cfg.getAllowedPaymentModes();
                }
            }
        }

        return ResponseEntity.ok(Map.ofEntries(
            Map.entry("foodIncludedInRent",     foodIncludedInRent),
            Map.entry("allowMealCancellations", allowMealCancellations),
            Map.entry("breakfastEnabled",       systemConfig.getRules().isBreakfastEnabled()),
            Map.entry("lunchEnabled",           systemConfig.getRules().isLunchEnabled()),
            Map.entry("dinnerEnabled",          systemConfig.getRules().isDinnerEnabled()),
            Map.entry("breakfastPrice",         pricing.breakfast()),
            Map.entry("lunchPrice",             pricing.lunch()),
            Map.entry("dinnerPrice",            pricing.dinner()),
            Map.entry("hasWashingMachine",      systemConfig.getRules().isHasWashingMachine()),
            Map.entry("breakfastCutoffTime",     breakfastCutoffTime.toString()),
            Map.entry("dinnerCutoffTime",        dinnerCutoffTime.toString()),
            Map.entry("isPreviousDay",          isPreviousDay),
            Map.entry("allowedPaymentModes",     allowedPaymentModes)
        ));
    }

    @GetMapping("/addons")
    public ResponseEntity<List<DailyLog>> getGuestAddons(Authentication auth) {
        Guest guest = guestService.getByUserId(auth.getName());
        return ResponseEntity.ok(dailyLogRepository.findAddonsByGuestId(guest.getId()));
    }

    @PostMapping("/maintenance")
    public ResponseEntity<MaintenanceTicket> createTicket(Authentication auth, @RequestBody Map<String, String> body) {
        Guest guest = guestService.getByUserId(auth.getName());
        
        String title = body.get("title");
        String description = body.get("description");
        String priorityStr = body.getOrDefault("priority", "MEDIUM");
        
        if (title == null || title.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Title and description are required");
        }
        
        String location = "N/A";
        String buildingId = null;
        if (guest.getBed() != null && guest.getBed().getRoom() != null) {
            Room room = guest.getBed().getRoom();
            String floorLabel = room.getFloor() != null ? room.getFloor().getFloorLabel() : "";
            location = "Room " + room.getRoomNumber() + (floorLabel.isEmpty() ? "" : " (" + floorLabel + ")");
            if (room.getFloor() != null && room.getFloor().getBuilding() != null) {
                buildingId = room.getFloor().getBuilding().getId();
            }
        }
        
        MaintenanceTicket ticket = MaintenanceTicket.builder()
                .raisedByGuest(guest)
                .buildingId(buildingId)
                .title(title)
                .location(location)
                .description(description)
                .status(com.pgcrm.entity.enums.MaintenanceStatus.OPEN)
                .priority(com.pgcrm.entity.enums.MaintenancePriority.valueOf(priorityStr.toUpperCase()))
                .build();
                
        MaintenanceTicket savedTicket = maintenanceTicketRepository.save(ticket);
        if (buildingId != null) {
            notificationService.alertManager(buildingId, 
                "New maintenance ticket raised by Guest " + guest.getFullName() + ": " + title + " (Priority: " + priorityStr + ")");
        }
        return ResponseEntity.ok(savedTicket);
    }

    @GetMapping("/maintenance")
    public ResponseEntity<List<MaintenanceTicket>> getTickets(Authentication auth) {
        Guest guest = guestService.getByUserId(auth.getName());
        return ResponseEntity.ok(maintenanceTicketRepository.findByRaisedByGuestId(guest.getId()));
    }

    @PostMapping("/invoices/{id}/pay-cash")
    public ResponseEntity<InvoiceResponse> payCash(Authentication auth, @PathVariable String id) {
        Guest guest = guestService.getByUserId(auth.getName());
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
        if (!invoice.getGuest().getId().equals(guest.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        if (invoice.getStatus() == com.pgcrm.entity.enums.InvoiceStatus.PAID) {
            throw new RuntimeException("Invoice is already paid");
        }
        invoice.setStatus(com.pgcrm.entity.enums.InvoiceStatus.PENDING_CASH_VERIFICATION);
        invoice = invoiceRepository.save(invoice);

        // Audit Log
        auditService.log(com.pgcrm.entity.enums.AuditAction.PAYMENT_RECEIVED, "Invoice", id,
            "Guest requested cash payment verification for Invoice " + id + " amount: ₹" + invoice.getTotalAmount());

        // Alert Manager
        String buildingId = (guest.getBed() != null && guest.getBed().getRoom() != null
                && guest.getBed().getRoom().getFloor() != null)
                ? guest.getBed().getRoom().getFloor().getBuilding().getId() : null;
        if (buildingId != null) {
            notificationService.alertManager(buildingId, 
                "Pending cash payment verification for Guest " + guest.getFullName() + 
                ", Invoice Month: " + invoice.getMonth() + "/" + invoice.getYear() + 
                ", Amount: ₹" + invoice.getTotalAmount());
        }

        return ResponseEntity.ok(invoiceMapper.toResponse(invoice));
    }
}
