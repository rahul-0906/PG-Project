package com.pgcrm.dto;

import com.pgcrm.entity.Guest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestResponse {
    private String id;
    private String userId;
    private String bedId;
    private String bedLabel;
    private String roomNumber;
    private String floorName;
    private BigDecimal baseRent;
    private String fullName;
    private String email;
    private String phone;
    private String whatsappNumber;
    private String vehicleRegistration;
    private String kycStatus;
    private LocalDate checkInDate;
    private LocalDate expectedCheckOutDate;
    private LocalDate noticeDate;
    private LocalDate exitDate;
    private LocalDate actualCheckOutDate;
    private BigDecimal advanceDeposit;
    private boolean active;
    private boolean breakfastPreference;
    private boolean lunchPreference;
    private boolean dinnerPreference;
    private boolean vegPreference;

    public static GuestResponse fromEntity(Guest guest) {
        if (guest == null) return null;
        String bedId = guest.getBed() != null ? guest.getBed().getId() : null;
        String bedLabel = guest.getBed() != null ? guest.getBed().getBedLabel() : null;
        String roomNumber = null;
        String floorName = null;
        BigDecimal baseRent = null;
        if (guest.getBed() != null && guest.getBed().getRoom() != null) {
            roomNumber = guest.getBed().getRoom().getRoomNumber();
            baseRent = guest.getBed().getRoom().getBaseRent();
            if (guest.getBed().getRoom().getFloor() != null) {
                floorName = guest.getBed().getRoom().getFloor().getFloorLabel();
            }
        }
        return GuestResponse.builder()
                .id(guest.getId())
                .userId(guest.getUser() != null ? guest.getUser().getId() : null)
                .bedId(bedId)
                .bedLabel(bedLabel)
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
                .build();
    }
}
