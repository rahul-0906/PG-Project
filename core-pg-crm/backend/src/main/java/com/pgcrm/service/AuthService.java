package com.pgcrm.service;

import com.pgcrm.dto.AuthResponse;
import com.pgcrm.entity.User;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.repository.UserRepository;
import com.pgcrm.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;

/**
 * Service responsible for all authentication lifecycle operations in the PG CRM application.
 *
 * <p>Implements the core security workflows:</p>
 * <ul>
 *   <li><strong>Login:</strong> Validates credentials, issues access and refresh JWT tokens.</li>
 *   <li><strong>Token Refresh:</strong> Validates a refresh token and issues a new access token.</li>
 *   <li><strong>Forgot Password:</strong> Generates a cryptographically secure temporary password,
 *       encodes it, and dispatches a password-reset email via {@link EmailService}.</li>
 * </ul>
 *
 * <p><strong>Security Notes:</strong></p>
 * <ul>
 *   <li>Both invalid email and invalid password responses throw {@link ResourceNotFoundException}
 *       with the generic message {@code "Invalid credentials"} to prevent user enumeration attacks.</li>
 *   <li>Forgot-password requests for unregistered or inactive emails are silently ignored
 *       (no exception, no distinguishable HTTP response) to prevent account existence disclosure.</li>
 *   <li>Temporary passwords are generated using {@link SecureRandom} with an ambiguity-reduced
 *       character set (no {@code O/0}, {@code I/1/l}) to minimise support calls.</li>
 * </ul>
 *
 * @see JwtUtil
 * @see EmailService
 * @see UserRepository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    /** Ambiguity-reduced character set for generated temporary passwords. */
    private static final String TEMP_PASS_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    /** Cryptographically secure random number generator for temporary password generation. */
    private static final SecureRandom RNG = new SecureRandom();

    private final UserRepository       userRepository;
    private final EmailService         emailService;
    private final JwtUtil              jwtUtil;
    private final PasswordEncoder      passwordEncoder;
    private final AuthenticationManager authenticationManager;

    /**
     * Authenticates a user by email and password, returning a signed JWT pair on success.
     *
     * <p>Authentication flow:</p>
     * <ol>
     *   <li>Looks up the user by email (case-insensitive).</li>
     *   <li>Verifies the account is active; throws if deactivated.</li>
     *   <li>Verifies the raw password against the stored BCrypt hash.</li>
     *   <li>Generates an access token (short-lived) and a refresh token (long-lived).</li>
     *   <li>Returns a fully-populated {@link AuthResponse} including role, branchId,
     *       and first-login flags consumed by the frontend routing logic.</li>
     * </ol>
     *
     * @param email    the user's email address (case-insensitive).
     * @param password the raw plaintext password submitted by the user.
     * @return a populated {@link AuthResponse} containing both JWT tokens and user metadata.
     * @throws ResourceNotFoundException if no account exists for the given email, or
     *                                   if the password does not match — both cases return
     *                                   the generic message {@code "Invalid credentials"}.
     * @throws RuntimeException          if the account is deactivated.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(final String email, final String password) {
        log.info("Login attempt initiated for email: {}", email);
        final User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    log.warn("Login failed: User not found for email: {}", email);
                    return new ResourceNotFoundException("Invalid credentials");
                });

        if (!user.isActive()) {
            log.warn("Login failed: Account is deactivated for email: {}", email);
            throw new RuntimeException("Account is deactivated");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("Login failed: Password mismatch for email: {}", email);
            throw new ResourceNotFoundException("Invalid credentials");
        }

        final String accessToken  = jwtUtil.generateAccessToken(user.getId(), user.getRole(), user.getBranchId());
        final String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        log.info("Login successful for email: {} with role: {}", email, user.getRole());
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Issues a new access token using a valid, unexpired refresh token.
     *
     * <p>The refresh token is validated cryptographically. If valid, the user is
     * re-fetched from the database to ensure their account is still active and
     * their role/branchId reflect the latest state. The original refresh token
     * is preserved in the response (refresh token rotation is not implemented).</p>
     *
     * @param refreshToken the previously issued refresh token from the client's secure storage.
     * @return a populated {@link AuthResponse} with a new access token and the same refresh token.
     * @throws RuntimeException          if the refresh token is expired, malformed, or invalid.
     * @throws ResourceNotFoundException if the user referenced by the token no longer exists.
     */
    @Transactional
    public AuthResponse refresh(final String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken)) {
            log.warn("Token refresh failed: Invalid refresh token");
            throw new RuntimeException("Invalid refresh token");
        }

        final String userId = jwtUtil.extractUserId(refreshToken);
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Token refresh failed: User not found for ID: {}", userId);
                    return new ResourceNotFoundException("User not found: " + userId);
                });

        final String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getRole(), user.getBranchId());
        log.info("Token refreshed successfully for user ID: {}", userId);
        return buildAuthResponse(user, newAccessToken, refreshToken);
    }

    /**
     * Processes a forgot-password request for the given email address.
     *
     * <p>If the email is registered and the account is active, a cryptographically
     * secure temporary password is generated, encoded with BCrypt, persisted to the
     * user record, and dispatched via {@link EmailService#sendPasswordResetEmail(User, String)}.
     * The user is flagged with {@code mustChangePassword = true} and {@code firstLogin = true}
     * to force a password change on next login.</p>
     *
     * <p>If the email is unregistered or the account is inactive, the method returns silently
     * without raising an exception or distinguishable HTTP response, preventing account
     * enumeration via the forgot-password endpoint.</p>
     *
     * @param email the email address that requested a password reset.
     */
    @Transactional
    public void processForgotPassword(final String email) {
        log.info("Password reset request received for email: {}", email);
        final Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isPresent()) {
            final User user = userOpt.get();
            if (!user.isActive()) {
                log.info("Password reset requested for inactive user: {}", email);
                return;
            }

            final String tempPassword = generateTempPassword(10);
            user.setPassword(passwordEncoder.encode(tempPassword));
            user.setMustChangePassword(true);
            user.setFirstLogin(true);
            userRepository.save(user);

            emailService.sendPasswordResetEmail(user, tempPassword);
            log.info("Temporary password generated and email dispatched for user: {}", email);
        } else {
            log.info("Password reset requested for unregistered email: {}", email);
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Builds a standardised {@link AuthResponse} from a user entity and the issued token pair.
     *
     * <p>Defensively falls back to an empty string for {@code fullName} if the user's
     * name has not yet been set (e.g., immediately after a programmatic account creation).</p>
     *
     * @param user         the authenticated {@link User} entity.
     * @param accessToken  the newly issued JWT access token.
     * @param refreshToken the JWT refresh token (either newly issued or preserved from the request).
     * @return a fully populated {@link AuthResponse}.
     */
    private AuthResponse buildAuthResponse(final User user, final String accessToken, final String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .userId(user.getId())
                .fullName(user.getFullName() != null ? user.getFullName() : "")
                .branchId(user.getBranchId())
                .firstLogin(user.isFirstLogin())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    /**
     * Generates a cryptographically secure temporary password of the given length.
     *
     * <p>The character set {@link #TEMP_PASS_CHARS} excludes visually ambiguous characters
     * ({@code O}, {@code 0}, {@code I}, {@code 1}, {@code l}) to reduce transcription errors
     * when users read their temporary password from an email client.</p>
     *
     * @param length the desired password length (typically {@code 10}).
     * @return a randomly generated temporary password string.
     */
    private String generateTempPassword(final int length) {
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(TEMP_PASS_CHARS.charAt(RNG.nextInt(TEMP_PASS_CHARS.length())));
        }
        return sb.toString();
    }
}
