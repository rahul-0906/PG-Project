package com.pgcrm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rooms")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Null for ground-floor standalone rooms with no block */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Block block;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Floor floor;

    @Column(name = "room_number", nullable = false)
    private String roomNumber;

    /** Number of beds: 2, 4, etc. Configured by Platform Admin */
    @Column(name = "sharing_type", nullable = false)
    private int sharingType;

    /** Base rent per bed — configured per room by Platform Admin */
    @Column(name = "base_rent", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseRent;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Bed> beds = new ArrayList<>();

    
}
