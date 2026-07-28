package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.InvoiceLineType;
import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.repository.BuildingConfigRepository;
import com.pgcrm.repository.DailyLogRepository;
import com.pgcrm.repository.EbBillGuestRepository;
import com.pgcrm.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private DailyLogRepository dailyLogRepository;
    @Mock
    private EbBillGuestRepository ebBillGuestRepository;
    @Mock
    private SystemConfigProperties systemConfig;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PricingService pricingService;
    @Mock
    private BuildingConfigRepository buildingConfigRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    private Guest guest;
    private Bed bed;
    private Room room;
    private Building building;

    @BeforeEach
    void setUp() {
        building = new Building();
        building.setId("bld1");

        Floor floor = new Floor();
        floor.setBuilding(building);

        room = new Room();
        room.setId("rm1");
        room.setFloor(floor);
        room.setBaseRent(BigDecimal.valueOf(9000));

        bed = new Bed();
        bed.setId("bed1");
        bed.setRoom(room);

        guest = new Guest();
        guest.setId("guest1");
        guest.setFullName("John Doe");
        guest.setEmail("john@example.com");
        guest.setBeds(new ArrayList<>(List.of(bed)));
        guest.setCheckInDate(LocalDate.of(2026, 1, 1));
    }

    @Test
    void testGenerateInvoiceForGuest_Idempotency() {
        Invoice existing = new Invoice();
        existing.setId("inv_existing");
        
        when(invoiceRepository.findByGuestIdAndMonthAndYear("guest1", 5, 2026))
                .thenReturn(Optional.of(existing));

        Invoice result = invoiceService.generateInvoiceForGuest(guest, 5, 2026);
        assertEquals(existing, result);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void testGenerateInvoiceForGuest_NewInvoice_FullMonthRent() {
        int month = 5;
        int year = 2026;

        when(invoiceRepository.findByGuestIdAndMonthAndYear("guest1", month, year))
                .thenReturn(Optional.empty());

        // Pricing rules mock
        SystemConfigProperties.Rules rules = new SystemConfigProperties.Rules();
        rules.setFoodIncludedInRent(false);
        rules.setHasWashingMachine(true);
        when(systemConfig.getRules()).thenReturn(rules);

        PricingService.EffectivePricing pricing = new PricingService.EffectivePricing(
                BigDecimal.valueOf(40),  // breakfast
                BigDecimal.valueOf(60),  // lunch
                BigDecimal.valueOf(50),  // dinner
                BigDecimal.valueOf(50),  // washingMachine
                BigDecimal.valueOf(10),  // omelette
                BigDecimal.valueOf(5)    // boiledEgg
        );
        
        when(pricingService.getEffectivePricing("bld1")).thenReturn(pricing);

        // Daily Logs mock for food calculation
        DailyLog log = DailyLog.builder()
                .logDate(LocalDate.of(2026, 5, 10))
                .breakfastOpted(true)
                .isVeg(true)
                .omeletteCount(1)
                .washingMachineCount(2)
                .build();
        when(dailyLogRepository.findByGuestIdAndLogDateBetween(eq("guest1"), any(), any()))
                .thenReturn(List.of(log));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        // Generate Invoice
        Invoice result = invoiceService.generateInvoiceForGuest(guest, month, year);

        assertNotNull(result);
        assertEquals(InvoiceStatus.GENERATED, result.getStatus());
        assertEquals(guest, result.getGuest());
        assertEquals(month, result.getMonth());
        assertEquals(year, result.getYear());

        // Verify line items (Rent: 9000, Food: 50 (40 breakfast + 10 omelette), Laundry: 100)
        List<InvoiceLineItem> items = result.getLineItems();
        assertEquals(4, items.size()); // Rent, EB, Food, Laundry
        
        BigDecimal total = items.stream()
                .map(InvoiceLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(total, result.getTotalAmount());
        assertEquals(BigDecimal.valueOf(9150), result.getTotalAmount());

        verify(invoiceRepository, atLeastOnce()).save(result);
        verify(notificationService, times(1)).sendBoth(any(Guest.class), anyString());
    }

    @Test
    void testPreviewInvoice() {
        int month = 5;
        int year = 2026;

        when(invoiceRepository.findByGuestIdAndMonthAndYear("guest1", month, year))
                .thenReturn(Optional.empty());

        SystemConfigProperties.Rules rules = new SystemConfigProperties.Rules();
        rules.setFoodIncludedInRent(true); // skips food line item
        rules.setHasWashingMachine(false); // skips laundry
        when(systemConfig.getRules()).thenReturn(rules);

        // Preview Invoice
        InvoiceService.InvoicePreview preview = invoiceService.previewInvoice(guest, month, year);

        assertNotNull(preview);
        assertFalse(preview.isAlreadyGenerated());
        assertEquals(BigDecimal.valueOf(9000), preview.getRent());
        assertEquals(BigDecimal.ZERO, preview.getFood());
        assertEquals(BigDecimal.ZERO, preview.getLaundry());
        assertEquals(BigDecimal.valueOf(9000), preview.getTotal());
    }
}
