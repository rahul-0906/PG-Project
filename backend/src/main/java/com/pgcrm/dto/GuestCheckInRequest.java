package com.pgcrm.dto;

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
public class GuestCheckInRequest {
    private String bedId;
    private String fullName;
    private String email;
    private String phone;
    private String whatsappNumber;
    private BigDecimal advanceDeposit;
    private LocalDate checkInDate;
    private String vehicleRegistration;
    private boolean isVeg;
    private boolean breakfastOpted;
    private boolean lunchOpted;
    private boolean dinnerOpted;
}
