package com.pgcrm.scheduler;

import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Invoice;
import com.pgcrm.multitenancy.TenantJobRunner;
import com.pgcrm.repository.GuestRepository;
import com.pgcrm.service.InvoiceService;
import com.pgcrm.service.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlyBillingScheduler {

    private final GuestRepository guestRepository;
    private final InvoiceService invoiceService;
    private final PricingService pricingService;
    private final TenantJobRunner tenantJobRunner;

    /**
     * Runs at midnight on the 1st of every month.
     * Generates invoices for ALL active guests across ALL active tenants.
     * Payment due on the configured paymentDueDayOfMonth (default: 10th).
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void generateMonthlyInvoices() {
        LocalDate now = LocalDate.now();
        // Bill for previous month
        LocalDate lastMonth = now.minusMonths(1);
        int month = lastMonth.getMonthValue();
        int year = lastMonth.getYear();

        log.info("=== Monthly Billing Cron START — Target Period: {}/{} ===", month, year);

        tenantJobRunner.runForActiveTenants("MonthlyBilling", tenant -> {
            processTenantMonthlyInvoices(month, year);
        });

        log.info("=== Monthly Billing Cron COMPLETE ===");
    }

    /**
     * Processes monthly invoice generation for all active guests in the currently set tenant.
     * Idempotent: existing invoices for (guest, month, year) are skipped without error.
     */
    public void processTenantMonthlyInvoices(int month, int year) {
        List<Guest> allActiveGuests = guestRepository.findByActiveTrue();
        int success = 0, failed = 0;

        for (Guest guest : allActiveGuests) {
            try {
                String guestBuildingId = guest.getBed() != null && guest.getBed().getRoom() != null && guest.getBed().getRoom().getFloor() != null
                        ? guest.getBed().getRoom().getFloor().getBuilding().getId()
                        : null;

                if (guestBuildingId != null && !pricingService.isBillingSchedulerEnabled(guestBuildingId)) {
                    log.info("⏭ Skipping automatic monthly invoice generation for guest {} as billing scheduler is disabled for building {}",
                            guest.getFullName(), guestBuildingId);
                    continue;
                }

                Invoice invoice = invoiceService.generateInvoiceForGuest(guest, month, year);
                log.info("✓ Invoice {} generated for guest {} | Total: ₹{}",
                        invoice.getId(), guest.getFullName(), invoice.getTotalAmount());
                success++;
            } catch (Exception e) {
                log.error("✗ Failed to generate invoice for guest {}: {}", guest.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Tenant billing batch summary — Success: {}, Failed: {}", success, failed);
    }
}
