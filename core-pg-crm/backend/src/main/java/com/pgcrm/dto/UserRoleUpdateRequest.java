package com.pgcrm.dto;

import com.pgcrm.entity.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleUpdateRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "New role is required")
    private Role newRole;

    private String branchId;
}
