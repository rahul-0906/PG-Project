package com.pgcrm.controlplane.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_instances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "domain_name", nullable = false, unique = true)
    private String domainName;

    @Column(name = "vps_ip_address")
    private String vpsIpAddress;

    @Column(name = "allocated_port")
    private Integer allocatedPort;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TenantStatus status;

    @Column(name = "razorpay_order_id", unique = true)
    private String razorpayOrderId;

    @Column(name = "whatsapp_token")
    private String whatsappToken;

    @Column(name = "razorpay_key_id")
    private String razorpayKeyId;

    @Column(name = "razorpay_key_secret")
    private String razorpayKeySecret;

    @Column(name = "primary_color")
    private String primaryColor;

    @OneToOne(mappedBy = "tenantInstance", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Subscription subscription;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
