package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.PricingConfig;
import com.pgcrm.repository.PricingConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PricingServiceTest {

    @Mock
    private PricingConfigRepository pricingConfigRepository;

    @Mock
    private SystemConfigProperties systemConfig;

    @InjectMocks
    private PricingService pricingService;

    private SystemConfigProperties.Pricing pricingDefaults;

    @BeforeEach
    void setUp() {
        pricingDefaults = new SystemConfigProperties.Pricing();
        pricingDefaults.setBreakfast(BigDecimal.valueOf(60.00));
        pricingDefaults.setLunch(BigDecimal.valueOf(65.00));
        pricingDefaults.setDinner(BigDecimal.valueOf(60.00));
        pricingDefaults.setWashingMachine(BigDecimal.valueOf(50.00));
        pricingDefaults.setOmelette(BigDecimal.valueOf(18.00));
        pricingDefaults.setBoiledEgg(BigDecimal.valueOf(18.00));
    }

    @Test
    void testGetEffectivePricing_FallbackToDefaults() {
        String buildingId = "b1";
        when(systemConfig.getPricing()).thenReturn(pricingDefaults);
        when(pricingConfigRepository.findByBuildingId(buildingId)).thenReturn(List.of());

        PricingService.EffectivePricing effective = pricingService.getEffectivePricing(buildingId);

        assertEquals(BigDecimal.valueOf(60.00), effective.breakfast());
        assertEquals(BigDecimal.valueOf(65.00), effective.lunch());
        assertEquals(BigDecimal.valueOf(60.00), effective.dinner());
        assertEquals(BigDecimal.valueOf(50.00), effective.washingMachine());
        assertEquals(BigDecimal.valueOf(18.00), effective.omelette());
        assertEquals(BigDecimal.valueOf(18.00), effective.boiledEgg());
    }

    @Test
    void testGetEffectivePricing_WithOverrides() {
        String buildingId = "b1";
        PricingConfig overrideBreakfast = PricingConfig.builder()
                .buildingId(buildingId)
                .priceKey(PricingService.BREAKFAST)
                .value(BigDecimal.valueOf(70.00))
                .build();
        PricingConfig overrideOmelette = PricingConfig.builder()
                .buildingId(buildingId)
                .priceKey(PricingService.OMELETTE)
                .value(BigDecimal.valueOf(25.00))
                .build();

        when(systemConfig.getPricing()).thenReturn(pricingDefaults);
        when(pricingConfigRepository.findByBuildingId(buildingId)).thenReturn(List.of(overrideBreakfast, overrideOmelette));

        PricingService.EffectivePricing effective = pricingService.getEffectivePricing(buildingId);

        assertEquals(BigDecimal.valueOf(70.00), effective.breakfast()); // Overridden
        assertEquals(BigDecimal.valueOf(65.00), effective.lunch());    // Default
        assertEquals(BigDecimal.valueOf(60.00), effective.dinner());   // Default
        assertEquals(BigDecimal.valueOf(50.00), effective.washingMachine()); // Default
        assertEquals(BigDecimal.valueOf(25.00), effective.omelette());  // Overridden
        assertEquals(BigDecimal.valueOf(18.00), effective.boiledEgg()); // Default
    }

    @Test
    void testGetFullPricingMap() {
        String buildingId = "b1";
        PricingConfig overrideLunch = PricingConfig.builder()
                .buildingId(buildingId)
                .priceKey(PricingService.LUNCH)
                .value(BigDecimal.valueOf(80.00))
                .build();

        when(systemConfig.getPricing()).thenReturn(pricingDefaults);
        when(pricingConfigRepository.findByBuildingId(buildingId)).thenReturn(List.of(overrideLunch));

        Map<String, BigDecimal> fullMap = pricingService.getFullPricingMap(buildingId);

        assertEquals(BigDecimal.valueOf(60.00), fullMap.get(PricingService.BREAKFAST));
        assertEquals(BigDecimal.valueOf(80.00), fullMap.get(PricingService.LUNCH)); // Overridden
        assertEquals(BigDecimal.valueOf(60.00), fullMap.get(PricingService.DINNER));
        assertEquals(BigDecimal.valueOf(50.00), fullMap.get(PricingService.WASHING_MACHINE));
        assertEquals(BigDecimal.valueOf(18.00), fullMap.get(PricingService.OMELETTE));
        assertEquals(BigDecimal.valueOf(18.00), fullMap.get(PricingService.BOILED_EGG));
    }

    @Test
    void testIsBillingSchedulerEnabled_True() {
        String buildingId = "b1";
        PricingConfig config = PricingConfig.builder()
                .buildingId(buildingId)
                .priceKey("billing_scheduler_enabled")
                .value(BigDecimal.ONE)
                .build();

        when(pricingConfigRepository.findByBuildingIdAndPriceKey(buildingId, "billing_scheduler_enabled"))
                .thenReturn(Optional.of(config));

        assertTrue(pricingService.isBillingSchedulerEnabled(buildingId));
    }

    @Test
    void testIsBillingSchedulerEnabled_False() {
        String buildingId = "b1";
        PricingConfig config = PricingConfig.builder()
                .buildingId(buildingId)
                .priceKey("billing_scheduler_enabled")
                .value(BigDecimal.ZERO)
                .build();

        when(pricingConfigRepository.findByBuildingIdAndPriceKey(buildingId, "billing_scheduler_enabled"))
                .thenReturn(Optional.of(config));

        assertFalse(pricingService.isBillingSchedulerEnabled(buildingId));
    }

    @Test
    void testIsBillingSchedulerEnabled_Missing() {
        String buildingId = "b1";
        when(pricingConfigRepository.findByBuildingIdAndPriceKey(buildingId, "billing_scheduler_enabled"))
                .thenReturn(Optional.empty());

        assertFalse(pricingService.isBillingSchedulerEnabled(buildingId));
    }
}
