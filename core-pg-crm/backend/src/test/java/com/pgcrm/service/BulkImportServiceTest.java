package com.pgcrm.service;

import com.pgcrm.dto.ImportSummaryDTO;
import com.pgcrm.entity.Building;
import com.pgcrm.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BulkImportServiceTest {

    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private FloorRepository floorRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private BedRepository bedRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GuestRepository guestRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private MeterReadingRepository meterReadingRepository;

    @InjectMocks
    private BulkImportService bulkImportService;

    private Building building;

    @BeforeEach
    void setUp() {
        building = new Building();
        building.setId("b1");
        building.setName("Test PG");
    }

    private byte[] createMockWorkbookBytes() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Guests");
            
            // Header
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Room Number");
            header.createCell(1).setCellValue("First Name");
            header.createCell(2).setCellValue("Last Name");
            header.createCell(3).setCellValue("Phone");
            header.createCell(4).setCellValue("Rent");
            header.createCell(5).setCellValue("Check-in Date");

            // Row 1
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("101");
            row1.createCell(1).setCellValue("Alice");
            row1.createCell(2).setCellValue("Smith");
            row1.createCell(3).setCellValue("9999999999");
            row1.createCell(4).setCellValue(7500);
            row1.createCell(5).setCellValue("2026-05-15");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    void testImportGuests_EmptySheet() throws IOException {
        // Create an empty workbook (just sheet, no rows)
        byte[] bytes;
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Empty");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            bytes = bos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "empty.xlsx", 
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        when(buildingRepository.findById("b1")).thenReturn(Optional.of(building));

        ImportSummaryDTO summary = bulkImportService.importGuests(file, "b1");
        assertNotNull(summary);
        assertEquals(0, summary.getTotalImported());
    }

    @Test
    void testImportGuests_MissingHeader_ThrowsException() throws IOException {
        // Workbook with no header row at index 0
        byte[] bytes;
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(1); // empty row at index 1
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            bytes = bos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile("file", "no_header.xlsx", 
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        when(buildingRepository.findById("b1")).thenReturn(Optional.of(building));

        assertThrows(RuntimeException.class, () -> {
            bulkImportService.importGuests(file, "b1");
        });
    }
}
