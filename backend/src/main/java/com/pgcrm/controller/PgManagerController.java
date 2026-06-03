package com.pgcrm.controller;

import com.pgcrm.entity.*;
import com.pgcrm.repository.*;
import com.pgcrm.service.*;
import com.pgcrm.dto.GuestCheckInRequest;
import com.pgcrm.dto.GuestResponse;
import com.pgcrm.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.pgcrm.mapper.GuestMapper;

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
    private final BuildingRepository buildingRepository;
    private final DailyLogService dailyLogService;
    private final GuestMapper guestMapper;
    private final InvoiceRepository invoiceRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @GetMapping("/assigned-buildings")
    public ResponseEntity<List<Building>> getAssignedBuildings(Authentication auth) {
        User user = userRepository.findById(auth.getName()).orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getRole() == com.pgcrm.entity.enums.Role.PG_OWNER) {
            return ResponseEntity.ok(buildingRepository.findAll());
        }
        if (user.getBranchId() == null || user.getBranchId().isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        List<String> ids = java.util.Arrays.asList(user.getBranchId().split(","));
        return ResponseEntity.ok(buildingRepository.findAllById(ids));
    }

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
        String buildingName = "All Buildings";
        if (branchId != null) {
            buildingName = buildingRepository.findById(branchId)
                    .map(Building::getName)
                    .orElse("Main Building");
        }
        return ResponseEntity.ok(Map.of(
                "totalBeds", total,
                "vacantBeds", vacant,
                "occupiedBeds", total - vacant,
                "pendingMaintenanceTickets", pending,
                "buildingName", buildingName
        ));
    }

    // ── Guest Management ──────────────────────────────────────────

    @GetMapping("/guests")
    public ResponseEntity<List<GuestResponse>> getActiveGuests(@RequestAttribute(required = false) String branchId) {
        List<Guest> guests;
        if (branchId != null) {
            guests = guestRepository.findActiveGuestsByBuildingId(branchId);
        } else {
            guests = guestRepository.findByActiveTrue();
        }
        List<GuestResponse> response = guests.stream()
                .map(guestMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/guests")
    public ResponseEntity<GuestResponse> checkIn(@RequestBody GuestCheckInRequest body) {
        LocalDate checkInDate = body.getCheckInDate() != null
                ? body.getCheckInDate() : LocalDate.now();
        Guest guest = guestService.checkIn(
                body.getBedId(),
                body.getFullName(),
                body.getEmail(),
                body.getPhone(),
                body.getWhatsappNumber(),
                body.getAdvanceDeposit() != null
                        ? body.getAdvanceDeposit() : BigDecimal.ZERO,
                checkInDate,
                body.getVehicleRegistration()
        );

        boolean isVeg = body.isVeg();
        boolean breakfastOpted = body.isBreakfastOpted();
        boolean lunchOpted = body.isLunchOpted();
        boolean dinnerOpted = body.isDinnerOpted();

        guest.setVegPreference(isVeg);
        guest.setBreakfastPreference(breakfastOpted);
        guest.setLunchPreference(lunchOpted);
        guest.setDinnerPreference(dinnerOpted);
        guestRepository.save(guest);

        DailyLog log = DailyLog.builder()
                .guest(guest)
                .logDate(checkInDate)
                .isVeg(isVeg)
                .breakfastOpted(breakfastOpted)
                .lunchOpted(lunchOpted)
                .dinnerOpted(dinnerOpted)
                .build();
        dailyLogRepository.save(log);

        return ResponseEntity.ok(guestMapper.toResponse(guest));
    }

    @PutMapping("/guests/{guestId}")
    public ResponseEntity<GuestResponse> updateGuestDetails(@PathVariable String guestId,
                                                    @RequestBody Map<String, Object> body) {
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found: " + guestId));

        if (body.containsKey("fullName")) guest.setFullName((String) body.get("fullName"));
        if (body.containsKey("phone")) guest.setPhone((String) body.get("phone"));
        if (body.containsKey("whatsappNumber")) guest.setWhatsappNumber((String) body.get("whatsappNumber"));
        if (body.containsKey("advanceDeposit")) {
            guest.setAdvanceDeposit(new BigDecimal(body.get("advanceDeposit").toString()));
        }
        if (body.containsKey("kycStatus")) {
            guest.setKycStatus(com.pgcrm.entity.enums.KycStatus.valueOf((String) body.get("kycStatus")));
        }

        User user = guest.getUser();
        if (body.containsKey("email")) {
            String newEmail = ((String) body.get("email")).trim();
            if (!newEmail.equalsIgnoreCase(guest.getEmail())) {
                java.util.Optional<User> existingUser = userRepository.findByEmailIgnoreCase(newEmail);
                if (existingUser.isPresent() && (user == null || !existingUser.get().getId().equals(user.getId()))) {
                    throw new IllegalArgumentException("Email is already in use by another account");
                }
                guest.setEmail(newEmail);
                if (user != null) {
                    user.setEmail(newEmail);
                }
            }
        }

        if (user != null) {
            if (body.containsKey("fullName")) user.setFullName((String) body.get("fullName"));
            if (body.containsKey("phone")) user.setPhone((String) body.get("phone"));
            userRepository.save(user);
        }

        return ResponseEntity.ok(guestMapper.toResponse(guestRepository.save(guest)));
    }

    @PutMapping("/guests/{guestId}/switch-bed/{newBedId}")
    public ResponseEntity<GuestResponse> switchBed(@PathVariable String guestId,
                                                    @PathVariable String newBedId) {
        return ResponseEntity.ok(guestService.switchBed(guestId, newBedId));
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
            if (log == null) {
                log = dailyLogService.createDefaultLog(g, logDate);
            }
            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("guestId",             g.getId());
            item.put("guestName",           g.getFullName());
            item.put("isVeg",               log.isVeg());
            item.put("breakfastOpted",      log.isBreakfastOpted());
            item.put("lunchOpted",          log.isLunchOpted());
            item.put("dinnerOpted",         log.isDinnerOpted());
            item.put("omeletteCount",        log.getOmeletteCount());
            item.put("boiledEggCount",       log.getBoiledEggCount());
            item.put("washingMachineCount",  log.getWashingMachineCount());
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

    @PutMapping("/maintenance/{ticketId}/resolve")
    public ResponseEntity<MaintenanceTicket> resolveTicket(@PathVariable String ticketId) {
        MaintenanceTicket ticket = maintenanceTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        ticket.setStatus(com.pgcrm.entity.enums.MaintenanceStatus.RESOLVED);
        ticket.setResolvedAt(java.time.LocalDateTime.now());
        
        // Audit
        auditService.log(com.pgcrm.entity.enums.AuditAction.MAINTENANCE_RESOLVED, 
                "MaintenanceTicket", 
                ticketId, 
                "Ticket status updated to RESOLVED");
                 
        return ResponseEntity.ok(maintenanceTicketRepository.save(ticket));
    }

    @PostMapping("/invoices/{id}/verify-cash")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Invoice> verifyCash(Authentication auth, @PathVariable String id) {
        User manager = userRepository.findById(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));
        
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + id));
        
        if (invoice.getStatus() == com.pgcrm.entity.enums.InvoiceStatus.PAID) {
            throw new RuntimeException("Invoice is already paid");
        }
        
        Guest guest = invoice.getGuest();
        String guestBuildingId = (guest.getBed() != null && guest.getBed().getRoom() != null
                && guest.getBed().getRoom().getFloor() != null)
                ? guest.getBed().getRoom().getFloor().getBuilding().getId() : null;
        
        if (manager.getRole() == com.pgcrm.entity.enums.Role.PG_MANAGER) {
            String branchId = manager.getBranchId();
            if (guestBuildingId == null || branchId == null) {
                throw new RuntimeException("Unauthorized: Manager not assigned to this guest's building");
            }
            List<String> allowedBranches = java.util.Arrays.asList(branchId.split(","));
            if (!allowedBranches.contains(guestBuildingId)) {
                throw new RuntimeException("Unauthorized: Manager not assigned to this guest's building");
            }
        }

        invoice.setStatus(com.pgcrm.entity.enums.InvoiceStatus.PAID);
        invoice.setPaymentMethod("CASH");
        invoice.setPaidAt(java.time.LocalDateTime.now());
        invoiceRepository.save(invoice);

        // Audit Log
        auditService.log(com.pgcrm.entity.enums.AuditAction.PAYMENT_RECEIVED, "Invoice", id,
            "Cash payment verified by Manager " + manager.getFullName() + " for Invoice " + id + " amount: ₹" + invoice.getTotalAmount());

        // Notify guest
        notificationService.sendBoth(guest, "Your cash payment of ₹" + invoice.getTotalAmount() + " for invoice " + invoice.getMonth() + "/" + invoice.getYear() + " has been verified and received. Thank you!");

        return ResponseEntity.ok(invoice);
    }

    @GetMapping("/invoices/pending-cash")
    public ResponseEntity<List<Invoice>> getPendingCashInvoices(Authentication auth) {
        User manager = userRepository.findById(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));
        
        List<Invoice> pending = invoiceRepository.findByStatus(com.pgcrm.entity.enums.InvoiceStatus.PENDING_CASH_VERIFICATION);
        
        if (manager.getRole() == com.pgcrm.entity.enums.Role.PG_MANAGER) {
            String branchId = manager.getBranchId();
            pending = pending.stream()
                .filter(inv -> {
                    Guest guest = inv.getGuest();
                    String guestBuildingId = (guest != null && guest.getBed() != null && guest.getBed().getRoom() != null
                            && guest.getBed().getRoom().getFloor() != null)
                            ? guest.getBed().getRoom().getFloor().getBuilding().getId() : null;
                    if (guestBuildingId == null || branchId == null) return false;
                    List<String> allowedBranches = java.util.Arrays.asList(branchId.split(","));
                    return allowedBranches.contains(guestBuildingId);
                })
                .collect(java.util.stream.Collectors.toList());
        }
        
        return ResponseEntity.ok(pending);
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

    // ── Invoice Generator Module ──────────────────────────────────

    /**
     * GET /api/manager/invoices/preview?month=5&year=2026
     * Returns a computed (unsaved) invoice breakdown for every active guest.
     */
    @GetMapping("/invoices/preview")
    public ResponseEntity<List<com.pgcrm.service.InvoiceService.InvoicePreview>> previewInvoices(
            @RequestParam int month,
            @RequestParam int year,
            @RequestAttribute(required = false) String branchId) {

        List<com.pgcrm.entity.Guest> guests = branchId != null
                ? guestRepository.findActiveGuestsByBuildingId(branchId)
                : guestRepository.findByActiveTrue();

        List<com.pgcrm.service.InvoiceService.InvoicePreview> previews = guests.stream()
                .map(g -> invoiceService.previewInvoice(g, month, year))
                .toList();

        return ResponseEntity.ok(previews);
    }

    /**
     * POST /api/manager/invoices/generate-all
     * Generates invoices for all guests who don't yet have one for this month/year.
     * Skips guests who already have an invoice.
     */
    @PostMapping("/invoices/generate-all")
    public ResponseEntity<Map<String, Object>> generateAllInvoices(
            @RequestBody Map<String, Object> body,
            @RequestAttribute(required = false) String branchId) {

        int month = Integer.parseInt(body.get("month").toString());
        int year  = Integer.parseInt(body.get("year").toString());

        List<com.pgcrm.entity.Guest> guests = branchId != null
                ? guestRepository.findActiveGuestsByBuildingId(branchId)
                : guestRepository.findByActiveTrue();

        int generated = 0, skipped = 0, failed = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (com.pgcrm.entity.Guest guest : guests) {
            try {
                com.pgcrm.entity.Invoice inv = invoiceService.generateInvoiceForGuest(guest, month, year);
                // generateInvoiceForGuest returns existing if already present — check by comparing generatedAt
                if (inv.getGeneratedAt() != null &&
                    inv.getGeneratedAt().isAfter(java.time.LocalDateTime.now().minusSeconds(5))) {
                    generated++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                errors.add(guest.getFullName() + ": " + e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "generated", generated,
                "skipped", skipped,
                "failed", failed,
                "errors", errors,
                "total", guests.size()
        ));
    }

    /**
     * GET /api/manager/monthly-meals?month=5&year=2026
     * Returns a daily breakdown of meal opt-ins and add-on counts for all active guests in a month.
     */
    @GetMapping("/monthly-meals")
    public ResponseEntity<java.util.List<Map<String, Object>>> getMonthlyMeals(
            @RequestParam int month,
            @RequestParam int year,
            @RequestAttribute(required = false) String branchId) {

        java.time.LocalDate start = java.time.LocalDate.of(year, month, 1);
        java.time.LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        java.util.List<Guest> guests = branchId != null
                ? guestRepository.findActiveGuestsByBuildingId(branchId)
                : guestRepository.findByActiveTrue();

        java.util.List<DailyLog> logs = dailyLogRepository.findByLogDateBetween(start, end);

        // Group logs by guest ID and logDate
        java.util.Map<String, java.util.Map<java.time.LocalDate, DailyLog>> logsMap = new java.util.HashMap<>();
        for (DailyLog log : logs) {
            if (log.getGuest() != null) {
                logsMap.computeIfAbsent(log.getGuest().getId(), k -> new java.util.HashMap<>())
                       .put(log.getLogDate(), log);
            }
        }

        java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Guest g : guests) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("guestId", g.getId());
            item.put("guestName", g.getFullName());
            item.put("bedLabel", g.getBed() != null ? g.getBed().getBedLabel() : "—");

            java.util.Map<String, Object> daysMap = new java.util.LinkedHashMap<>();
            java.util.Map<java.time.LocalDate, DailyLog> guestLogs = logsMap.getOrDefault(g.getId(), java.util.Map.of());

            for (java.time.LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                DailyLog log = guestLogs.get(date);
                if (log == null) {
                    log = dailyLogService.createDefaultLog(g, date);
                }
                daysMap.put(date.toString(), java.util.Map.ofEntries(
                    java.util.Map.entry("breakfast", log.isBreakfastOpted()),
                    java.util.Map.entry("lunch", log.isLunchOpted()),
                    java.util.Map.entry("dinner", log.isDinnerOpted()),
                    java.util.Map.entry("isVeg", log.isVeg()),
                    java.util.Map.entry("omelettes", log.getOmeletteCount()),
                    java.util.Map.entry("boiledEggs", log.getBoiledEggCount()),
                    java.util.Map.entry("laundry", log.getWashingMachineCount())
                ));
            }
            item.put("days", daysMap);
            result.add(item);
        }

        return ResponseEntity.ok(result);
    }
}

