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
        private String name;
        private String shortTitle;
    }

    @Data
    public static class Rules {
        private boolean foodIncludedInRent;
        private boolean allowMealCancellations;
        private String ebSplitMethod;
        private boolean hasWashingMachine;
        private int paymentDueDayOfMonth;
        private int noticePeriodDays;
        private String invoiceWhatsappTemplate;
        private String defaultWhatsAppTemplate;
        private String defaultEmailTemplate;
        private boolean breakfastEnabled;
        private boolean lunchEnabled;
        private boolean dinnerEnabled;
        private java.time.LocalTime breakfastLockoutTime;
        private java.time.LocalTime dinnerLockoutTime;
    }

    @Data
    public static class Pricing {
        private BigDecimal breakfast;
        private BigDecimal lunch;
        private BigDecimal dinner;
        private BigDecimal washingMachine;
        private BigDecimal omelette;
        private BigDecimal boiledEgg;
    }
}
