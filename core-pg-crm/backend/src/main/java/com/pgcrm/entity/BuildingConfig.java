package com.pgcrm.entity;

import com.pgcrm.entity.enums.EbSplitMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Operational configuration record for a single PG {@link Building}.
 *
 * <p>{@code BuildingConfig} stores all per-building behavioural settings that govern
 * meal management, electricity-bill splitting, guest payment modes, and the daily
 * meal opt-in/opt-out cutoff rules. It maintains a strict <strong>one-to-one</strong>
 * relationship with its parent {@link Building}, sharing the same UUID primary key
 * via the {@link MapsId} annotation.</p>
 *
 * <p><strong>Meal Pricing:</strong> Prices for breakfast, lunch, dinner, omelette,
 * boiled egg, and washing machine use are defined here as per-unit amounts. If a
 * per-building override also exists in the {@code pricing_config} table, the
 * {@code PricingService} layer resolves the correct value at runtime.</p>
 *
 * <p><strong>EB Split Method:</strong> The {@link EbSplitMethod} enum determines how
 * the shared electricity bill for a block is divided among its resident guests.</p>
 *
 * <p><strong>Meal Cutoff Times:</strong> {@link #breakfastCutoffTime} and
 * {@link #dinnerCutoffTime} represent the daily deadlines after which guests can
 * no longer modify their meal opt-ins for the following meal period.
 * The {@link #isPreviousDay} flag controls whether the cutoff applies to the
 * <em>previous day</em>'s booking window (the standard PG CRM arrears model).</p>
 *
 * @see Building
 * @see EbSplitMethod
 */
@Entity
@Table(name = "building_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildingConfig {

    /**
     * Primary key — identical to the parent {@link Building}'s UUID.
     * The {@link MapsId} annotation on {@link #building} ensures this column is
     * populated automatically from the shared association.
     */
    @Id
    @Column(name = "building_id")
    private String buildingId;

    /**
     * The parent {@link Building} that owns this configuration.
     * The {@code @JsonIgnore} annotation prevents circular serialisation when the
     * building is embedded in API responses that include its config.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "building_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Building building;

    // ── Meal Plan Settings ─────────────────────────────────────────────────────

    /**
     * Whether the base room rent already includes three meals a day.
     * When {@code true}, the billing pipeline skips food line-item generation for guests.
     * When {@code false}, meals are billed individually based on daily log records.
     * Defaults to {@code false} (à la carte model).
     */
    @Column(name = "food_included_in_rent", nullable = false)
    @Builder.Default
    private boolean foodIncludedInRent = false;

    /**
     * Whether guests are permitted to cancel individual meal slots after opting in.
     * When {@code false}, opted-in meals are locked for billing regardless of attendance.
     * Defaults to {@code true}.
     */
    @Column(name = "allow_meal_cancellations", nullable = false)
    @Builder.Default
    private boolean allowMealCancellations = true;

    // ── Per-Unit Meal Prices ───────────────────────────────────────────────────

    /** Per-meal price charged for breakfast, in Indian Rupees (₹). Defaults to ₹0. */
    @Column(name = "breakfast_price", nullable = false)
    @Builder.Default
    private BigDecimal breakfastPrice = BigDecimal.ZERO;

    /** Per-meal price charged for lunch, in Indian Rupees (₹). Defaults to ₹0. */
    @Column(name = "lunch_price", nullable = false)
    @Builder.Default
    private BigDecimal lunchPrice = BigDecimal.ZERO;

    /** Per-meal price charged for dinner, in Indian Rupees (₹). Defaults to ₹0. */
    @Column(name = "dinner_price", nullable = false)
    @Builder.Default
    private BigDecimal dinnerPrice = BigDecimal.ZERO;

    // ── Add-On / Service Prices ────────────────────────────────────────────────

    /** Per-item price charged for an omelette add-on, in Indian Rupees (₹). Defaults to ₹0. */
    @Column(name = "omelette_price", nullable = false)
    @Builder.Default
    private BigDecimal omelettePrice = BigDecimal.ZERO;

    @Column(name = "omelette_label", nullable = false)
    @Builder.Default
    private String omeletteLabel = "Omelette";

    /** Per-item price charged for a boiled egg add-on, in Indian Rupees (₹). Defaults to ₹0. */
    @Column(name = "boiled_egg_price", nullable = false)
    @Builder.Default
    private BigDecimal boiledEggPrice = BigDecimal.ZERO;

    @Column(name = "boiled_egg_label", nullable = false)
    @Builder.Default
    private String boiledEggLabel = "Boiled Egg";

    /** Per-use price charged for washing machine usage, in Indian Rupees (₹). Defaults to ₹0. */
    @Column(name = "washing_machine_price", nullable = false)
    @Builder.Default
    private BigDecimal washingMachinePrice = BigDecimal.ZERO;

    @Column(name = "washing_machine_label", nullable = false)
    @Builder.Default
    private String washingMachineLabel = "Washing Machine";

    // ── Electricity Bill Settings ──────────────────────────────────────────────

    /**
     * The algorithm used to divide the shared electricity bill across resident guests.
     * May be {@code null} if no EB billing has been configured for this building.
     *
     * @see EbSplitMethod
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "eb_split_method")
    private EbSplitMethod ebSplitMethod;

    // ── Meal Cutoff Window ─────────────────────────────────────────────────────

    /**
     * The daily time after which the next-day breakfast opt-in window closes.
     * In the arrears billing model, this is the cutoff on the <em>previous evening</em>
     * for tomorrow's breakfast. Defaults to {@code 22:00} (10 PM).
     */
    @Column(name = "breakfast_cutoff_time", nullable = false)
    @Builder.Default
    private LocalTime breakfastCutoffTime = LocalTime.of(22, 0);

    /**
     * The daily time after which the same-day dinner opt-in window closes.
     * Defaults to {@code 14:00} (2 PM), giving the kitchen adequate preparation time.
     */
    @Column(name = "dinner_cutoff_time", nullable = false)
    @Builder.Default
    private LocalTime dinnerCutoffTime = LocalTime.of(14, 0);

    /**
     * Controls whether meal opt-ins are recorded for the <em>previous day</em>
     * (the standard PG CRM arrears model) or for the current day.
     * When {@code true}, a log saved on Day N applies to Day N-1 for billing purposes.
     * Defaults to {@code true}.
     */
    @Column(name = "is_previous_day", nullable = false)
    @Builder.Default
    private boolean isPreviousDay = true;

    // ── Payment Settings ───────────────────────────────────────────────────────

    /**
     * Comma-separated list of accepted payment modes for invoice settlement.
     * Accepted values: {@code "ONLINE"} (Razorpay), {@code "CASH"}, or {@code "BOTH"}.
     * Defaults to {@code "BOTH"} to support all payment channels at launch.
     */
    @Column(name = "allowed_payment_modes", nullable = false)
    @Builder.Default
    private String allowedPaymentModes = "BOTH";

    /**
     * Whether the building config allows Omelette add-on.
     * Defaults to {@code true}.
     */
    @Column(name = "offer_omelette", nullable = false)
    @com.fasterxml.jackson.annotation.JsonProperty("offerOmelette")
    @Builder.Default
    private boolean offerOmelette = true;

    /**
     * Whether the building config allows Boiled Egg add-on.
     * Defaults to {@code true}.
     */
    @Column(name = "offer_boiled_egg", nullable = false)
    @com.fasterxml.jackson.annotation.JsonProperty("offerBoiledEgg")
    @Builder.Default
    private boolean offerBoiledEgg = true;
}
