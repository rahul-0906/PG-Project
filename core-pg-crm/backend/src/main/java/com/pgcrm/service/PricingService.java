package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.BuildingConfig;
import com.pgcrm.entity.PricingConfig;
import com.pgcrm.repository.BuildingConfigRepository;
import com.pgcrm.repository.PricingConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service responsible for resolving effective pricing for meals, add-ons, and laundry
 * across buildings, with a two-tier override system.
 *
 * <p><strong>Price Resolution Precedence:</strong></p>
 * <ol>
 *   <li><strong>Per-Building {@link BuildingConfig}:</strong> If a {@link BuildingConfig} row
 *       exists for the building, its price fields take absolute precedence.</li>
 *   <li><strong>Global YAML Defaults:</strong> If no building config exists (e.g., for a
 *       building that was created without explicit price configuration), the values from
 *       {@link SystemConfigProperties#getPricing()} ({@code tenant-config.yml}) are used.</li>
 * </ol>
 *
 * <p><strong>Supported Price Keys:</strong></p>
 * <ul>
 *   <li>{@link #BREAKFAST} — {@code "breakfast"}</li>
 *   <li>{@link #LUNCH} — {@code "lunch"}</li>
 *   <li>{@link #DINNER} — {@code "dinner"}</li>
 *   <li>{@link #WASHING_MACHINE} — {@code "washing_machine"}</li>
 *   <li>{@link #OMELETTE} — {@code "omelette"}</li>
 *   <li>{@link #BOILED_EGG} — {@code "boiled_egg"}</li>
 * </ul>
 *
 * <p><strong>Legacy Key Support:</strong> The key {@code "billing_scheduler_enabled"} is
 * a special-purpose override stored in the {@link PricingConfig} table (not in
 * {@link BuildingConfig}) and routed through {@link #upsertLegacy} for backward compatibility.</p>
 *
 * @see EffectivePricing
 * @see BuildingConfigRepository
 * @see PricingConfigRepository
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingConfigRepository  pricingConfigRepository;
    private final BuildingConfigRepository buildingConfigRepository;
    private final SystemConfigProperties   systemConfig;

    // ── Price Key Constants ───────────────────────────────────────────────────

    /** Price key for breakfast meal. */
    public static final String BREAKFAST       = "breakfast";

    /** Price key for lunch meal. */
    public static final String LUNCH           = "lunch";

    /** Price key for dinner meal. */
    public static final String DINNER          = "dinner";

    /** Price key for washing machine usage. */
    public static final String WASHING_MACHINE = "washing_machine";

    /** Price key for omelette add-on. */
    public static final String OMELETTE        = "omelette";

    /** Price key for boiled egg add-on. */
    public static final String BOILED_EGG      = "boiled_egg";

    /**
     * Resolves the effective pricing bundle for the given building.
     *
     * <p>If {@code buildingId} is {@code null}, the YAML global defaults are returned
     * directly without a database query. Otherwise, the building config is looked up
     * and its prices are used. If no building config row exists, the YAML defaults are used.</p>
     *
     * @param buildingId the UUID of the building; may be {@code null} to force use of YAML defaults.
     * @return an {@link EffectivePricing} record containing the resolved prices for all six line items.
     */
    public EffectivePricing getEffectivePricing(final String buildingId) {
        final SystemConfigProperties.Pricing def = systemConfig.getPricing();
        if (buildingId == null) {
            return new EffectivePricing(
                    def.getBreakfast(), def.getLunch(), def.getDinner(),
                    def.getWashingMachine(), def.getOmelette(), def.getBoiledEgg());
        }

        final Optional<BuildingConfig> configOpt = buildingConfigRepository.findById(buildingId);
        if (configOpt.isPresent()) {
            final BuildingConfig cfg = configOpt.get();
            return new EffectivePricing(
                    cfg.getBreakfastPrice(), cfg.getLunchPrice(), cfg.getDinnerPrice(),
                    cfg.getWashingMachinePrice(), cfg.getOmelettePrice(), cfg.getBoiledEggPrice());
        }

        // No building config found — fall back to YAML defaults.
        return new EffectivePricing(
                def.getBreakfast(), def.getLunch(), def.getDinner(),
                def.getWashingMachine(), def.getOmelette(), def.getBoiledEgg());
    }

    /**
     * Returns a complete key-to-value pricing map for a building, merging building config
     * values with YAML defaults for any missing keys.
     *
     * <p>Used by the building configuration UI to display all current prices in a single call.</p>
     *
     * @param buildingId the UUID of the building; may be {@code null} to return all YAML defaults.
     * @return a {@link Map} of price key → {@link BigDecimal} value for all six supported price keys.
     */
    public Map<String, BigDecimal> getFullPricingMap(final String buildingId) {
        final SystemConfigProperties.Pricing def = systemConfig.getPricing();
        final Map<String, BigDecimal> result = new HashMap<>();

        final Optional<BuildingConfig> configOpt = buildingId != null
                ? buildingConfigRepository.findById(buildingId)
                : Optional.empty();

        if (configOpt.isPresent()) {
            final BuildingConfig cfg = configOpt.get();
            result.put(BREAKFAST,       cfg.getBreakfastPrice());
            result.put(LUNCH,           cfg.getLunchPrice());
            result.put(DINNER,          cfg.getDinnerPrice());
            result.put(WASHING_MACHINE, cfg.getWashingMachinePrice());
            result.put(OMELETTE,        cfg.getOmelettePrice());
            result.put(BOILED_EGG,      cfg.getBoiledEggPrice());
        } else {
            result.put(BREAKFAST,       def.getBreakfast());
            result.put(LUNCH,           def.getLunch());
            result.put(DINNER,          def.getDinner());
            result.put(WASHING_MACHINE, def.getWashingMachine());
            result.put(OMELETTE,        def.getOmelette());
            result.put(BOILED_EGG,      def.getBoiledEgg());
        }
        return result;
    }

    /**
     * Upserts a single pricing override for a building.
     *
     * <p>For standard price keys ({@link #BREAKFAST}, {@link #LUNCH}, etc.), the corresponding
     * field on the building's {@link BuildingConfig} row is updated (creating the row if it does
     * not yet exist). For the legacy key {@code "billing_scheduler_enabled"}, the request is
     * routed to {@link #upsertLegacy(String, String, BigDecimal, String)} which stores the
     * value in the {@link PricingConfig} table.</p>
     *
     * @param buildingId the UUID of the building to update pricing for.
     * @param key        the price key to update (one of the {@code public static final} constants
     *                   or {@code "billing_scheduler_enabled"}).
     * @param value      the new price value.
     * @param updatedBy  the identity of the actor making the change (for audit purposes).
     * @return the saved entity: a {@link BuildingConfig} for standard keys,
     *         or a {@link PricingConfig} for the legacy key.
     */
    @Transactional
    public Object upsert(final String buildingId, final String key,
                         final BigDecimal value, final String updatedBy) {
        if ("billing_scheduler_enabled".equals(key)) {
            return upsertLegacy(buildingId, key, value, updatedBy);
        }

        BuildingConfig cfg = buildingConfigRepository.findById(buildingId)
                .orElse(BuildingConfig.builder().buildingId(buildingId).build());

        switch (key) {
            case BREAKFAST       -> cfg.setBreakfastPrice(value);
            case LUNCH           -> cfg.setLunchPrice(value);
            case DINNER          -> cfg.setDinnerPrice(value);
            case WASHING_MACHINE -> cfg.setWashingMachinePrice(value);
            case OMELETTE        -> cfg.setOmelettePrice(value);
            case BOILED_EGG      -> cfg.setBoiledEggPrice(value);
        }
        return buildingConfigRepository.save(cfg);
    }

    /**
     * Upserts a legacy pricing entry in the {@link PricingConfig} table.
     *
     * <p>Used only for the special {@code "billing_scheduler_enabled"} key, which predates
     * the migration of price fields into {@link BuildingConfig} and is retained for
     * backward compatibility with existing data.</p>
     *
     * @param buildingId the UUID of the building.
     * @param key        the legacy price key (e.g., {@code "billing_scheduler_enabled"}).
     * @param value      the new value ({@code 1} = enabled, {@code 0} = disabled for boolean flags).
     * @param updatedBy  the identity of the actor making the change.
     * @return the saved {@link PricingConfig} entity.
     */
    @Transactional
    public PricingConfig upsertLegacy(final String buildingId, final String key,
                                      final BigDecimal value, final String updatedBy) {
        final PricingConfig cfg = pricingConfigRepository
                .findByBuildingIdAndPriceKey(buildingId, key)
                .orElse(PricingConfig.builder().buildingId(buildingId).priceKey(key).build());
        cfg.setValue(value);
        cfg.setUpdatedBy(updatedBy);
        return pricingConfigRepository.save(cfg);
    }

    /**
     * Checks whether the billing scheduler is enabled for the given building.
     *
     * <p>The scheduler flag is stored as a numeric value in the {@link PricingConfig} table
     * under the key {@code "billing_scheduler_enabled"}. A value {@code > 0} is interpreted
     * as {@code true}; absent or zero is interpreted as {@code false}.</p>
     *
     * @param buildingId the UUID of the building to check; returns {@code false} if {@code null}.
     * @return {@code true} if the billing scheduler is enabled for the building.
     */
    public boolean isBillingSchedulerEnabled(final String buildingId) {
        if (buildingId == null) return false;
        return pricingConfigRepository.findByBuildingIdAndPriceKey(buildingId, "billing_scheduler_enabled")
                .map(c -> c.getValue().compareTo(BigDecimal.ZERO) > 0)
                .orElse(false);
    }

    // ── Nested Record ─────────────────────────────────────────────────────────

    /**
     * Immutable value object carrying the fully resolved effective prices for all six
     * billable line items in the system.
     *
     * <p>Returned by {@link #getEffectivePricing(String)} and consumed by
     * {@code InvoiceService} and {@code SettlementService} during billing calculations.</p>
     *
     * @param breakfast     the per-meal breakfast price.
     * @param lunch         the per-meal lunch price.
     * @param dinner        the per-meal dinner price.
     * @param washingMachine the per-use washing machine price.
     * @param omelette      the per-unit omelette add-on price.
     * @param boiledEgg     the per-unit boiled egg add-on price.
     */
    public record EffectivePricing(
            BigDecimal breakfast,
            BigDecimal lunch,
            BigDecimal dinner,
            BigDecimal washingMachine,
            BigDecimal omelette,
            BigDecimal boiledEgg
    ) {}
}
