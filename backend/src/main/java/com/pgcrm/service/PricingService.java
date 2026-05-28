package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.PricingConfig;
import com.pgcrm.repository.PricingConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves effective pricing per building.
 * DB overrides take precedence over tenant-config.yml defaults.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingConfigRepository pricingConfigRepository;
    private final SystemConfigProperties systemConfig;

    /** Price keys used in the system */
    public static final String BREAKFAST      = "breakfast";
    public static final String LUNCH          = "lunch";
    public static final String DINNER         = "dinner";
    public static final String WASHING_MACHINE = "washing_machine";
    public static final String OMELETTE       = "omelette";
    public static final String BOILED_EGG     = "boiled_egg";

    /**
     * Returns the effective pricing for a building.
     * DB override wins; falls back to YAML defaults.
     */
    public EffectivePricing getEffectivePricing(String buildingId) {
        Map<String, BigDecimal> overrides = buildingId != null
                ? toMap(pricingConfigRepository.findByBuildingId(buildingId))
                : Map.of();

        SystemConfigProperties.Pricing def = systemConfig.getPricing();
        return new EffectivePricing(
            overrides.getOrDefault(BREAKFAST,       def.getBreakfast()),
            overrides.getOrDefault(LUNCH,           def.getLunch()),
            overrides.getOrDefault(DINNER,          def.getDinner()),
            overrides.getOrDefault(WASHING_MACHINE, def.getWashingMachine()),
            overrides.getOrDefault(OMELETTE,        def.getOmelette()),
            overrides.getOrDefault(BOILED_EGG,      def.getBoiledEgg())
        );
    }

    /**
     * Returns all pricing entries for a building as a key → value map,
     * merged with YAML defaults for any missing keys.
     */
    public Map<String, BigDecimal> getFullPricingMap(String buildingId) {
        Map<String, BigDecimal> overrides = buildingId != null
                ? toMap(pricingConfigRepository.findByBuildingId(buildingId))
                : new HashMap<>();

        SystemConfigProperties.Pricing def = systemConfig.getPricing();
        Map<String, BigDecimal> result = new HashMap<>();
        result.put(BREAKFAST,       overrides.getOrDefault(BREAKFAST,       def.getBreakfast()));
        result.put(LUNCH,           overrides.getOrDefault(LUNCH,           def.getLunch()));
        result.put(DINNER,          overrides.getOrDefault(DINNER,          def.getDinner()));
        result.put(WASHING_MACHINE, overrides.getOrDefault(WASHING_MACHINE, def.getWashingMachine()));
        result.put(OMELETTE,        overrides.getOrDefault(OMELETTE,        def.getOmelette()));
        result.put(BOILED_EGG,      overrides.getOrDefault(BOILED_EGG,      def.getBoiledEgg()));
        return result;
    }

    /**
     * Upserts a single pricing override for a building.
     */
    @Transactional
    public PricingConfig upsert(String buildingId, String key, BigDecimal value, String updatedBy) {
        Optional<PricingConfig> existing = pricingConfigRepository.findByBuildingIdAndPriceKey(buildingId, key);
        PricingConfig cfg = existing.orElse(PricingConfig.builder()
                .buildingId(buildingId)
                .priceKey(key)
                .build());
        cfg.setValue(value);
        cfg.setUpdatedBy(updatedBy);
        return pricingConfigRepository.save(cfg);
    }

    /** Pricing resolved for a specific building */
    public record EffectivePricing(
        BigDecimal breakfast,
        BigDecimal lunch,
        BigDecimal dinner,
        BigDecimal washingMachine,
        BigDecimal omelette,
        BigDecimal boiledEgg
    ) {}

    public boolean isBillingSchedulerEnabled(String buildingId) {
        if (buildingId == null) return false;
        return pricingConfigRepository.findByBuildingIdAndPriceKey(buildingId, "billing_scheduler_enabled")
                .map(c -> c.getValue().compareTo(BigDecimal.ZERO) > 0)
                .orElse(false); // Default to false (disabled)
    }

    private Map<String, BigDecimal> toMap(List<PricingConfig> list) {
        Map<String, BigDecimal> m = new HashMap<>();
        list.forEach(c -> m.put(c.getPriceKey(), c.getValue()));
        return m;
    }
}
