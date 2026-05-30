package com.pgcrm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "pg.system")
public class SystemConfigProperties {

    private Branding branding = new Branding();
    private Rules rules = new Rules();
    private Pricing pricing = new Pricing();
    private Map<String, BigDecimal> rentConfig;

    @Data
    public static class Branding {
        private String name = "PG CRM";
        private String shortTitle = "PG";
    }

    @Data
    public static class Rules {
        private boolean foodIncludedInRent = false;
        private boolean allowMealCancellations = true;
        private String ebSplitMethod = "EQUAL_SPLIT";
        private boolean hasWashingMachine = true;
        private int paymentDueDayOfMonth = 5;
        private int noticePeriodDays = 30;
        private String invoiceWhatsappTemplate = "Hi {guestName}, your invoice for {month} is ready. Rent: {rent}, EB: {eb}, Food: {food}, Laundry: {wm}. Total: {total}. Due by {dueDate}.";
        private String defaultWhatsAppTemplate = "Hi {guestName}, this is a notification from PG CRM.";
        private String defaultEmailTemplate = "Hi {guestName},\n\nThis is a notification from PG CRM.";
        private boolean breakfastEnabled = true;
        private boolean lunchEnabled = true;
        private boolean dinnerEnabled = true;
        private java.time.LocalTime breakfastLockoutTime = java.time.LocalTime.of(22, 0);
        private java.time.LocalTime dinnerLockoutTime = java.time.LocalTime.of(17, 0);
    }

    @Data
    public static class Pricing {
        private BigDecimal breakfast = new BigDecimal("60.00");
        private BigDecimal lunch = new BigDecimal("65.00");
        private BigDecimal dinner = new BigDecimal("60.00");
        private BigDecimal washingMachine = new BigDecimal("50.00");
        private BigDecimal omelette = new BigDecimal("18.00");
        private BigDecimal boiledEgg = new BigDecimal("18.00");
    }
}
