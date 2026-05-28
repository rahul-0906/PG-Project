package com.pgcrm.controller;

import com.pgcrm.entity.*;
import com.pgcrm.repository.*;
import com.pgcrm.service.*;
import com.pgcrm.config.SystemConfigProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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

    @GetMapping("/profile")
    public ResponseEntity<Guest> getProfile(Authentication auth) {
        return ResponseEntity.ok(guestService.getByUserId(auth.getName()));
    }

    @PutMapping("/profile")
    public ResponseEntity<Guest> updateProfile(Authentication auth,
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
        return ResponseEntity.ok(guest);
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

    @GetMapping("/invoices")
    public ResponseEntity<List<Invoice>> getInvoices(Authentication auth) {
        Guest guest = guestService.getByUserId(auth.getName());
        return ResponseEntity.ok(invoiceRepository.findByGuestId(guest.getId()));
    }

    @GetMapping(value = "/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadInvoicePdf(Authentication auth, @PathVariable String id) {
        // Validate ownership
        Guest guest = guestService.getByUserId(auth.getName());
        Invoice inv = invoiceRepository.findById(id).orElseThrow(() -> new RuntimeException("Invoice not found"));
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
        Guest guest = guestService.getByUserId(auth.getName());
        return ResponseEntity.ok(notificationRepository.findByGuestIdOrderBySentAtDesc(guest.getId()));
    }

    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable String id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(Authentication auth) {
        Guest guest = guestService.getByUserId(auth.getName());

        List<Invoice> invoices = invoiceRepository.findByGuestId(guest.getId());
        long unread = notificationRepository.countByGuestIdAndReadFalse(guest.getId());

        return ResponseEntity.ok(Map.of(
                "guestName", guest.getFullName(),
                "bedLabel", guest.getBed() != null ? guest.getBed().getBedLabel() : "N/A",
                "checkInDate", guest.getCheckInDate() != null ? guest.getCheckInDate().toString() : "",
                "totalInvoices", invoices.size(),
                "unreadNotifications", unread,
                "foodIncludedInRent", systemConfig.getRules().isFoodIncludedInRent(),
                "allowMealCancellations", systemConfig.getRules().isAllowMealCancellations()
        ));
    }

    /**
     * Returns the tenant-level feature flags visible to the guest.
     * Used by DailyLog to show/hide meals based on what is configured.
     */
    @GetMapping("/tenant-config")
    public ResponseEntity<Map<String, Object>> getTenantConfig() {
        return ResponseEntity.ok(Map.ofEntries(
            Map.entry("foodIncludedInRent",     systemConfig.getRules().isFoodIncludedInRent()),
            Map.entry("allowMealCancellations", systemConfig.getRules().isAllowMealCancellations()),
            Map.entry("breakfastEnabled",       systemConfig.getRules().isBreakfastEnabled()),
            Map.entry("lunchEnabled",           systemConfig.getRules().isLunchEnabled()),
            Map.entry("dinnerEnabled",          systemConfig.getRules().isDinnerEnabled()),
            Map.entry("breakfastPrice",         systemConfig.getPricing().getBreakfast()),
            Map.entry("lunchPrice",             systemConfig.getPricing().getLunch()),
            Map.entry("dinnerPrice",            systemConfig.getPricing().getDinner()),
            Map.entry("hasWashingMachine",      systemConfig.getRules().isHasWashingMachine())
        ));
    }

    @GetMapping("/addons")
    public ResponseEntity<List<DailyLog>> getGuestAddons(Authentication auth) {
        Guest guest = guestService.getByUserId(auth.getName());
        return ResponseEntity.ok(dailyLogRepository.findAddonsByGuestId(guest.getId()));
    }
}
