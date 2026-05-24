package com.pgcrm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "eb_bill_guests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EbBillGuest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eb_bill_id", nullable = false)
    private EbBill ebBill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @Column(name = "share_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal shareAmount;

    /** For METER_BASED split — previous meter reading (kWh) */
    @Column(name = "previous_reading", precision = 10, scale = 2)
    private BigDecimal previousReading;

    /** For METER_BASED split — current meter reading (kWh) */
    @Column(name = "current_reading", precision = 10, scale = 2)
    private BigDecimal currentReading;

    /** Units consumed = currentReading - previousReading */
    @Column(name = "units_consumed", precision = 10, scale = 2)
    private BigDecimal unitsConsumed;
}
