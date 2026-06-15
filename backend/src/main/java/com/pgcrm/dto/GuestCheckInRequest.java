package com.pgcrm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Inbound request payload for the guest check-in operation.
 *
 * <p>Submitted as a JSON body to {@code POST /api/manager/guests/checkin} by a
 * PG Manager. The {@code GuestService} validates the payload, creates the linked
 * {@link com.pgcrm.entity.User} account, assigns the specified {@link com.pgcrm.entity.Bed},
 * transitions the bed's status to {@code OCCUPIED}, and emits a welcome email and
 * optional WhatsApp notification to the new guest.</p>
 *
 * <p><strong>Bed Assignment:</strong> The {@link #bedId} must reference an existing
 * {@link com.pgcrm.entity.Bed} with status {@code VACANT}. The service layer throws
 * a {@code BedNotAvailableException} if the bed is already occupied or under maintenance.</p>
 *
 * <p><strong>Meal Preferences:</strong> The {@link #breakfastOpted}, {@link #lunchOpted},
 * and {@link #dinnerOpted} flags set the guest's <em>default</em> daily preferences on
 * the {@link com.pgcrm.entity.Guest} entity. Day-specific overrides submitted via the
 * daily-log endpoint always take precedence over these defaults.</p>
 *
 * <p><strong>Dietary Preference:</strong> The {@link #isVeg} flag is annotated with
 * {@link JsonProperty @JsonProperty("isVeg")} to ensure the JSON key is {@code "isVeg"}
 * rather than the Lombok-generated {@code "veg"}, maintaining API contract stability
 * regardless of the Java boolean getter naming convention.</p>
 *
 * @see GuestResponse
 * @see com.pgcrm.entity.Guest
 * @see com.pgcrm.entity.Bed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestCheckInRequest {

    /**
     * UUID of the {@link com.pgcrm.entity.Bed} to which the new guest will be assigned.
     * The bed must exist and must have a current status of {@code VACANT}.
     * The service will transition it to {@code OCCUPIED} upon successful check-in.
     */
    private String bedId;

    /**
     * Full legal name of the guest.
     * Stored on both the {@link com.pgcrm.entity.Guest} and associated
     * {@link com.pgcrm.entity.User} records. Appears in invoices, email communications,
     * and the manager dashboard.
     */
    private String fullName;

    /**
     * Primary email address of the guest.
     * Used as the login username for the guest portal and for invoice delivery.
     * Must be unique across all active user accounts in the system.
     */
    private String email;

    /**
     * Primary contact phone number of the guest, in E.164 format.
     * Displayed in the manager's guest-list dashboard for direct contact.
     */
    private String phone;

    /**
     * WhatsApp-enabled phone number for push notification delivery.
     * May differ from {@link #phone} if the guest uses a separate WhatsApp number.
     * If omitted, falls back to {@link #phone} for WhatsApp channel notifications.
     */
    private String whatsappNumber;

    /**
     * Refundable security deposit collected from the guest at check-in, in Indian Rupees (₹).
     * Stored on the {@link com.pgcrm.entity.Guest} record and displayed in the
     * guest profile. Defaults to {@code ₹0.00} if not provided.
     */
    private BigDecimal advanceDeposit;

    /**
     * The official check-in date, used to calculate billing start periods
     * and displayed on the guest's profile and invoices.
     * Defaults to the current server date if not explicitly provided by the manager.
     */
    private LocalDate checkInDate;

    /**
     * The official check-in time.
     */
    private LocalTime checkInTime;

    /**
     * Vehicle registration number of the guest's 2-wheeler, if applicable.
     * Used for parking allocation and gate-entry logs.
     * Example: {@code "TN22AB1234"}. May be {@code null} if the guest has no vehicle.
     */
    private String vehicleRegistration;

    /**
     * Default dietary preference for this guest.
     * {@code true} = Vegetarian, {@code false} = Non-Vegetarian.
     * Used to auto-populate the {@code is_veg} column in daily log records
     * and communicated to the kitchen for meal planning.
     * The {@code @JsonProperty("isVeg")} annotation preserves the API contract key name,
     * overriding Lombok's default boolean getter naming ({@code isVeg()} → {@code "veg"}).
     */
    @JsonProperty("isVeg")
    private boolean isVeg;

    /**
     * Default breakfast opt-in preference for this guest.
     * When {@code true}, the daily log system auto-opts the guest in to breakfast
     * unless they explicitly cancel for a given day.
     */
    private boolean breakfastOpted;

    /**
     * Default lunch opt-in preference for this guest.
     * When {@code true}, the daily log system auto-opts the guest in to lunch
     * unless they explicitly cancel for a given day.
     */
    private boolean lunchOpted;

    /**
     * Default dinner opt-in preference for this guest.
     * When {@code true}, the daily log system auto-opts the guest in to dinner
     * unless they explicitly cancel for a given day.
     */
    private boolean dinnerOpted;

    /**
     * Whether the guest booked the entire room.
     */
    @JsonProperty("isBookEntireRoom")
    private boolean isBookEntireRoom;

    /** List of all bed IDs in the room when booking the entire room. */
    private java.util.List<String> roomBedIds;

    /** List of all bed IDs in the room when booking the entire room (alias). */
    private java.util.List<String> bedIds;
}
