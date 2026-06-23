package com.pgcrm.controlplane.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRegistrationRequest {

    @NotBlank(message = "Owner name is required")
    private String ownerName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String phone;

    @NotBlank(message = "PG Brand Name is required")
    private String pgBrandName;

    @NotBlank(message = "Desired domain/subdomain name is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Domain name must be alphanumeric and hyphen only")
    private String domainName;

    private String whatsappToken;
    private String razorpayKeyId;
    private String razorpayKeySecret;
    private String primaryColor;
}
