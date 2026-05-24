package com.pgcrm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "floors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Floor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Building building;

    @Column(name = "floor_number", nullable = false)
    private int floorNumber;

    @Column(name = "floor_label")
    private String floorLabel; // e.g. "Ground Floor", "1st Floor"

    @OneToMany(mappedBy = "floor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Block> blocks = new ArrayList<>();

    /** Rooms directly on this floor (no block — e.g. Ground Floor) */
    @OneToMany(mappedBy = "floor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Room> standaloneRooms = new ArrayList<>();

    
}
