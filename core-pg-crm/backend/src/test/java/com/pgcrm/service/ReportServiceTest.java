package com.pgcrm.service;

import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReportServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private GuestRepository guestRepository;

    @Mock
    private BedRepository bedRepository;

    @Mock
    private EbBillRepository ebBillRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private ReportService reportService;

    @Test
    void testGetMonthlyRevenueSummary_NoBuildingId() {
        int year = 2026;
        
        // Mock revenue breakdown rows for Month 1 (January)
        Object[] row1 = new Object[]{"RENT", BigDecimal.valueOf(50000)};
        Object[] row2 = new Object[]{"EB", BigDecimal.valueOf(4500)};
        Object[] row3 = new Object[]{"FOOD", BigDecimal.valueOf(6000)};
        Object[] row4 = new Object[]{"LAUNDRY", BigDecimal.valueOf(1500)};
        Object[] rowUnknown = new Object[]{"OTHER", BigDecimal.valueOf(1000)};
        
        when(invoiceRepository.getRevenueBreakdown(year, 1, null))
                .thenReturn(List.of(row1, row2, row3, row4, rowUnknown));
                
        // Return empty for other months
        for (int m = 2; m <= 12; m++) {
            when(invoiceRepository.getRevenueBreakdown(year, m, null))
                    .thenReturn(Collections.emptyList());
        }

        List<Map<String, Object>> result = reportService.getMonthlyRevenueSummary(year);

        assertNotNull(result);
        assertEquals(12, result.size());
        
        // Assert January values
        Map<String, Object> jan = result.get(0);
        assertEquals("Jan", jan.get("month"));
        assertEquals(1, jan.get("monthNum"));
        assertEquals(BigDecimal.valueOf(50000), jan.get("rent"));
        assertEquals(BigDecimal.valueOf(4500), jan.get("eb"));
        assertEquals(BigDecimal.valueOf(6000), jan.get("food"));
        assertEquals(BigDecimal.valueOf(1500), jan.get("laundry"));
        assertEquals(BigDecimal.valueOf(62000), jan.get("total"));
    }

    @Test
    void testGetMonthlyRevenueSummary_WithBuildingId() {
        int year = 2026;
        String buildingId = "b1";

        for (int m = 1; m <= 12; m++) {
            when(invoiceRepository.getRevenueBreakdown(year, m, buildingId))
                    .thenReturn(Collections.emptyList());
        }

        List<Map<String, Object>> result = reportService.getMonthlyRevenueSummary(year, buildingId);
        assertNotNull(result);
        assertEquals(12, result.size());
    }

    @Test
    void testGetOccupancyReport_NoBuildingId() {
        int year = 2026;
        
        when(bedRepository.countTotal()).thenReturn(100L);
        when(invoiceRepository.countDistinctGuestsByMonthYear(eq(year), anyInt(), eq(null)))
                .thenReturn(75L);

        List<Map<String, Object>> result = reportService.getOccupancyReport(year);

        assertNotNull(result);
        assertEquals(12, result.size());
        
        Map<String, Object> monthData = result.get(0);
        assertEquals(100L, monthData.get("totalBeds"));
        assertEquals(75L, monthData.get("occupiedBeds"));
        assertEquals(75L, monthData.get("occupancyPct"));
    }

    @Test
    void testGetOccupancyReport_WithBuildingId() {
        int year = 2026;
        String buildingId = "b1";
        
        when(bedRepository.countTotalByBuildingId(buildingId)).thenReturn(50L);
        when(invoiceRepository.countDistinctGuestsByMonthYear(eq(year), anyInt(), eq(buildingId)))
                .thenReturn(25L);

        List<Map<String, Object>> result = reportService.getOccupancyReport(year, buildingId);

        assertNotNull(result);
        assertEquals(12, result.size());
        
        Map<String, Object> monthData = result.get(0);
        assertEquals(50L, monthData.get("totalBeds"));
        assertEquals(25L, monthData.get("occupiedBeds"));
        assertEquals(50L, monthData.get("occupancyPct"));
    }

    @Test
    void testGetOccupancyReport_ZeroBeds() {
        int year = 2026;
        when(bedRepository.countTotal()).thenReturn(0L);
        when(invoiceRepository.countDistinctGuestsByMonthYear(eq(year), anyInt(), eq(null)))
                .thenReturn(0L);

        List<Map<String, Object>> result = reportService.getOccupancyReport(year);

        assertNotNull(result);
        assertEquals(0L, result.get(0).get("occupancyPct"));
    }

    @Test
    void testGetGuestTurnoverReport_NoBuildingId() {
        int year = 2026;
        
        when(guestRepository.countCheckInsByMonthYear(eq(year), anyInt(), eq(null)))
                .thenReturn(5L);
        when(guestRepository.countCheckOutsByMonthYear(eq(year), anyInt(), eq(null)))
                .thenReturn(2L);

        List<Map<String, Object>> result = reportService.getGuestTurnoverReport(year);

        assertNotNull(result);
        assertEquals(12, result.size());
        
        Map<String, Object> jan = result.get(0);
        assertEquals(5L, jan.get("checkIns"));
        assertEquals(2L, jan.get("checkOuts"));
    }

    @Test
    void testGetGuestTurnoverReport_WithBuildingId() {
        int year = 2026;
        String buildingId = "b1";
        
        when(guestRepository.countCheckInsByMonthYear(eq(year), anyInt(), eq(buildingId)))
                .thenReturn(4L);
        when(guestRepository.countCheckOutsByMonthYear(eq(year), anyInt(), eq(buildingId)))
                .thenReturn(3L);

        List<Map<String, Object>> result = reportService.getGuestTurnoverReport(year, buildingId);

        assertNotNull(result);
        assertEquals(12, result.size());
        
        Map<String, Object> jan = result.get(0);
        assertEquals(4L, jan.get("checkIns"));
        assertEquals(3L, jan.get("checkOuts"));
    }

    @Test
    void testGetPaymentSummary_NoBuildingId() {
        int year = 2026;
        
        when(invoiceRepository.countByYearAndStatus(year, InvoiceStatus.GENERATED, null)).thenReturn(10L);
        when(invoiceRepository.countByYearAndStatus(year, InvoiceStatus.PAID, null)).thenReturn(40L);
        when(invoiceRepository.countByYearAndStatus(year, InvoiceStatus.OVERDUE, null)).thenReturn(5L);
        when(invoiceRepository.sumPaidAmountByYear(year, null)).thenReturn(BigDecimal.valueOf(250000));

        Map<String, Object> result = reportService.getPaymentSummary(year);

        assertNotNull(result);
        assertEquals(10L, result.get("generated"));
        assertEquals(40L, result.get("paid"));
        assertEquals(5L, result.get("overdue"));
        assertEquals(BigDecimal.valueOf(250000), result.get("totalRevenue"));
    }

    @Test
    void testGetPaymentSummary_WithBuildingId() {
        int year = 2026;
        String buildingId = "b1";
        
        when(invoiceRepository.countByYearAndStatus(year, InvoiceStatus.GENERATED, buildingId)).thenReturn(8L);
        when(invoiceRepository.countByYearAndStatus(year, InvoiceStatus.PAID, buildingId)).thenReturn(30L);
        when(invoiceRepository.countByYearAndStatus(year, InvoiceStatus.OVERDUE, buildingId)).thenReturn(3L);
        when(invoiceRepository.sumPaidAmountByYear(year, buildingId)).thenReturn(null); // test null fallback

        Map<String, Object> result = reportService.getPaymentSummary(year, buildingId);

        assertNotNull(result);
        assertEquals(8L, result.get("generated"));
        assertEquals(30L, result.get("paid"));
        assertEquals(3L, result.get("overdue"));
        assertEquals(BigDecimal.ZERO, result.get("totalRevenue"));
    }
}
