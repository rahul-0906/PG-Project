package com.pgcrm.dto;

import com.pgcrm.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbound read model (response DTO) representing a {@link User} account record.
 *
 * <p>Returned by user-management endpoints consumed by the PG Owner dashboard:
 * {@code GET /api/owner/users}, {@code GET /api/owner/users/{id}}, and the
 * manager-creation response. This DTO is a <strong>sanitised projection</strong>
 * of the {@link User} entity — the {@link User#getPassword()} field is
 * <strong>intentionally excluded</strong> to ensure credentials are never
 * exposed through the API layer.</p>
 *
 * <p><strong>Role String:</strong> The {@link #role} field is serialised as the
 * enum name string (e.g., {@code "PG_MANAGER"}) rather than the enum constant,
 * decoupling the API contract from the enum class definition and allowing
 * frontend consumers to use the role string directly in display logic.</p>
 *
 * <p><strong>Branch Scope:</strong> The {@link #branchId} field exposes the
 * comma-separated list of building UUIDs assigned to a {@code PG_MANAGER}.
 * The owner dashboard uses this to display which buildings a manager oversees
 * and to populate the building-picker when editing manager assignments.</p>
 *
 * <p><strong>Mapping:</strong> The static factory method {@link #fromEntity(User)}
 * provides a clean, null-safe conversion from the JPA entity. It does not require
 * an active Hibernate session since no lazy associations are accessed.</p>
 *
 * @see User
 * @see com.pgcrm.entity.enums.Role
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    /** UUID of the {@link User} entity. */
    private String id;

    /**
     * The user's unique email address and portal login identifier.
     * Displayed in the owner dashboard's user-management table.
     */
    private String email;

    /**
     * The user's display name, shown in the portal header and manager profile cards.
     */
    private String fullName;

    /** Contact phone number for the user account. */
    private String phone;

    /**
     * The application role assigned to this user, serialised as its enum name string.
     * Possible values: {@code "PG_OWNER"}, {@code "PG_MANAGER"}, {@code "GUEST"}.
     *
     * @see com.pgcrm.entity.enums.Role
     */
    private String role;

    /**
     * Comma-separated list of {@link com.pgcrm.entity.Building} UUIDs this user
     * is authorised to manage. Relevant only for {@code PG_MANAGER} accounts.
     * {@code null} for {@code PG_OWNER} and {@code GUEST} roles.
     */
    private String branchId;

    /**
     * Whether this user account is currently active and able to authenticate.
     * Deactivated users ({@code false}) are blocked at the JWT filter layer.
     */
    private boolean active;

    /**
     * Whether this is the user's first login session.
     * Used by the frontend to trigger onboarding flows after the initial sign-in.
     */
    private boolean firstLogin;

    /**
     * Whether this user is required to change their password before accessing
     * protected resources. Displays a forced password-change prompt in the
     * frontend router guard when {@code true}.
     */
    private boolean mustChangePassword;

    /**
     * Null-safe static factory method that maps a {@link User} entity to a
     * sanitised {@code UserResponse} DTO, excluding all sensitive fields.
     *
     * <p>No lazy associations are accessed during this mapping, so the method
     * may be called outside of an active Hibernate session without risk of a
     * {@code LazyInitializationException}.</p>
     *
     * <p>The {@link User#getRole()} enum is safely converted to its string name
     * with an explicit {@code null} guard to handle partially constructed user
     * records (e.g., during seeding or import workflows).</p>
     *
     * @param user the {@link User} entity to map; returns {@code null} if {@code null}.
     * @return a sanitised {@code UserResponse}, or {@code null} if input is {@code null}.
     */
    public static UserResponse fromEntity(final User user) {
        if (user == null) {
            return null;
        }

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
