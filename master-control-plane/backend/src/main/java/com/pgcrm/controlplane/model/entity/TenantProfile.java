package com.pgcrm.controlplane.model.entity;

import com.pgcrm.controlplane.model.enums.TenantStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "pg_name", nullable = false)
    private String pgName;

    @Column(name = "pg_short_title")
    private String pgShortTitle;

    @Column(name = "custom_domain", unique = true)
    private String customDomain;

    @Column(name = "router_ip")
    private String routerIp;

    @Column(name = "whatsapp_number")
    private String whatsappNumber;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "razorpay_key")
    private String razorpayKey;

    @Column(name = "razorpay_secret")
    private String razorpaySecret;

    @Column(name = "whatsapp_token")
    private String whatsappToken;

    @Column(name = "whatsapp_key")
    private String whatsappKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TenantStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
