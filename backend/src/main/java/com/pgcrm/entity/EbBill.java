package com.pgcrm.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "eb_bills")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EbBill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "block_id", nullable = false)
    private Block block;

    @Column(name = "billing_period_start", nullable = false)
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false)
    private LocalDate billingPeriodEnd;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /** Rate per unit in ₹ — used for METER_BASED split method */
    @Column(name = "rate_per_unit", precision = 10, scale = 4)
    private BigDecimal ratePerUnit;

    @Column(name = "split_method", length = 30)
    private String splitMethod;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "ebBill", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<EbBillGuest> guestShares = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
