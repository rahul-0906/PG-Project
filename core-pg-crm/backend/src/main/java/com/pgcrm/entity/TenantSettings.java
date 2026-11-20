package com.pgcrm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Per-tenant operational settings, branding, and encrypted external service credentials.
 *
 * <p>Stored inside each tenant's dedicated PostgreSQL schema ({@code t_<slug>.tenant_settings}).</p>
 */
@Entity
@Table(name = "tenant_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantSettings {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    // ── Branding & Theme ────────────────────────────────────────────────────────

    @Column(name = "branding_name", nullable = false)
    @Builder.Default
    private String brandingName = "PG CRM";

    @Column(name = "branding_short_name", length = 50, nullable = false)
    @Builder.Default
    private String brandingShortName = "PG";

    @Column(name = "branding_primary_color", length = 50, nullable = false)
    @Builder.Default
    private String brandingPrimaryColor = "#2563eb";

    @Column(name = "branding_logo_url", length = 500)
    private String brandingLogoUrl;

    // ── Razorpay Gateway (Secrets stored AES-GCM encrypted) ──────────────────────

    @Column(name = "razorpay_key_id")
    private String razorpayKeyId;

    @Column(name = "razorpay_key_secret", length = 500)
    private String razorpayKeySecret;

    @Column(name = "razorpay_webhook_secret", length = 500)
    private String razorpayWebhookSecret;

    // ── Meta WhatsApp Cloud API (Secrets stored AES-GCM encrypted) ──────────────

    @Column(name = "whatsapp_phone_number_id")
    private String whatsappPhoneNumberId;

    @Column(name = "whatsapp_access_token", length = 1000)
    private String whatsappAccessToken;

    @Column(name = "whatsapp_verify_token", length = 500)
    private String whatsappVerifyToken;

    // ── Notification Toggles ───────────────────────────────────────────────────

    @Column(name = "notification_email_enabled", nullable = false)
    @Builder.Default
    private boolean notificationEmailEnabled = true;

    @Column(name = "notification_whatsapp_enabled", nullable = false)
    @Builder.Default
    private boolean notificationWhatsappEnabled = false;

    // ── Timestamps ─────────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
