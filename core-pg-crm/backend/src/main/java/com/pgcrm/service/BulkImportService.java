package com.pgcrm.service;

import com.pgcrm.dto.ImportSummaryDTO;
import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.*;
import com.pgcrm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkImportService {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final UserRepository userRepository;
    private final GuestRepository guestRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final InvoiceRepository invoiceRepository;
    private final MeterReadingRepository meterReadingRepository;

    /**
     * Imports guests and rooms from an uploaded Excel file.
     * Transactional boundary ensures atomic rollbacks on any parsing/database failure.
     */
    @Transactional
    public ImportSummaryDTO importGuests(MultipartFile file, String buildingId) {
        log.info("Starting bulk guest import for building: {}", buildingId);

        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Target building not found with ID: " + buildingId));

        int totalImported = 0;
        int roomsCreated = 0;

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                log.warn("Bulk import Excel sheet contains no rows (excluding potential header).");
                return new ImportSummaryDTO(0, 0);
            }

            // Parse header row to resolve column indices
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel sheet is missing a header row.");
            }

            int roomNumIdx = -1;
            int firstNameIdx = -1;
            int lastNameIdx = -1;
            int phoneIdx = -1;
            int rentIdx = -1;
            int checkInIdx = -1;
            int openingRentArrearsIdx = -1;
            int initialEbReadingIdx = -1;
            int mealPlanOptInIdx = -1;

            for (Cell cell : headerRow) {
                if (cell == null) continue;
                String header = getCellValueAsString(cell).toLowerCase();
                if (header.contains("room number") || header.contains("room_number") || header.equals("room")) {
                    roomNumIdx = cell.getColumnIndex();
                } else if (header.contains("first name") || header.contains("first_name") || header.equals("firstname")) {
                    firstNameIdx = cell.getColumnIndex();
                } else if (header.contains("last name") || header.contains("last_name") || header.equals("lastname")) {
                    lastNameIdx = cell.getColumnIndex();
                } else if (header.contains("phone") || header.contains("phone_number") || header.contains("contact")) {
                    phoneIdx = cell.getColumnIndex();
                } else if (header.contains("monthly rent") || header.contains("rent") || header.contains("price")) {
                    rentIdx = cell.getColumnIndex();
                } else if (header.contains("check-in date") || header.contains("check_in_date") || header.contains("checkin") || header.contains("date")) {
                    checkInIdx = cell.getColumnIndex();
                } else if (header.contains("opening rent arrears") || header.contains("arrears") || header.contains("opening_rent_arrears")) {
                    openingRentArrearsIdx = cell.getColumnIndex();
                } else if (header.contains("initial eb reading") || header.contains("eb reading") || header.contains("initial_eb_reading") || header.contains("reading")) {
                    initialEbReadingIdx = cell.getColumnIndex();
                } else if (header.contains("meal plan opt-in") || header.contains("meal plan") || header.contains("meal_plan") || header.contains("meal")) {
                    mealPlanOptInIdx = cell.getColumnIndex();
                }
            }

            // Fallback to defaults if headers are not matched by labels
            if (roomNumIdx == -1) roomNumIdx = 0;
            if (firstNameIdx == -1) firstNameIdx = 1;
            if (lastNameIdx == -1) lastNameIdx = 2;
            if (phoneIdx == -1) phoneIdx = 3;
            if (rentIdx == -1) rentIdx = 4;
            if (checkInIdx == -1) checkInIdx = 5;
            if (openingRentArrearsIdx == -1) openingRentArrearsIdx = 6;
            if (initialEbReadingIdx == -1) initialEbReadingIdx = 7;
            if (mealPlanOptInIdx == -1) mealPlanOptInIdx = 8;

            log.info("Excel Headers Mapped -> Room: {}, FirstName: {}, LastName: {}, Phone: {}, Rent: {}, CheckIn: {}, Arrears: {}, EBReading: {}, MealPlan: {}",
                    roomNumIdx, firstNameIdx, lastNameIdx, phoneIdx, rentIdx, checkInIdx, openingRentArrearsIdx, initialEbReadingIdx, mealPlanOptInIdx);

            // Iterate and import rows
            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // Ensure basic elements exist
                Cell roomCell = row.getCell(roomNumIdx);
                Cell firstCell = row.getCell(firstNameIdx);
                if (roomCell == null && firstCell == null) {
                    continue; // Skip empty row
                }

                String roomNumber = getCellValueAsString(roomCell);
                String firstName = getCellValueAsString(row.getCell(firstNameIdx));
                String lastName = getCellValueAsString(row.getCell(lastNameIdx));
                String phone = getCellValueAsString(row.getCell(phoneIdx));
                BigDecimal rentVal = getCellValueAsBigDecimal(row.getCell(rentIdx));
                LocalDate checkInDate = getCellValueAsDate(row.getCell(checkInIdx));

                // Validate row fields
                if (roomNumber.isEmpty() || firstName.isEmpty() || phone.isEmpty()) {
                    log.warn("Skipping row {}: missing mandatory fields (Room: '{}', First Name: '{}', Phone: '{}')",
                            r, roomNumber, firstName, phone);
                    continue;
                }

                // Resolve Room & Floor
                Room room = resolveOrCreateRoom(roomNumber, rentVal, building, buildingId);
                if (room.getId() == null || room.getBeds().isEmpty()) {
                    roomsCreated++;
                }

                // Assign to vacant Bed (dynamic expansion if needed)
                Bed bed = assignVacantBed(room);

                // Create user profile
                String email = "guest." + phone + "@pgcrm.com";
                String fullName = firstName + (lastName.isEmpty() ? "" : " " + lastName);

                User user = resolveOrCreateUser(email, fullName, phone);

                // Check-in Guest
                Guest guest = resolveOrCreateGuest(user, fullName, email, phone, checkInDate, building, bed);

                // Opening Rent Arrears
                BigDecimal arrearsVal = BigDecimal.ZERO;
                if (openingRentArrearsIdx < row.getLastCellNum()) {
                    arrearsVal = getCellValueAsBigDecimal(row.getCell(openingRentArrearsIdx));
                }

                if (arrearsVal.compareTo(BigDecimal.ZERO) > 0) {
                    Invoice arrearsInvoice = Invoice.builder()
                            .guest(guest)
                            .month(LocalDate.now().getMonthValue())
                            .year(LocalDate.now().getYear())
                            .status(InvoiceStatus.GENERATED)
                            .dueDate(LocalDate.now())
                            .totalAmount(arrearsVal)
                            .lineItems(new ArrayList<>())
                            .build();

                    InvoiceLineItem lineItem = InvoiceLineItem.builder()
                            .invoice(arrearsInvoice)
                            .type(InvoiceLineType.RENT)
                            .description("Opening Balance (Migrated Arrears)")
                            .amount(arrearsVal)
                            .build();

                    arrearsInvoice.getLineItems().add(lineItem);
                    invoiceRepository.save(arrearsInvoice);
                    log.info("Created migrated arrears invoice of ₹{} for guest: {}", arrearsVal, guest.getFullName());
                }

                // Baseline EB Reading
                BigDecimal initialEbVal = BigDecimal.ZERO;
                if (initialEbReadingIdx < row.getLastCellNum()) {
                    initialEbVal = getCellValueAsBigDecimal(row.getCell(initialEbReadingIdx));
                }

                if (initialEbVal.compareTo(BigDecimal.ZERO) > 0) {
                    boolean hasBaseline = !meterReadingRepository.findByRoomIdAndType(room.getId(), "MIGRATION_BASELINE").isEmpty();
                    if (!hasBaseline) {
                        MeterReading reading = MeterReading.builder()
                                .room(room)
                                .type("MIGRATION_BASELINE")
                                .value(initialEbVal)
                                .date(LocalDate.now())
                                .build();
                        meterReadingRepository.save(reading);
                        log.info("Recorded migration baseline EB reading of {} for room: {}", initialEbVal, room.getRoomNumber());
                    }
                }

                // Meal Plan Opt-In
                boolean hasMealPlan = false;
                if (mealPlanOptInIdx < row.getLastCellNum()) {
                    String mealPlanStr = getCellValueAsString(row.getCell(mealPlanOptInIdx)).trim().toLowerCase();
                    hasMealPlan = mealPlanStr.equals("yes") || mealPlanStr.equals("y") || mealPlanStr.equals("true") || mealPlanStr.equals("1");
                }
                guest.setHasMealPlan(hasMealPlan);
                guestRepository.save(guest);

                totalImported++;
            }

        } catch (Exception e) {
            log.error("Fatal error during bulk import orchestration: {}", e.getMessage(), e);
            throw new RuntimeException("Excel parsing or transactional import failed: " + e.getMessage(), e);
        }

        log.info("Bulk Import Done: totalImported={}, roomsCreated={}", totalImported, roomsCreated);
        return new ImportSummaryDTO(totalImported, roomsCreated);
    }

    private Room resolveOrCreateRoom(String roomNumber, BigDecimal rentVal, Building building, String buildingId) {
        // Query existing rooms in building
        Room room = roomRepository.findByFloor_Building_Id(buildingId).stream()
                .filter(r -> r.getRoomNumber().equalsIgnoreCase(roomNumber))
                .findFirst()
                .orElse(null);

        if (room != null) {
            return room;
        }

        // Room does not exist; parse floor from first char if digit, else default to Floor 1
        int floorNum = 1;
        if (Character.isDigit(roomNumber.charAt(0))) {
            floorNum = Character.getNumericValue(roomNumber.charAt(0));
        }

        final int targetFloorNumber = floorNum;
        Floor floor = floorRepository.findByBuildingId(buildingId).stream()
                .filter(f -> f.getFloorNumber() == targetFloorNumber)
                .findFirst()
                .orElseGet(() -> {
                    Floor newFloor = Floor.builder()
                            .building(building)
                            .floorNumber(targetFloorNumber)
                            .floorLabel("Floor " + targetFloorNumber)
                            .build();
                    return floorRepository.save(newFloor);
                });

        BigDecimal finalRent = rentVal.compareTo(BigDecimal.ZERO) > 0 ? rentVal : BigDecimal.valueOf(5000.00);

        Room newRoom = Room.builder()
                .floor(floor)
                .roomNumber(roomNumber)
                .baseRent(finalRent)
                .sharingType(2) // Default to double occupancy
                .isAc(true)
                .beds(new ArrayList<>())
                .build();

        newRoom = roomRepository.save(newRoom);

        // Provision standard beds (A and B)
        Bed bedA = Bed.builder().room(newRoom).bedLabel(roomNumber + "-A").status(BedStatus.VACANT).build();
        Bed bedB = Bed.builder().room(newRoom).bedLabel(roomNumber + "-B").status(BedStatus.VACANT).build();

        bedRepository.save(bedA);
        bedRepository.save(bedB);

        newRoom.getBeds().add(bedA);
        newRoom.getBeds().add(bedB);
        newRoom = roomRepository.save(newRoom);

        auditService.log(AuditAction.ROOM_CREATED, "Room", newRoom.getId(),
                "Room " + roomNumber + " created with beds via bulk import");

        return newRoom;
    }

    private Bed assignVacantBed(Room room) {
        return room.getBeds().stream()
                .filter(b -> b.getStatus() == BedStatus.VACANT)
                .findFirst()
                .orElseGet(() -> {
                    // All beds occupied -> dynamically expand sharing capacity
                    int nextIndex = room.getBeds().size();
                    char suffix = (char) ('A' + nextIndex);
                    String label = room.getRoomNumber() + "-" + suffix;
                    
                    Bed newBed = Bed.builder()
                            .room(room)
                            .bedLabel(label)
                            .status(BedStatus.VACANT)
                            .build();
                    Bed savedBed = bedRepository.save(newBed);
                    
                    room.getBeds().add(savedBed);
                    room.setSharingType(room.getBeds().size());
                    roomRepository.save(room);
                    
                    auditService.log(AuditAction.BED_ADDED, "Bed", savedBed.getId(),
                            "Added capacity bed " + label + " dynamically during import");
                    
                    return savedBed;
                });
    }

    private User resolveOrCreateUser(String email, String fullName, String phone) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User newUser = User.builder()
                    .email(email)
                    .fullName(fullName)
                    .phone(phone)
                    .password(passwordEncoder.encode("Welcome123"))
                    .role(Role.GUEST)
                    .active(true)
                    .firstLogin(true)
                    .mustChangePassword(true)
                    .build();
            return userRepository.save(newUser);
        });
    }

    private Guest resolveOrCreateGuest(User user, String fullName, String email, String phone, LocalDate checkInDate, Building building, Bed bed) {
        Guest guest = guestRepository.findByUserId(user.getId()).orElse(null);
        if (guest == null) {
            guest = Guest.builder()
                    .user(user)
                    .fullName(fullName)
                    .email(email)
                    .phone(phone)
                    .whatsappNumber(phone)
                    .checkInDate(checkInDate)
                    .active(true)
                    .building(building)
                    .isAnonymized(false)
                    .build();
        } else {
            guest.setActive(true);
            guest.setBuilding(building);
            guest.setCheckInDate(checkInDate);
        }
        guest.setBeds(List.of(bed));
        guest = guestRepository.save(guest);

        // Update Bed occupancy
        bed.setStatus(BedStatus.OCCUPIED);
        bedRepository.save(bed);

        auditService.log(AuditAction.GUEST_CHECKIN, "Guest", guest.getId(),
                "Checked in guest: " + fullName + " to bed " + bed.getBedLabel() + " via bulk import");
        return guest;
    }

    // ── Excel Parsing Helpers ──────────────────────────────────────────

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    private BigDecimal getCellValueAsBigDecimal(Cell cell) {
        if (cell == null) {
            return BigDecimal.ZERO;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String val = getCellValueAsString(cell);
        try {
            return new BigDecimal(val);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate getCellValueAsDate(Cell cell) {
        if (cell == null) {
            return LocalDate.now();
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            Date d = cell.getDateCellValue();
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String val = getCellValueAsString(cell);
        try {
            return LocalDate.parse(val);
        } catch (Exception e) {
            return LocalDate.now(); // Fallback to current date
        }
    }
}
