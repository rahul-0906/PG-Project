package com.pgcrm.controller;

import com.pgcrm.entity.*;
import com.pgcrm.repository.*;
import com.pgcrm.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
public class PgManagerController {

    private final GuestService guestService;
    private final GuestRepository guestRepository;
    private final BedRepository bedRepository;
    private final EbBillService ebBillService;
    private final SettlementService settlementService;
    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final DailyLogRepository dailyLogRepository;
    private final InvoiceService invoiceService;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // ── Dashboard ─────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(@RequestAttribute(required = false) String branchId) {
        long total   = branchId != null ? bedRepository.countTotalByBuildingId(branchId)   : bedRepository.countTotal();
        long vacant  = branchId != null ? bedRepository.countVacantByBuildingId(branchId)  : bedRepository.countVacant();
        long pending = branchId != null
                ? maintenanceTicketRepository.countByBuildingIdAndStatus(branchId,
                    com.pgcrm.entity.enums.MaintenanceStatus.OPEN)
                : maintenanceTicketRepository.countByStatus(
                    com.pgcrm.entity.enums.MaintenanceStatus.OPEN);
        return ResponseEntity.ok(Map.of(
                "totalBeds", total,
                "vacantBeds", vacant,
                "occupiedBeds", total - vacant,
                "pendingMaintenanceTickets", pending
        ));
    }

    // ── Guest Management ──────────────────────────────────────────

    @GetMapping("/guests")
    public ResponseEntity<List<Guest>> getActiveGuests(@RequestAttribute(required = false) String branchId) {
        if (branchId != null) return ResponseEntity.ok(guestRepository.findActiveGuestsByBuildingId(branchId));
        return ResponseEntity.ok(guestRepository.findByActiveTrue());
    }

    @PostMapping("/guests")
    public ResponseEntity<Guest> checkIn(@RequestBody Map<String, Object> body) {
        LocalDate checkInDate = body.get("checkInDate") != null
                ? LocalDate.parse(body.get("checkInDate").toString()) : LocalDate.now();
        Guest guest = guestService.checkIn(
                (String) body.get("bedId"),
                (String) body.get("fullName"),
                (String) body.get("email"),
                (String) body.get("phone"),
                (String) body.get("whatsappNumber"),
                body.get("advanceDeposit") != null
                        ? new BigDecimal(body.get("advanceDeposit").toString()) : BigDecimal.ZERO,
                checkInDate,
                (String) body.get("vehicleRegistration")
        );

        DailyLog log = DailyLog.builder()
                .guest(guest)
                .logDate(checkInDate)
                .isVeg(body.get("isVeg") == null || (Boolean) body.get("isVeg"))
                .breakfastOpted(body.get("breakfastOpted") != null && (Boolean) body.get("breakfastOpted"))
                .lunchOpted(body.get("lunchOpted") != null && (Boolean) body.get("lunchOpted"))
                .dinnerOpted(body.get("dinnerOpted") != null && (Boolean) body.get("dinnerOpted"))
                .build();
        dailyLogRepository.save(log);

        return ResponseEntity.ok(guest);
    }

    @PutMapping("/guests/{guestId}")
    public ResponseEntity<Guest> updateGuestDetails(@PathVariable String guestId,
                                                    @RequestBody Map<String, Object> body) {
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new RuntimeException("Guest not found"));

        if (body.containsKey("fullName")) guest.setFullName((String) body.get("fullName"));
        if (body.containsKey("email")) guest.setEmail((String) body.get("email"));
        if (body.containsKey("phone")) guest.setPhone((String) body.get("phone"));
        if (body.containsKey("whatsappNumber")) guest.setWhatsappNumber((String) body.get("whatsappNumber"));
        if (body.containsKey("advanceDeposit")) {
            guest.setAdvanceDeposit(new BigDecimal(body.get("advanceDeposit").toString()));
        }
        if (body.containsKey("kycStatus")) {
            guest.setKycStatus(com.pgcrm.entity.enums.KycStatus.valueOf((String) body.get("kycStatus")));
        }

        User user = guest.getUser();
        if (user != null) {
            if (body.containsKey("fullName")) user.setFullName((String) body.get("fullName"));
            if (body.containsKey("email")) user.setEmail((String) body.get("email"));
            if (body.containsKey("phone")) user.setPhone((String) body.get("phone"));
            userRepository.save(user);
        }

        return ResponseEntity.ok(guestRepository.save(guest));
    }

    @PostMapping("/guests/{guestId}/initiate-checkout")
    public ResponseEntity<Guest> initiateCheckout(@PathVariable String guestId) {
        return ResponseEntity.ok(settlementService.initiateCheckout(guestId));
    }

    @PostMapping("/guests/{guestId}/confirm-checkout")
    public ResponseEntity<SettlementService.SettlementResult> confirmCheckout(@PathVariable String guestId) {
        return ResponseEntity.ok(settlementService.confirmCheckout(guestId));
    }

    // ── EB Bill ───────────────────────────────────────────────────

    @PostMapping("/eb-bill")
    public ResponseEntity<EbBill> recordEbBill(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ebBillService.recordAndSplit(
                (String) body.get("blockId"),
                new BigDecimal(body.get("totalAmount").toString()),
                LocalDate.parse(body.get("periodStart").toString()),
                LocalDate.parse(body.get("periodEnd").toString())
        ));
    }

    /** Meter-based EB: manager provides previous + current reading per guest */
    @PostMapping("/eb-bill/meter")
    public ResponseEntity<EbBill> recordMeterBasedEbBill(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> readings =
            (java.util.List<Map<String, Object>>) body.get("readings");
        return ResponseEntity.ok(ebBillService.recordMeterBased(
                (String) body.get("blockId"),
                new BigDecimal(body.get("ratePerUnit").toString()),
                LocalDate.parse(body.get("periodStart").toString()),
                LocalDate.parse(body.get("periodEnd").toString()),
                readings
        ));
    }

    // ── Manager-side Daily Add-ons (egg/omelette/veg/WM) ─────────
    // Manager records these per guest — guest cannot edit add-ons

    @GetMapping("/guest-log/{guestId}/{date}")
    public ResponseEntity<DailyLog> getGuestLog(@PathVariable String guestId,
                                                 @PathVariable String date) {
        DailyLog log = dailyLogRepository.findByGuestIdAndLogDate(guestId, java.time.LocalDate.parse(date))
                .orElse(DailyLog.builder().logDate(java.time.LocalDate.parse(date)).build());
        return ResponseEntity.ok(log);
    }

    @PutMapping("/guest-log/{guestId}/{date}")
    public ResponseEntity<DailyLog> updateGuestAddons(@PathVariable String guestId,
                                                        @PathVariable String date,
                                                        @RequestBody Map<String, Object> body) {
        java.time.LocalDate logDate = java.time.LocalDate.parse(date);
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new RuntimeException("Guest not found"));
        DailyLog log = dailyLogRepository.findByGuestIdAndLogDate(guestId, logDate)
                .orElse(DailyLog.builder().guest(guest).logDate(logDate).build());

        if (body.containsKey("isVeg"))              log.setVeg((Boolean) body.get("isVeg"));
        if (body.containsKey("breakfastOpted"))      log.setBreakfastOpted((Boolean) body.get("breakfastOpted"));
        if (body.containsKey("lunchOpted"))          log.setLunchOpted((Boolean) body.get("lunchOpted"));
        if (body.containsKey("dinnerOpted"))         log.setDinnerOpted((Boolean) body.get("dinnerOpted"));
        if (body.containsKey("omeletteCount"))      log.setOmeletteCount(Integer.parseInt(body.get("omeletteCount").toString()));
        if (body.containsKey("boiledEggCount"))     log.setBoiledEggCount(Integer.parseInt(body.get("boiledEggCount").toString()));
        if (body.containsKey("washingMachineCount")) log.setWashingMachineCount(Integer.parseInt(body.get("washingMachineCount").toString()));

        return ResponseEntity.ok(dailyLogRepository.save(log));
    }

    /**
     * Returns all active guests with their log entry for a given date.
     * Used by the Manager Add-ons page to bulk-load state.
     */
    @GetMapping("/guests-with-log/{date}")
    public ResponseEntity<java.util.List<Map<String, Object>>> getGuestsWithLog(
            @PathVariable String date,
            @RequestAttribute(required = false) String branchId) {
        java.time.LocalDate logDate = java.time.LocalDate.parse(date);
        java.util.List<Guest> guestList = branchId != null
                ? guestRepository.findActiveGuestsByBuildingId(branchId)
                : guestRepository.findByActiveTrue();

        java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Guest g : guestList) {
            DailyLog log = dailyLogRepository.findByGuestIdAndLogDate(g.getId(), logDate).orElse(null);
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("guestId",             g.getId());
            item.put("guestName",           g.getFullName());
            item.put("isVeg",               log != null ? log.isVeg() : true);
            item.put("breakfastOpted",      log != null ? log.isBreakfastOpted() : false);
            item.put("lunchOpted",          log != null ? log.isLunchOpted() : false);
            item.put("dinnerOpted",         log != null ? log.isDinnerOpted() : false);
            item.put("omeletteCount",        log != null ? log.getOmeletteCount() : 0);
            item.put("boiledEggCount",       log != null ? log.getBoiledEggCount() : 0);
            item.put("washingMachineCount",  log != null ? log.getWashingMachineCount() : 0);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    // ── Food Count ────────────────────────────────────────────────

    @GetMapping("/food-count/{date}")
    public ResponseEntity<Object[]> getFoodCount(@RequestAttribute(required = false) String branchId,
                                                  @PathVariable String date) {
        Object[] result;
        if (branchId != null) {
            result = dailyLogRepository.getFoodCountByBuildingAndDate(branchId, LocalDate.parse(date));
        } else {
            result = dailyLogRepository.getFoodCountByDate(LocalDate.parse(date));
        }
        return ResponseEntity.ok(result);
    }

    // ── Maintenance ───────────────────────────────────────────────

    @GetMapping("/maintenance")
    public ResponseEntity<List<MaintenanceTicket>> getTickets(@RequestAttribute(required = false) String branchId) {
        if (branchId != null) return ResponseEntity.ok(maintenanceTicketRepository.findByBuildingId(branchId));
        return ResponseEntity.ok(maintenanceTicketRepository.findAll());
    }

    @PostMapping("/maintenance")
    public ResponseEntity<MaintenanceTicket> createTicket(
            @RequestBody MaintenanceTicket ticket,
            @RequestAttribute(required = false) String branchId) {
        if (ticket.getBuildingId() == null && branchId != null) {
            ticket.setBuildingId(branchId);
        }
        return ResponseEntity.ok(maintenanceTicketRepository.save(ticket));
    }

    @PutMapping("/maintenance/{id}/resolve")
    public ResponseEntity<MaintenanceTicket> resolveTicket(@PathVariable String id) {
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        ticket.setStatus(com.pgcrm.entity.enums.MaintenanceStatus.RESOLVED);
        ticket.setResolvedAt(java.time.LocalDateTime.now());
        return ResponseEntity.ok(maintenanceTicketRepository.save(ticket));
    }

    // ── Vacancies ─────────────────────────────────────────────────

    @GetMapping("/vacancies")
    public ResponseEntity<Map<String, Object>> getVacancies(@RequestAttribute(required = false) String branchId) {
        long total  = branchId != null ? bedRepository.countTotalByBuildingId(branchId)  : bedRepository.countTotal();
        long vacant = branchId != null ? bedRepository.countVacantByBuildingId(branchId) : bedRepository.countVacant();
        return ResponseEntity.ok(Map.of(
                "totalBeds", total, "vacantBeds", vacant, "occupiedBeds", total - vacant));
    }

    // ── Manual Invoice Generation (for testing / backfill) ────────

    @PostMapping("/billing/generate")
    public ResponseEntity<Object> generateInvoice(@RequestBody java.util.Map<String, Object> body) {
        String guestId = (String) body.get("guestId");
        int month = Integer.parseInt(body.get("month").toString());
        int year  = Integer.parseInt(body.get("year").toString());
        com.pgcrm.entity.Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new RuntimeException("Guest not found"));
        return ResponseEntity.ok(invoiceService.generateInvoiceForGuest(guest, month, year));
    }
}
