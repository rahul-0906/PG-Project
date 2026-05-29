package com.pgcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_logs", indexes = {
    @Index(name = "idx_daily_log_guest_date", columnList = "guest_id, log_date")
})
@SQLDelete(sql = "UPDATE daily_logs SET is_deleted = true WHERE id=?")
@SQLRestriction("is_deleted = false")
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DailyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "bed", "user", "invoices"})
    private Guest guest;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    // ── Meal Opts ─────────────────────────────────────────────
    @Column(name = "breakfast_opted")
    @Builder.Default
    private boolean breakfastOpted = false;

    @Column(name = "lunch_opted")
    @Builder.Default
    private boolean lunchOpted = false;

    @Column(name = "dinner_opted")
    @Builder.Default
    private boolean dinnerOpted = false;

    /** Veg or Non-Veg preference for the day */
    @Column(name = "is_veg")
    @Builder.Default
    @JsonProperty("isVeg")
    private boolean isVeg = true;

    @Column(name = "omelette_count")
    @Builder.Default
    private int omeletteCount = 0;

    @Column(name = "boiled_egg_count")
    @Builder.Default
    private int boiledEggCount = 0;

    // ── Laundry ──────────────────────────────────────────────
    @Column(name = "washing_machine_count")
    @Builder.Default
    private int washingMachineCount = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
