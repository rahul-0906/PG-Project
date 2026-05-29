package com.pgcrm.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pgcrm.entity.enums.KycStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "guests")
@SQLDelete(sql = "UPDATE guests SET is_deleted = true WHERE id=?")
@SQLRestriction("is_deleted = false")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Guest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bed_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "room"})
    private Bed bed;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    /** WhatsApp number for notifications (may differ from phone) */
    @Column(name = "whatsapp_number")
    private String whatsappNumber;

    /** 2-Wheeler vehicle registration number (e.g. TN22AB1234) */
    @Column(name = "vehicle_registration", length = 20)
    private String vehicleRegistration;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status")
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "check_in_date")
    private LocalDate checkInDate;

    @Column(name = "expected_check_out_date")
    private LocalDate expectedCheckOutDate;

    /** Date notice was formally given */
    @Column(name = "notice_date")
    private LocalDate noticeDate;

    /** Date calculated as notice_date + noticePeriodDays */
    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "actual_check_out_date")
    private LocalDate actualCheckOutDate;

    @Column(name = "advance_deposit", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal advanceDeposit = BigDecimal.ZERO;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "breakfast_preference")
    @Builder.Default
    private boolean breakfastPreference = false;

    @Column(name = "lunch_preference")
    @Builder.Default
    private boolean lunchPreference = false;

    @Column(name = "dinner_preference")
    @Builder.Default
    private boolean dinnerPreference = false;

    @Column(name = "is_veg_preference")
    @Builder.Default
    private boolean vegPreference = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
