package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link InvoiceService}.
 *
 * <p>Focuses on the critical billing calculation logic, idempotency guard, and
 * the flat-rate rent business rule (BIZ-01). All repository and service
 * dependencies are mocked.</p>
 *
 * <p>Coverage targets:
 * <ul>
 *   <li>Flat-rate rent: always returns full monthly rent regardless of check-in date</li>
 *   <li>Zero rent for guest checked in after end of billing month</li>
 *   <li>Idempotency: second call to {@code generateInvoiceForGuest} returns existing invoice</li>
 *   <li>Food is skipped when {@code foodIncludedInRent = true}</li>
 *   <li>Laundry is skipped when {@code hasWashingMachine = false}</li>
 *   <li>Invoice total = rent + EB + food + laundry</li>
 * </ul></p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InvoiceService")
class InvoiceServiceTest {

    @Mock private InvoiceRepository        invoiceRepository;
    @Mock private GuestRepository          guestRepository;
    @Mock private DailyLogRepository       dailyLogRepository;
    @Mock private EbBillGuestRepository    ebBillGuestRepository;
    @Mock private SystemConfigProperties   systemConfig;
    @Mock private NotificationService      notificationService;
    @Mock private PricingService           pricingService;
    @Mock private BuildingConfigRepository buildingConfigRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private Guest guest;
    private SystemConfigProperties.Rules rules;
    private PricingService.EffectivePricing pricing;

    /**
     * Builds a full Bed -> Room -> Floor -> Building chain so that
     * {@link Guest#getMonthlyRent()} (which reads {@code bed.getRoom().getBaseRent()})
     * returns the expected test value without NPE.
     */
    private Guest buildGuestWithRent(BigDecimal rent, LocalDate checkInDate) {
        Room room = new Room();
        room.setBaseRent(rent);

        Bed bed = new Bed();
        bed.setId("bed-001");
        bed.setRoom(room);

        Guest g = Guest.builder()
                .id("guest-001")
                .fullName("Test Guest")
                .email("guest@pgcrm.com")
                .checkInDate(checkInDate)
                .active(true)
                .build();
        g.setBed(bed);
        return g;
    }

