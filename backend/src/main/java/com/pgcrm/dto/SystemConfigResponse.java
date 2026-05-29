package com.pgcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigResponse {
    private BrandingDto branding;
    private RulesDto rules;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandingDto {
        private String name;
        private String shortTitle;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RulesDto {
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
        private String breakfastLockoutTime;
        private String dinnerLockoutTime;
    }

    public static SystemConfigResponse fromProperties(com.pgcrm.config.SystemConfigProperties properties) {
        if (properties == null) return null;
        return SystemConfigResponse.builder()
                .branding(BrandingDto.builder()
                        .name(properties.getBranding().getName())
                        .shortTitle(properties.getBranding().getShortTitle())
                        .build())
                .rules(RulesDto.builder()
                        .foodIncludedInRent(properties.getRules().isFoodIncludedInRent())
                        .allowMealCancellations(properties.getRules().isAllowMealCancellations())
                        .ebSplitMethod(properties.getRules().getEbSplitMethod())
                        .hasWashingMachine(properties.getRules().isHasWashingMachine())
                        .paymentDueDayOfMonth(properties.getRules().getPaymentDueDayOfMonth())
                        .noticePeriodDays(properties.getRules().getNoticePeriodDays())
                        .invoiceWhatsappTemplate(properties.getRules().getInvoiceWhatsappTemplate())
                        .defaultWhatsAppTemplate(properties.getRules().getDefaultWhatsAppTemplate())
                        .defaultEmailTemplate(properties.getRules().getDefaultEmailTemplate())
                        .breakfastEnabled(properties.getRules().isBreakfastEnabled())
                        .lunchEnabled(properties.getRules().isLunchEnabled())
                        .dinnerEnabled(properties.getRules().isDinnerEnabled())
                        .breakfastLockoutTime(properties.getRules().getBreakfastLockoutTime() != null ? properties.getRules().getBreakfastLockoutTime().toString() : null)
                        .dinnerLockoutTime(properties.getRules().getDinnerLockoutTime() != null ? properties.getRules().getDinnerLockoutTime().toString() : null)
                        .build())
                .build();
    }
}
