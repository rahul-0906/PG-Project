package com.pgcrm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "buildings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "building", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Floor> floors = new ArrayList<>();

    @OneToOne(mappedBy = "building", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private BuildingConfig buildingConfig;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
