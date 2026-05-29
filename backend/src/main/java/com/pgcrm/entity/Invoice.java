package com.pgcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pgcrm.entity.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@SQLDelete(sql = "UPDATE invoices SET is_deleted = true WHERE id=?")
@SQLRestriction("is_deleted = false")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Invoice {

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

    @Column(name = "invoice_month", nullable = false)
    private int month;

    @Column(name = "invoice_year", nullable = false)
    private int year;


    @Column(name = "total_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.GENERATED;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // ── Razorpay Payment ──────────────────────────────────────
    @Column(name = "razorpay_order_id")
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "payment_method")
    private String paymentMethod;  // UPI / CARD / NETBANKING / etc.

    /** Timestamp when the 5-day reminder was sent — null means not yet sent */
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @Builder.Default
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        generatedAt = LocalDateTime.now();
    }
}
