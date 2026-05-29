package com.pgcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String role;
    private String userId;
    private String fullName;
    private boolean firstLogin;
    private boolean mustChangePassword;
}