    @BeforeEach
    void setUp() {
        guest = buildGuestWithRent(new BigDecimal("5000.00"), LocalDate.of(2025, 1, 1));

        rules = mock(SystemConfigProperties.Rules.class);
        when(systemConfig.getRules()).thenReturn(rules);
        // Default: food NOT included, no washing machine, due day = 5
        when(rules.isFoodIncludedInRent()).thenReturn(false);
        when(rules.isHasWashingMachine()).thenReturn(false);
        when(rules.getPaymentDueDayOfMonth()).thenReturn(5);

        pricing = new PricingService.EffectivePricing(
                new BigDecimal("60"),   // breakfast
                new BigDecimal("80"),   // lunch
                new BigDecimal("100"),  // dinner
                new BigDecimal("50"),   // washingMachine  ← position 4 per record definition
                new BigDecimal("20"),   // omelette
                new BigDecimal("10")    // boiledEgg
        );
        when(pricingService.getEffectivePricing(any())).thenReturn(pricing);

        // Default: no EB shares, no daily logs
        when(ebBillGuestRepository.findByEbBill_BlockIdAndGuestId(anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(dailyLogRepository.findByGuestIdAndLogDateBetween(anyString(), any(), any()))
                .thenReturn(Collections.emptyList());
        // Prevent NPE in buildInvoiceMessage() which formats a WhatsApp template
        when(rules.getInvoiceWhatsappTemplate()).thenReturn(
                "Invoice generated for {name}: Rent={rent} Total={total}");
    }

    // ── Flat-Rate Rent (BIZ-01) ───────────────────────────────────────────────

    @Nested
    @DisplayName("Flat-Rate Rent (BIZ-01)")
    class FlatRateRentTests {

        @Test
        @DisplayName("charges full monthly rent regardless of mid-month check-in date")
        void rent_midMonthCheckIn_chargesFullRent() {
            // Guest checked in on the 15th — must still pay full rent (flat-rate model)
            guest.setCheckInDate(LocalDate.of(2025, 6, 15));

            when(invoiceRepository.findByGuestIdAndMonthAndYear("guest-001", 6, 2025))
                    .thenReturn(Optional.empty());
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

            Invoice invoice = invoiceService.generateInvoiceForGuest(guest, 6, 2025);

            // Total = rent (5000) + EB (0) + food (0) + laundry (0)
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("5000.00");
        }

        @Test
        @DisplayName("charges full monthly rent when guest checked in on first day of month")
        void rent_firstDayCheckIn_chargesFullRent() {
            guest.setCheckInDate(LocalDate.of(2025, 6, 1));

            when(invoiceRepository.findByGuestIdAndMonthAndYear("guest-001", 6, 2025))
                    .thenReturn(Optional.empty());
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

            Invoice invoice = invoiceService.generateInvoiceForGuest(guest, 6, 2025);

            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("5000.00");
        }

        @Test
        @DisplayName("charges zero rent when guest check-in is after end of billing month")
        void rent_checkInAfterBillingMonth_chargesZeroRent() {
            // Billing for June 2025 but guest only checks in July 1
            guest.setCheckInDate(LocalDate.of(2025, 7, 1));

            when(invoiceRepository.findByGuestIdAndMonthAndYear("guest-001", 6, 2025))
                    .thenReturn(Optional.empty());
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

            Invoice invoice = invoiceService.generateInvoiceForGuest(guest, 6, 2025);

            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("does NOT pro-rate even for last-day-of-month check-in (flat-rate enforced)")
        void rent_lastDayCheckIn_chargesFullRent_notProRated() {
            // If any accidental pro-rating were applied, this would be very small (1/30 * 5000)
            guest.setCheckInDate(LocalDate.of(2025, 6, 30));

            when(invoiceRepository.findByGuestIdAndMonthAndYear("guest-001", 6, 2025))
                    .thenReturn(Optional.empty());
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

            Invoice invoice = invoiceService.generateInvoiceForGuest(guest, 6, 2025);

            // Must be full rent, not ~167 (1-day pro-rata)
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("5000.00");
        }
    }

    // ── Idempotency Guard ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency Guard")
    class IdempotencyTests {

        @Test
        @DisplayName("returns existing invoice on second call — does not create duplicate")
        void generateInvoice_calledTwice_returnsExistingInvoice() {
            Invoice existing = Invoice.builder()
                    .id("inv-001")
                    .guest(guest)
                    .month(6)
                    .year(2025)
                    .totalAmount(new BigDecimal("5000.00"))
                    .status(InvoiceStatus.GENERATED)
                    .build();

            when(invoiceRepository.findByGuestIdAndMonthAndYear("guest-001", 6, 2025))
                    .thenReturn(Optional.of(existing));

            Invoice result = invoiceService.generateInvoiceForGuest(guest, 6, 2025);

            assertThat(result.getId()).isEqualTo("inv-001");
            // Repository.save must NOT be called again
            verify(invoiceRepository, never()).save(any());
        }
    }

    // ── Food / Laundry Config ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Food and Laundry Config")
    class FoodLaundryTests {

        @Test
        @DisplayName("invoice total excludes food when foodIncludedInRent = true")
        void generateInvoice_foodIncludedInRent_foodLineIsZero() {
            when(rules.isFoodIncludedInRent()).thenReturn(true);
            when(invoiceRepository.findByGuestIdAndMonthAndYear("guest-001", 6, 2025))
                    .thenReturn(Optional.empty());
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

            Invoice invoice = invoiceService.generateInvoiceForGuest(guest, 6, 2025);

            // Total should be rent only (5000) — food line should be zero
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("5000.00");
            // DailyLogRepository should NOT be queried for food when it's included
            verify(dailyLogRepository, never()).findByGuestIdAndLogDateBetween(anyString(), any(), any());
        }

        @Test
        @DisplayName("invoice total includes washing machine charges when hasWashingMachine = true")
        void generateInvoice_withWashingMachine_laundryAddedToTotal() {
            when(rules.isHasWashingMachine()).thenReturn(true);

            DailyLog logWithWm = DailyLog.builder()
                    .id("log-1")
                    .washingMachineCount(2)
                    .build();

            when(dailyLogRepository.findByGuestIdAndLogDateBetween(eq("guest-001"), any(), any()))
                    .thenReturn(List.of(logWithWm));
            when(invoiceRepository.findByGuestIdAndMonthAndYear("guest-001", 6, 2025))
                    .thenReturn(Optional.empty());
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

            Invoice invoice = invoiceService.generateInvoiceForGuest(guest, 6, 2025);

            // 2 washes * 50 = 100 laundry; total = 5000 + 0 (EB) + 0 (food) + 100
            assertThat(invoice.getTotalAmount()).isEqualByComparingTo("5100.00");
        }
    }

    // ── previewInvoice() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("previewInvoice()")
    class PreviewTests {

        @Test
        @DisplayName("preview marks alreadyGenerated = false when no invoice exists yet")
        void preview_notYetGenerated_returnsCorrectAmountsAndFlag() {
            when(invoiceRepository.findByGuestIdAndMonthAndYear("guest-001", 6, 2025))
                    .thenReturn(Optional.empty());

            InvoiceService.InvoicePreview preview = invoiceService.previewInvoice(guest, 6, 2025);

            assertThat(preview.rent).isEqualByComparingTo("5000.00");
            assertThat(preview.total).isEqualByComparingTo("5000.00");
            assertThat(preview.alreadyGenerated).isFalse();
            assertThat(preview.id).isNull();
        }

        @Test
        @DisplayName("preview marks alreadyGenerated = true when invoice exists")
        void preview_alreadyGenerated_flagIsTrue() {
            Invoice existing = Invoice.builder()
                    .id("inv-001")
                    .status(InvoiceStatus.GENERATED)
                    .build();

            when(invoiceRepository.findByGuestIdAndMonthAndYear("guest-001", 6, 2025))
                    .thenReturn(Optional.of(existing));

            InvoiceService.InvoicePreview preview = invoiceService.previewInvoice(guest, 6, 2025);

            assertThat(preview.alreadyGenerated).isTrue();
            assertThat(preview.id).isEqualTo("inv-001");
            assertThat(preview.status).isEqualTo("GENERATED");
        }
    }
}
