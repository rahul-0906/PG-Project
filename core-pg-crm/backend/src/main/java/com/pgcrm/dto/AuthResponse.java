package com.pgcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbound response payload returned on successful authentication.
 *
 * <p>Returned by {@code POST /api/auth/login} after the {@code AuthService}
 * validates the supplied {@link AuthRequest} credentials. The client stores
 * the {@link #accessToken} and attaches it as a {@code Bearer} token in the
 * {@code Authorization} header of every subsequent API request.</p>
 *
 * <p><strong>Token Strategy:</strong></p>
 * <ul>
 *   <li>{@link #accessToken} — Short-lived JWT containing the user's ID, role,
 *       and branch scope. Expiry is configured in {@code application.yml} under
 *       {@code jwt.access-token-expiration-ms}.</li>
 *   <li>{@link #refreshToken} — Longer-lived opaque token used to obtain a new
 *       {@code accessToken} without re-authenticating. Stored securely by the
 *       client and submitted to {@code POST /api/auth/refresh}.</li>
 * </ul>
 *
 * <p><strong>First-Login / Force-Change-Password Flow:</strong></p>
 * <p>If {@link #mustChangePassword} is {@code true}, the React frontend immediately
 * redirects the user to the password-change page. All other navigation is blocked
 * client-side until the password is successfully updated and a fresh token is issued.</p>
 *
 * <p>If {@link #firstLogin} is {@code true}, an onboarding prompt is displayed
 * after the password-change step (if applicable).</p>
 *
 * @see AuthRequest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * Short-lived JWT access token to be attached as a {@code Bearer} token
     * in the {@code Authorization} header of all protected API requests.
     */
    private String accessToken;

    /**
     * Long-lived refresh token used to obtain a new {@link #accessToken}
     * when the current one expires, without requiring the user to log in again.
     * Must be stored securely on the client (e.g., in an {@code HttpOnly} cookie
     * or secure storage, not {@code localStorage}).
     */
    private String refreshToken;

    /**
     * The application role assigned to the authenticated user.
     * Serialised as the enum name string: {@code "PG_OWNER"}, {@code "PG_MANAGER"},
     * or {@code "GUEST"}. Used by the React router to direct the user to the
     * appropriate dashboard and restrict navigation.
     *
     * @see com.pgcrm.entity.enums.Role
     */
    private String role;

    /**
     * The UUID of the authenticated {@link com.pgcrm.entity.User} record.
     * Used by the frontend to construct profile-specific API requests.
     */
    private String userId;

    /**
     * The display name of the authenticated user, shown in the portal header
     * and dashboard greeting immediately after login.
     */
    private String fullName;

    /**
     * Comma-separated list of {@link com.pgcrm.entity.Building} UUIDs that
     * the authenticated manager is authorised to access.
     * {@code null} for {@code PG_OWNER} (unrestricted) and {@code GUEST} roles.
     * Used by the frontend to scope building-picker dropdowns and API calls.
     */
    private String branchId;

    /**
     * Whether this is the user's first authenticated session.
     * When {@code true}, the frontend may show an onboarding wizard or
     * profile-completion prompt after successful login.
     */
    private boolean firstLogin;

    /**
     * Whether the user is required to change their password before accessing
     * any protected resource. When {@code true}, the frontend immediately redirects
     * to the forced password-change page, blocking all other navigation until
     * the password has been updated and a fresh token issued.
     */
    private boolean mustChangePassword;
}
