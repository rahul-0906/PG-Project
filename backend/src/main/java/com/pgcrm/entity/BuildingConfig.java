package com.pgcrm.entity;

import com.pgcrm.entity.enums.EbSplitMethod;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "building_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BuildingConfig {

    @Id
    @Column(name = "building_id")
    private String buildingId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "building_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Building building;

    @Column(name = "food_included_in_rent", nullable = false)
    @Builder.Default
    private boolean foodIncludedInRent = false;

    @Column(name = "allow_meal_cancellations", nullable = false)
    @Builder.Default
    private boolean allowMealCancellations = true;

    @Column(name = "breakfast_price", nullable = false)
    @Builder.Default
    private BigDecimal breakfastPrice = BigDecimal.ZERO;

    @Column(name = "lunch_price", nullable = false)
    @Builder.Default
    private BigDecimal lunchPrice = BigDecimal.ZERO;

    @Column(name = "dinner_price", nullable = false)
    @Builder.Default
    private BigDecimal dinnerPrice = BigDecimal.ZERO;

    @Column(name = "omelette_price", nullable = false)
    @Builder.Default
    private BigDecimal omelettePrice = BigDecimal.ZERO;

    @Column(name = "boiled_egg_price", nullable = false)
    @Builder.Default
    private BigDecimal boiledEggPrice = BigDecimal.ZERO;

    @Column(name = "washing_machine_price", nullable = false)
    @Builder.Default
    private BigDecimal washingMachinePrice = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "eb_split_method")
    private EbSplitMethod ebSplitMethod;

    @Column(name = "breakfast_cutoff_time", nullable = false)
    @Builder.Default
    private java.time.LocalTime breakfastCutoffTime = java.time.LocalTime.of(22, 0);

    @Column(name = "dinner_cutoff_time", nullable = false)
    @Builder.Default
    private java.time.LocalTime dinnerCutoffTime = java.time.LocalTime.of(14, 0);

    @Column(name = "is_previous_day", nullable = false)
    @Builder.Default
    private boolean isPreviousDay = true;

    @Column(name = "allowed_payment_modes", nullable = false)
    @Builder.Default
    private String allowedPaymentModes = "BOTH";
}
