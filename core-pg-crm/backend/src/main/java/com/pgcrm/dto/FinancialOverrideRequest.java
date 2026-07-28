package com.pgcrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialOverrideRequest {

    @NotBlank(message = "Guest ID is required")
    private String guestId;

    private String invoiceId;

    @NotBlank(message = "Override type is required (e.g. WAIVE_RENT, ADJUST_DEPOSIT, CUSTOM_DISCOUNT)")
    private String overrideType;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    @NotBlank(message = "Audit reason is required")
    private String reason;
}
