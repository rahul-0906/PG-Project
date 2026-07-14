package com.pgcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbound response DTO exposing a safe subset of application-level system configuration
 * to authenticated frontend clients.
 *
 * <p>Returned by {@code GET /api/config/system} and consumed by the React frontend
 * immediately after login to configure UI behaviour — for example, to show or hide
 * meal-management sections based on {@link RulesDto#isBreakfastEnabled()}, or to
 * display the building name from {@link BrandingDto#getName()}.</p>
 *
 * <p><strong>Security:</strong> This DTO exposes only safe, non-sensitive configuration
 * values (branding text and operational rules). Secret keys, credentials, database
 * URLs, and third-party API tokens are never included in this response.</p>
 *
 * <p><strong>Source:</strong> Populated via the {@link #fromProperties(com.pgcrm.config.SystemConfigProperties)}
 * factory method, which maps values from the {@link com.pgcrm.config.SystemConfigProperties}
 * {@code @ConfigurationProperties} bean loaded from {@code application.yml}.</p>
 *
 * <p><strong>Structure:</strong> Split into two nested sections for clear client-side
 * consumption:</p>
 * <ul>
 *   <li>{@link BrandingDto} — Display name and short title for the portal header and
 *       email templates.</li>
 *   <li>{@link RulesDto} — Operational feature flags and business rules that drive
 *       UI conditional rendering and workflow logic.</li>
 * </ul>
 *
 * @see com.pgcrm.config.SystemConfigProperties
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigResponse {

    /**
     * Branding information for the PG accommodation, used in the portal header,
     * page titles, and outgoing email templates.
     */
    private BrandingDto branding;

    /**
     * Operational rules and feature flags that govern the behaviour of the
     * PG CRM workflows (meal management, billing, notifications, etc.).
     */
    private RulesDto rules;

    // ── Nested DTOs ───────────────────────────────────────────────────────────

    /**
     * Branding configuration values safe to expose to authenticated clients.
     *
     * <p>Sourced from the {@code tenant.branding.*} namespace in {@code application.yml}.
     * Values are display-only and carry no security sensitivity.</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandingDto {

        /**
         * Full display name of the PG accommodation.
         * Shown in the portal header, the browser tab title, and all outgoing
         * email communications.
         * Example: {@code "Sunrise PG Residency"}.
         */
        private String name;

        /**
         * Abbreviated or short-form title used in compact UI elements
         * such as mobile headers, tab labels, and WhatsApp message prefixes.
         * Example: {@code "Sunrise PG"}.
         */
        private String shortTitle;

        /**
         * Dynamic primary branding color.
         */
        private String primaryColor;
    }

    /**
     * Operational business rules and feature-flag configuration values.
     *
     * <p>Sourced from the {@code tenant.rules.*} namespace in {@code application.yml}.
     * The frontend uses these values to conditionally render UI sections
     * (e.g., showing the laundry column only when {@link #hasWashingMachine} is {@code true})
     * and to drive client-side validation logic.</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RulesDto {

        /**
         * Whether the base room rent already includes all three meals.
         * When {@code true}, the frontend hides per-meal pricing inputs and
         * the billing pipeline skips food line-item generation.
         */
        private boolean foodIncludedInRent;

        /**
         * Whether guests are permitted to cancel individual meal slots after opting in.
         * When {@code false}, opted-in meals are locked for billing and cancellation
         * controls are hidden in the guest portal.
         */
        private boolean allowMealCancellations;

        /**
         * The electricity-bill split algorithm configured for this building.
         * Serialised as the enum name string: {@code "EQUAL_SPLIT"}, {@code "PER_BED"},
         * {@code "METER_BASED"}, or {@code "MANAGER_MANUAL"}.
         * Displayed in the EB bill recording form to inform the manager of the active method.
         *
         * @see com.pgcrm.entity.enums.EbSplitMethod
         */
        private String ebSplitMethod;

        /**
         * Whether the building has a washing machine available for guest use.
         * When {@code false}, the laundry section is hidden in the daily log form
         * and the laundry line type is excluded from invoice generation.
         */
        private boolean hasWashingMachine;

        /**
         * The day of the month (1–28) by which invoice payments are due.
         * Used by the frontend to display the due date in invoice cards and
         * by the backend scheduler to determine overdue transitions.
         * Example: {@code 10} = payments are due by the 10th of each month.
         */
        private int paymentDueDayOfMonth;

        /**
         * Number of days constituting the formal checkout notice period.
         * Added to {@code noticeDate} to compute {@code exitDate} on the guest entity.
         * Displayed in the checkout-notice form to inform the manager of the policy.
         * Example: {@code 15} = 15-day notice required.
         */
        private int noticePeriodDays;

        /**
         * WhatsApp message template string used for invoice payment reminders.
         * May contain placeholder tokens (e.g., {@code {{guestName}}}, {@code {{amount}}})
         * that are substituted by the notification service at dispatch time.
         */
        private String invoiceWhatsappTemplate;

        /**
         * Default WhatsApp message template for generic broadcast notifications
         * not covered by a specific template.
         */
        private String defaultWhatsAppTemplate;

        /**
         * Default email body template for generic notifications.
         * May contain HTML or plain-text with placeholder tokens.
         */
        private String defaultEmailTemplate;

        /**
         * Whether breakfast is an active, billable meal option for this building.
         * When {@code false}, breakfast opt-in controls are hidden in the daily log form.
         */
        private boolean breakfastEnabled;

        /**
         * Whether lunch is an active, billable meal option for this building.
         * When {@code false}, lunch opt-in controls are hidden in the daily log form.
         */
        private boolean lunchEnabled;

        /**
         * Whether dinner is an active, billable meal option for this building.
         * When {@code false}, dinner opt-in controls are hidden in the daily log form.
         */
        private boolean dinnerEnabled;

        /**
         * The breakfast opt-in cutoff time, serialised as a string (e.g., {@code "22:00"}).
         * After this time, guests cannot modify their next-day breakfast opt-in.
         * Derived from {@link com.pgcrm.config.SystemConfigProperties} by calling
         * {@code LocalTime.toString()} at mapping time.
         */
        private String breakfastLockoutTime;

        /**
         * The dinner opt-in cutoff time, serialised as a string (e.g., {@code "14:00"}).
         * After this time, guests cannot modify their same-day dinner opt-in.
         * Derived from {@link com.pgcrm.config.SystemConfigProperties} by calling
         * {@code LocalTime.toString()} at mapping time.
         */
        private String dinnerLockoutTime;

        private String omeletteLabel;
        private String boiledEggLabel;
        private String washingMachineLabel;
    }

    // ── Factory Method ────────────────────────────────────────────────────────

    /**
     * Null-safe static factory method that maps a
     * {@link com.pgcrm.config.SystemConfigProperties} bean to a
     * {@code SystemConfigResponse} DTO safe for API exposure.
     *
     * <p>The {@link RulesDto#getBreakfastLockoutTime()} and
     * {@link RulesDto#getDinnerLockoutTime()} fields are serialised from
     * {@code java.time.LocalTime} to their string representation (ISO-8601 time,
     * e.g., {@code "22:00"}). A {@code null} guard is applied before calling
     * {@code toString()} to handle cases where the config property is unset.</p>
     *
     * @param properties the {@link com.pgcrm.config.SystemConfigProperties} bean
     *                   to map; returns {@code null} if the input is {@code null}.
     * @return a fully populated {@code SystemConfigResponse}, or {@code null} if input is {@code null}.
     */
    public static SystemConfigResponse fromProperties(
            final com.pgcrm.config.SystemConfigProperties properties) {

        if (properties == null) {
            return null;
        }

        return SystemConfigResponse.builder()
                .branding(BrandingDto.builder()
                        .name(properties.getBranding().getName())
                        .shortTitle(properties.getBranding().getShortTitle())
                        .primaryColor(properties.getBranding().getPrimaryColor())
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
                        .breakfastLockoutTime(
                                properties.getRules().getBreakfastLockoutTime() != null
                                        ? properties.getRules().getBreakfastLockoutTime().toString()
                                        : null)
                        .dinnerLockoutTime(
                                properties.getRules().getDinnerLockoutTime() != null
                                        ? properties.getRules().getDinnerLockoutTime().toString()
                                        : null)
                        .omeletteLabel("Omelette")
                        .boiledEggLabel("Boiled Egg")
                        .washingMachineLabel("Washing Machine")
                        .build())
                .build();
    }
}
