package com.pgcrm.dto;

import com.pgcrm.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String email;
    private String fullName;
    private String phone;
    private String role;
    private String branchId;
    private boolean active;
    private boolean firstLogin;
    private boolean mustChangePassword;

    public static UserResponse fromEntity(User user) {
        if (user == null) return null;
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .branchId(user.getBranchId())
                .active(user.isActive())
                .firstLogin(user.isFirstLogin())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }
}
