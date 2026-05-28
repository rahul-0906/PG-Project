package com.pgcrm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores per-building pricing overrides.
 * Key examples: "breakfast", "lunch", "dinner", "washing_machine", "omelette", "boiled_egg"
 * Falls back to tenant-config.yml defaults if no override exists for the building.
 */
@Entity
@Table(name = "pricing_config",
        uniqueConstraints = @UniqueConstraint(columnNames = {"building_id", "price_key"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PricingConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "building_id", nullable = false)
    private String buildingId;

    @Column(name = "price_key", nullable = false, length = 50)
    private String priceKey;

    @Column(name = "price_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
