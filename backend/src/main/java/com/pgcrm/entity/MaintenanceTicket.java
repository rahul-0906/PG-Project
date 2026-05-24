package com.pgcrm.entity;

import com.pgcrm.entity.enums.MaintenancePriority;
import com.pgcrm.entity.enums.MaintenanceStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_tickets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MaintenanceTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raised_by_guest_id")
    private Guest raisedByGuest;

    @Column(name = "building_id")
    private String buildingId;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MaintenanceStatus status = MaintenanceStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MaintenancePriority priority = MaintenancePriority.MEDIUM;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
