package com.pgcrm.scheduler;

import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.multitenancy.TenantContext;
import com.pgcrm.multitenancy.TenantJobRunner;
import com.pgcrm.multitenancy.TenantRegistryService;
import com.pgcrm.repository.GuestRepository;
import com.pgcrm.repository.InvoiceRepository;
import com.pgcrm.service.AuditService;
import com.pgcrm.service.EmailService;
import com.pgcrm.service.InvoiceService;
import com.pgcrm.service.NotificationService;
import com.pgcrm.service.PricingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TwoTenantSchedulerTest {

    @Mock
    private TenantRegistryService tenantRegistryService;

    @Mock
    private GuestRepository guestRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private PricingService pricingService;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditService auditService;

    private TenantJobRunner tenantJobRunner;
    private MonthlyBillingScheduler monthlyBillingScheduler;
    private PaymentReminderScheduler paymentReminderScheduler;
    private OverdueInvoiceScheduler overdueInvoiceScheduler;
    private DataRetentionScheduler dataRetentionScheduler;

    private TenantContext.TenantInfo tenantA;
    private TenantContext.TenantInfo tenantB;

    @BeforeEach
    void setUp() {
        tenantA = new TenantContext.TenantInfo("id-a", "tenant_a", "t_tenant_a", "Tenant Alpha", "ACTIVE");
        tenantB = new TenantContext.TenantInfo("id-b", "tenant_b", "t_tenant_b", "Tenant Beta", "ACTIVE");

        when(tenantRegistryService.getAllActiveTenants()).thenReturn(List.of(tenantA, tenantB));

        tenantJobRunner = new TenantJobRunner(tenantRegistryService);

        monthlyBillingScheduler = new MonthlyBillingScheduler(
                guestRepository, invoiceService, pricingService, tenantJobRunner
        );

        paymentReminderScheduler = new PaymentReminderScheduler(
                invoiceRepository, emailService, notificationService, auditService, tenantJobRunner
        );

        overdueInvoiceScheduler = new OverdueInvoiceScheduler(
                invoiceRepository, notificationService, auditService, tenantJobRunner
        );

        dataRetentionScheduler = new DataRetentionScheduler(
                guestRepository, tenantJobRunner
        );

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testMonthlyBilling_ProcessesBothTenants_AndIsIdempotent() {
        Guest guestA = Guest.builder().id("g-a").fullName("Guest Alpha").active(true).build();
        Guest guestB = Guest.builder().id("g-b").fullName("Guest Beta").active(true).build();

        List<String> processedTenants = new ArrayList<>();

        when(guestRepository.findByActiveTrue()).thenAnswer(inv -> {
            String currentSlug = TenantContext.getCurrentTenantSlug();
            processedTenants.add(currentSlug);
            if ("tenant_a".equals(currentSlug)) {
                return List.of(guestA);
            } else {
                return List.of(guestB);
            }
        });

        when(invoiceService.generateInvoiceForGuest(any(Guest.class), anyInt(), anyInt()))
                .thenAnswer(inv -> {
                    Guest g = inv.getArgument(0);
                    return Invoice.builder()
                            .id("inv-" + g.getId())
                            .guest(g)
                            .status(InvoiceStatus.GENERATED)
                            .totalAmount(BigDecimal.valueOf(8000))
                            .build();
                });

        // 1. First Run
        monthlyBillingScheduler.generateMonthlyInvoices();

        assertEquals(2, processedTenants.size());
        assertTrue(processedTenants.contains("tenant_a"));
        assertTrue(processedTenants.contains("tenant_b"));
        verify(invoiceService, times(2)).generateInvoiceForGuest(any(Guest.class), anyInt(), anyInt());

        // 2. Re-Run (Idempotent: simulates second trigger where invoiceService returns existing invoice)
        monthlyBillingScheduler.generateMonthlyInvoices();
        verify(invoiceService, times(4)).generateInvoiceForGuest(any(Guest.class), anyInt(), anyInt());
    }

    @Test
    void testMonthlyBilling_TenantAFailure_DoesNotStopTenantB() {
        Guest guestB = Guest.builder().id("g-b").fullName("Guest Beta").active(true).build();

        when(guestRepository.findByActiveTrue()).thenAnswer(inv -> {
            String currentSlug = TenantContext.getCurrentTenantSlug();
            if ("tenant_a".equals(currentSlug)) {
                throw new RuntimeException("Database error in tenant_a schema");
            }
            return List.of(guestB);
        });

        when(invoiceService.generateInvoiceForGuest(eq(guestB), anyInt(), anyInt()))
                .thenReturn(Invoice.builder().id("inv-b").totalAmount(BigDecimal.valueOf(9000)).build());

        // Execute billing — tenant A will throw, but tenant B must complete!
        assertDoesNotThrow(() -> monthlyBillingScheduler.generateMonthlyInvoices());

        verify(invoiceService, times(1)).generateInvoiceForGuest(eq(guestB), anyInt(), anyInt());
    }

    @Test
    void testPaymentReminders_ProcessesBothTenants_AndIsIdempotent() {
        Guest guestA = Guest.builder().id("g-a").fullName("Guest A").email("a@test.com").build();
        Guest guestB = Guest.builder().id("g-b").fullName("Guest B").email("b@test.com").build();

        Invoice invA = Invoice.builder().id("inv-a").guest(guestA).totalAmount(BigDecimal.valueOf(5000)).month(5).year(2026).build();
        Invoice invB = Invoice.builder().id("inv-b").guest(guestB).totalAmount(BigDecimal.valueOf(6000)).month(5).year(2026).build();

        when(invoiceRepository.findUnpaidInvoicesDueBy(any(LocalDate.class))).thenAnswer(inv -> {
            String currentSlug = TenantContext.getCurrentTenantSlug();
            if ("tenant_a".equals(currentSlug)) {
                return List.of(invA);
            } else {
                return List.of(invB);
            }
        });

        paymentReminderScheduler.sendPaymentReminders();

        verify(emailService, times(1)).sendPaymentReminderEmail(eq(guestA), eq(invA));
        verify(emailService, times(1)).sendPaymentReminderEmail(eq(guestB), eq(invB));
        verify(invoiceRepository, times(2)).save(any(Invoice.class));
    }

    @Test
    void testOverdueScheduler_ProcessesBothTenants_FailureIsolation() {
        Invoice invB = Invoice.builder().id("inv-b").month(4).year(2026).totalAmount(BigDecimal.valueOf(7000)).build();

        when(invoiceRepository.findOverdueEligibleInvoices(any(LocalDate.class))).thenAnswer(inv -> {
            String currentSlug = TenantContext.getCurrentTenantSlug();
            if ("tenant_a".equals(currentSlug)) {
                throw new RuntimeException("Connection timeout in tenant A");
            }
            return List.of(invB);
        });

        assertDoesNotThrow(() -> overdueInvoiceScheduler.markOverdueInvoices());

        verify(invoiceRepository, times(1)).save(eq(invB));
        assertEquals(InvoiceStatus.OVERDUE, invB.getStatus());
    }

    @Test
    void testDataRetention_ProcessesBothTenants_FailureIsolation() {
        when(guestRepository.anonymizeExpiredGuests(any(LocalDate.class))).thenAnswer(inv -> {
            String currentSlug = TenantContext.getCurrentTenantSlug();
            if ("tenant_a".equals(currentSlug)) {
                throw new RuntimeException("Lock conflict in tenant A");
            }
            return 3;
        });

        assertDoesNotThrow(() -> dataRetentionScheduler.anonymizeExpiredGuestProfiles());

        verify(guestRepository, times(2)).anonymizeExpiredGuests(any(LocalDate.class));
    }
}
