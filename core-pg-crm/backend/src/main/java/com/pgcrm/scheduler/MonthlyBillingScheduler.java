package com.pgcrm.scheduler;

import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Invoice;
import com.pgcrm.repository.GuestRepository;
import com.pgcrm.service.InvoiceService;
import com.pgcrm.service.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlyBillingScheduler {

    private final GuestRepository guestRepository;
    private final InvoiceService invoiceService;
    private final PricingService pricingService;

    /**
     * Runs at midnight on the 1st of every month.
     * Generates invoices for ALL active guests across ALL active tenants.
     * Payment due on the configured paymentDueDayOfMonth (default: 10th).
     */
    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void generateMonthlyInvoices() {
        LocalDate now = LocalDate.now();
        // Bill for previous month
        LocalDate lastMonth = now.minusMonths(1);
        int month = lastMonth.getMonthValue();
        int year = lastMonth.getYear();

        log.info("=== Monthly Billing Cron START — Generating invoices for {}/{} ===", month, year);

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

        log.info("=== Monthly Billing Cron DONE — Success: {}, Failed: {} ===", success, failed);
    }
}
