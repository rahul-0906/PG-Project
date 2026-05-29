package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.PricingConfig;
import com.pgcrm.entity.BuildingConfig;
import com.pgcrm.repository.PricingConfigRepository;
import com.pgcrm.repository.BuildingConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves effective pricing per building.
 * DB overrides (BuildingConfig) take precedence over tenant-config.yml defaults.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingConfigRepository pricingConfigRepository;
    private final BuildingConfigRepository buildingConfigRepository;
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
        SystemConfigProperties.Pricing def = systemConfig.getPricing();
        if (buildingId == null) {
            return new EffectivePricing(
                def.getBreakfast(),
                def.getLunch(),
                def.getDinner(),
                def.getWashingMachine(),
                def.getOmelette(),
                def.getBoiledEgg()
            );
        }

        Optional<BuildingConfig> configOpt = buildingConfigRepository.findById(buildingId);
        if (configOpt.isPresent()) {
            BuildingConfig cfg = configOpt.get();
            return new EffectivePricing(
                cfg.getBreakfastPrice(),
                cfg.getLunchPrice(),
                cfg.getDinnerPrice(),
                cfg.getWashingMachinePrice(),
                cfg.getOmelettePrice(),
                cfg.getBoiledEggPrice()
            );
        }

        return new EffectivePricing(
            def.getBreakfast(),
            def.getLunch(),
            def.getDinner(),
            def.getWashingMachine(),
            def.getOmelette(),
            def.getBoiledEgg()
        );
    }

    /**
     * Returns all pricing entries for a building as a key → value map,
     * merged with YAML defaults for any missing keys.
     */
    public Map<String, BigDecimal> getFullPricingMap(String buildingId) {
        SystemConfigProperties.Pricing def = systemConfig.getPricing();
        Map<String, BigDecimal> result = new HashMap<>();

        Optional<BuildingConfig> configOpt = buildingId != null
                ? buildingConfigRepository.findById(buildingId)
                : Optional.empty();

        if (configOpt.isPresent()) {
            BuildingConfig cfg = configOpt.get();
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
     */
    @Transactional
    public Object upsert(String buildingId, String key, BigDecimal value, String updatedBy) {
        if ("billing_scheduler_enabled".equals(key)) {
            return upsertLegacy(buildingId, key, value, updatedBy);
        }

        BuildingConfig cfg = buildingConfigRepository.findById(buildingId)
                .orElse(BuildingConfig.builder().buildingId(buildingId).build());

        switch (key) {
            case BREAKFAST -> cfg.setBreakfastPrice(value);
            case LUNCH -> cfg.setLunchPrice(value);
            case DINNER -> cfg.setDinnerPrice(value);
            case WASHING_MACHINE -> cfg.setWashingMachinePrice(value);
            case OMELETTE -> cfg.setOmelettePrice(value);
            case BOILED_EGG -> cfg.setBoiledEggPrice(value);
        }
        return buildingConfigRepository.save(cfg);
    }

    @Transactional
    public PricingConfig upsertLegacy(String buildingId, String key, BigDecimal value, String updatedBy) {
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
}
