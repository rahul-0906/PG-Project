package com.pgcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound request payload for the login/authentication endpoint.
 *
 * <p>Submitted as a JSON body to {@code POST /api/auth/login}. The
 * {@code AuthService} validates the supplied credentials against the
 * {@link com.pgcrm.entity.User} table and, on success, returns an
 * {@link AuthResponse} containing the JWT access token and role information.</p>
 *
 * <p><strong>Field constraints (enforced at the service layer):</strong></p>
 * <ul>
 *   <li>{@link #email} — must be a valid, registered email address.</li>
 *   <li>{@link #password} — must match the BCrypt-hashed credential stored
 *       for the user; never logged or serialised back to the client.</li>
 * </ul>
 *
 * <p>Lombok's {@code @Data} generates getters, setters, {@code equals},
 * {@code hashCode}, and {@code toString}. The {@code toString} will include
 * the password field — ensure this DTO is never passed to any logging call
 * in the service or controller layer.</p>
 *
 * @see AuthResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {

    /**
     * The user's registered email address, used as the unique login identifier.
     * Case-insensitive comparison is applied by the authentication service.
     */
    private String email;

    /**
     * The user's plain-text password.
     * Compared against the BCrypt hash stored in the database.
     * <strong>Must never be persisted, returned in a response, or written to logs.</strong>
     */
    private String password;
}
