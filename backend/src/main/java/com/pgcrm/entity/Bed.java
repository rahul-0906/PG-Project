package com.pgcrm.entity;

import com.pgcrm.entity.enums.BedStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "beds")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Bed {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Room room;

    /** e.g. "A1", "A2", "B1" */
    @Column(name = "bed_label", nullable = false)
    private String bedLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BedStatus status = BedStatus.VACANT;

    
}
