package com.pgcrm.dto;

import com.pgcrm.entity.Guest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Outbound read model (response DTO) representing a guest's complete profile.
 *
 * <p>Returned by manager and guest portal endpoints that expose guest data:
 * {@code GET /api/manager/guests}, {@code GET /api/manager/guests/{id}},
 * {@code GET /api/guest/profile}, and the check-in response.
 * This DTO is a <strong>flattened projection</strong> of the {@link Guest} aggregate
 * and its associated entities ({@link com.pgcrm.entity.Bed}, {@link com.pgcrm.entity.Room},
 * {@link com.pgcrm.entity.Floor}), eliminating the need for the frontend to make
 * multiple nested API calls to render a complete guest card.</p>
 *
 * <p><strong>Mapping:</strong> The static factory method {@link #fromEntity(Guest)}
 * performs a null-safe traversal of the entity graph:
 * {@code Guest → Bed → Room → Floor}, extracting only the fields needed by the UI.
 * Lazy-loaded associations must be initialised before this method is called —
 * callers should ensure the entity is fetched within an active Hibernate session
 * (i.e., within a {@code @Transactional} service method).</p>
 *
 * <p><strong>Flattened Location Fields:</strong> Rather than exposing nested entity
 * objects, this DTO exposes {@link #bedLabel}, {@link #roomNumber}, and
 * {@link #floorName} as flat string fields. This simplifies frontend rendering
 * and removes coupling to the entity graph structure.</p>
 *
 * @see Guest
 * @see GuestCheckInRequest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestResponse {

    /** UUID of the {@link Guest} entity. */
    private String id;

    /** UUID of the linked {@link com.pgcrm.entity.User} login account. */
    private String userId;

    /**
     * UUID of the currently assigned {@link com.pgcrm.entity.Bed}.
     * {@code null} if the guest has checked out or no bed has been assigned yet.
     */
    private String bedId;

    /**
     * Human-readable label of the assigned bed (e.g., {@code "A1"}, {@code "B2"}).
     * Derived from {@link com.pgcrm.entity.Bed#getBedLabel()}.
     * {@code null} if no bed is assigned.
     */
    private String bedLabel;

    /**
     * Room number of the room containing the assigned bed (e.g., {@code "101"}).
     * Derived from {@link com.pgcrm.entity.Room#getRoomNumber()}.
     * {@code null} if no bed or room is assigned.
     */
    private String roomNumber;

    /**
     * Human-readable floor label of the room (e.g., {@code "1st Floor"}).
     * Derived from {@link com.pgcrm.entity.Floor#getFloorLabel()}.
     * {@code null} if the floor has no label or no bed is assigned.
     */
    private String floorName;

    /**
     * Per-bed monthly base rent for the assigned room, in Indian Rupees (₹).
     * Derived from {@link com.pgcrm.entity.Room#getBaseRent()}.
     * Used by the frontend to display the expected rent in the guest profile card.
     * {@code null} if no room is assigned.
     */
    private BigDecimal baseRent;

    /** Full name of the guest, displayed in invoices and dashboard tables. */
    private String fullName;

    /** Primary email address of the guest; also their portal login identifier. */
    private String email;

    /** Primary contact phone number of the guest. */
    private String phone;

    /**
     * WhatsApp-enabled phone number for notification delivery.
     * May differ from {@link #phone}.
     */
    private String whatsappNumber;

    /**
     * Vehicle registration number for the guest's 2-wheeler, if applicable.
     * Example: {@code "TN22AB1234"}.
     */
    private String vehicleRegistration;

    /**
     * Current KYC verification status, serialised as its enum name string.
     * Possible values: {@code "PENDING"}, {@code "VERIFIED"}, {@code "REJECTED"}.
     *
     * @see com.pgcrm.entity.enums.KycStatus
     */
    private String kycStatus;

    /** The date the guest officially checked in and their bed was assigned. */
    private LocalDate checkInDate;

    /**
     * The originally planned check-out date communicated at check-in.
     * Informational only — actual departure is tracked via {@link #actualCheckOutDate}.
     */
    private LocalDate expectedCheckOutDate;

    /**
     * The date on which a formal checkout notice was issued.
     * Triggers computation of {@link #exitDate} by the service layer.
     */
    private LocalDate noticeDate;

    /**
     * Computed exit date: {@code noticeDate + noticePeriodDays} (from building config).
     * Displayed in the manager dashboard to forecast upcoming vacancies.
     */
    private LocalDate exitDate;

    /** The date the guest physically vacated their bed and completed checkout. */
    private LocalDate actualCheckOutDate;

    /**
     * Refundable security deposit collected at check-in, in Indian Rupees (₹).
     * Used in checkout settlement calculations.
     */
    private BigDecimal advanceDeposit;

    /**
     * Whether this guest is currently active (checked in).
     * {@code false} indicates the guest has checked out.
     */
    private boolean active;

    /**
     * Default breakfast opt-in preference.
     * {@code true} = auto-opted in to breakfast daily unless cancelled.
     */
    private boolean breakfastPreference;

    /**
     * Default lunch opt-in preference.
     * {@code true} = auto-opted in to lunch daily unless cancelled.
     */
    private boolean lunchPreference;

    /**
     * Default dinner opt-in preference.
     * {@code true} = auto-opted in to dinner daily unless cancelled.
     */
    private boolean dinnerPreference;

    /**
     * Default dietary preference.
     * {@code true} = Vegetarian, {@code false} = Non-Vegetarian.
     */
    private boolean vegPreference;

    /**
     * Whether the guest's room has AC.
     */
    @com.fasterxml.jackson.annotation.JsonProperty("isAc")
    private boolean isAc;

    /**
     * Whether the guest booked the entire room.
     */
    @com.fasterxml.jackson.annotation.JsonProperty("isBookEntireRoom")
    private boolean isBookEntireRoom;

    /**
     * List of all bed labels assigned to this guest.
     */
    private java.util.List<String> beds;

    /**
     * Comma-separated list of assigned bed labels.
     */
    private String assignedBeds;

    /**
     * Null-safe static factory method that maps a {@link Guest} entity
     * (and its lazily-loaded associations) to a flat {@code GuestResponse} DTO.
     *
     * <p>The method traverses the entity graph {@code Guest → Bed → Room → Floor}
     * with explicit {@code null} guards at each level to prevent
     * {@code NullPointerException} when associations are absent (e.g., a guest
     * whose bed has been unassigned after checkout).</p>
     *
     * <p><strong>Precondition:</strong> Must be called within an active Hibernate
     * session (inside a {@code @Transactional} service method) to allow lazy
     * association initialisation for {@code Bed}, {@code Room}, and {@code Floor}.</p>
     *
     * @param guest the {@link Guest} entity to map; returns {@code null} if {@code null}.
     * @return a fully populated {@code GuestResponse}, or {@code null} if input is {@code null}.
     */
    public static GuestResponse fromEntity(final Guest guest) {
        if (guest == null) {
            return null;
        }

        final String bedId    = guest.getBed() != null ? guest.getBed().getId() : null;

        java.util.List<String> bedsList = new java.util.ArrayList<>();
        String assignedBedsStr = "";
        if (guest.getBeds() != null && !guest.getBeds().isEmpty()) {
            for (com.pgcrm.entity.Bed b : guest.getBeds()) {
                bedsList.add(b.getBedLabel());
            }
            assignedBedsStr = String.join(", ", bedsList);
        }

        final String bedLabel = assignedBedsStr.isEmpty() ? null : assignedBedsStr;

        String roomNumber = null;
        String floorName  = null;
        BigDecimal baseRent = null;
        boolean isAc = false;

        if (guest.getBed() != null && guest.getBed().getRoom() != null) {
            roomNumber = guest.getBed().getRoom().getRoomNumber();
            baseRent   = guest.getBed().getRoom().getBaseRent();
            isAc       = guest.getBed().getRoom().isAc();
            if (guest.getBed().getRoom().getFloor() != null) {
                floorName = guest.getBed().getRoom().getFloor().getFloorLabel();
            }
        }

        return GuestResponse.builder()
                .id(guest.getId())
                .userId(guest.getUser() != null ? guest.getUser().getId() : null)
                .bedId(bedId)
                .bedLabel(bedLabel)
                .beds(bedsList)
                .assignedBeds(assignedBedsStr)
                .roomNumber(roomNumber)
                .floorName(floorName)
                .baseRent(baseRent)
                .fullName(guest.getFullName())
                .email(guest.getEmail())
                .phone(guest.getPhone())
                .whatsappNumber(guest.getWhatsappNumber())
                .vehicleRegistration(guest.getVehicleRegistration())
                .kycStatus(guest.getKycStatus() != null ? guest.getKycStatus().name() : null)
                .checkInDate(guest.getCheckInDate())
                .expectedCheckOutDate(guest.getExpectedCheckOutDate())
                .noticeDate(guest.getNoticeDate())
                .exitDate(guest.getExitDate())
                .actualCheckOutDate(guest.getActualCheckOutDate())
                .advanceDeposit(guest.getAdvanceDeposit())
                .active(guest.isActive())
                .breakfastPreference(guest.isBreakfastPreference())
                .lunchPreference(guest.isLunchPreference())
                .dinnerPreference(guest.isDinnerPreference())
                .vegPreference(guest.isVegPreference())
                .isAc(isAc)
                .isBookEntireRoom(guest.isBookEntireRoom())
                .build();
    }
}
