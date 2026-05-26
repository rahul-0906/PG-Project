package com.pgcrm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "blocks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"blocks", "standaloneRooms"})
    private Floor floor;

    @Column(nullable = false)
    private String name; // e.g. "Block A", "Block B"

    @OneToMany(mappedBy = "block", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Room> rooms = new ArrayList<>();

    
}
