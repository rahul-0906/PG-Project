package com.pgcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pgcrm.entity.enums.InvoiceLineType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "invoice_line_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvoiceLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "lineItems", "guest"})
    private Invoice invoice;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceLineType type;

    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
}
