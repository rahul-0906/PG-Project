package com.pgcrm.service;

import com.pgcrm.dto.AuthResponse;
import com.pgcrm.entity.User;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.repository.UserRepository;
import com.pgcrm.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}.
 *
 * <p>All dependencies are mocked via Mockito — no Spring context, no database.
 * Tests cover every public method and all significant branch conditions.</p>
 *
 * <p>Coverage targets:
 * <ul>
 *   <li>{@link AuthService#login}: valid, wrong password, deactivated, unknown email</li>
 *   <li>{@link AuthService#refresh}: valid token, invalid token, user deleted</li>
 *   <li>{@link AuthService#processForgotPassword}: registered, unregistered, inactive</li>
 *   <li>{@link AuthService#changePassword}: success, wrong current pw, too short, user not found</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private UserRepository       userRepository;
    @Mock private EmailService         emailService;
    @Mock private JwtUtil              jwtUtil;
    @Mock private PasswordEncoder      passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id("user-001")
                .email("test@pgcrm.com")
                .password("$2a$encoded-hash")
                .role(Role.PG_OWNER)
                .fullName("Test Owner")
                .active(true)
                .firstLogin(false)
                .mustChangePassword(false)
                .build();
    }

    // ── login() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("returns AuthResponse with tokens on valid credentials")
        void login_validCredentials_returnsAuthResponse() {
            when(userRepository.findByEmailIgnoreCase("test@pgcrm.com"))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("rawPass", "$2a$encoded-hash")).thenReturn(true);
            when(jwtUtil.generateAccessToken(anyString(), any(), any())).thenReturn("access-tok");
            when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh-tok");

            AuthResponse resp = authService.login("test@pgcrm.com", "rawPass");

            assertThat(resp.getAccessToken()).isEqualTo("access-tok");
            assertThat(resp.getRefreshToken()).isEqualTo("refresh-tok");
            assertThat(resp.getRole()).isEqualTo("PG_OWNER");
            assertThat(resp.getUserId()).isEqualTo("user-001");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for unknown email")
        void login_unknownEmail_throwsResourceNotFound() {
            when(userRepository.findByEmailIgnoreCase("ghost@pgcrm.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login("ghost@pgcrm.com", "any"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Invalid credentials");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for wrong password")
        void login_wrongPassword_throwsResourceNotFound() {
            when(userRepository.findByEmailIgnoreCase("test@pgcrm.com"))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrongPass", "$2a$encoded-hash")).thenReturn(false);

            assertThatThrownBy(() -> authService.login("test@pgcrm.com", "wrongPass"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Invalid credentials");
        }

        @Test
        @DisplayName("throws RuntimeException for deactivated account")
        void login_deactivatedAccount_throwsRuntimeException() {
            activeUser.setActive(false);
            when(userRepository.findByEmailIgnoreCase("test@pgcrm.com"))
                    .thenReturn(Optional.of(activeUser));

            assertThatThrownBy(() -> authService.login("test@pgcrm.com", "anyPass"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("deactivated");
        }

        @Test
        @DisplayName("does NOT leak user existence — wrong pw and unknown email both throw same exception type")
        void login_enumProtection_sameExceptionForWrongPwAndUnknownEmail() {
            when(userRepository.findByEmailIgnoreCase("ghost@pgcrm.com"))
                    .thenReturn(Optional.empty());
            when(userRepository.findByEmailIgnoreCase("test@pgcrm.com"))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrongPass", "$2a$encoded-hash")).thenReturn(false);

            Throwable unknownEmail = catchThrowable(() -> authService.login("ghost@pgcrm.com", "any"));
            Throwable wrongPw      = catchThrowable(() -> authService.login("test@pgcrm.com", "wrongPass"));

            assertThat(unknownEmail).isInstanceOf(ResourceNotFoundException.class);
            assertThat(wrongPw).isInstanceOf(ResourceNotFoundException.class);
            // Both must carry the same generic message (no user-existence disclosure)
            assertThat(unknownEmail.getMessage()).isEqualTo(wrongPw.getMessage());
        }
    }

    // ── refresh() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        @DisplayName("returns new access token when refresh token is valid")
        void refresh_validToken_returnsNewAccessToken() {
            when(jwtUtil.isTokenValid("good-refresh")).thenReturn(true);
            when(jwtUtil.extractUserId("good-refresh")).thenReturn("user-001");
            when(userRepository.findById("user-001")).thenReturn(Optional.of(activeUser));
            when(jwtUtil.generateAccessToken(anyString(), any(), any())).thenReturn("new-access-tok");

            AuthResponse resp = authService.refresh("good-refresh");

            assertThat(resp.getAccessToken()).isEqualTo("new-access-tok");
            assertThat(resp.getRefreshToken()).isEqualTo("good-refresh"); // original preserved
        }

        @Test
        @DisplayName("throws RuntimeException when refresh token is invalid")
        void refresh_invalidToken_throwsRuntimeException() {
            when(jwtUtil.isTokenValid("expired-refresh")).thenReturn(false);

            assertThatThrownBy(() -> authService.refresh("expired-refresh"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Invalid refresh token");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user referenced in token is deleted")
        void refresh_userDeleted_throwsResourceNotFound() {
            when(jwtUtil.isTokenValid("good-refresh")).thenReturn(true);
            when(jwtUtil.extractUserId("good-refresh")).thenReturn("deleted-user-id");
            when(userRepository.findById("deleted-user-id")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh("good-refresh"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── processForgotPassword() ───────────────────────────────────────────────

    @Nested
    @DisplayName("processForgotPassword()")
    class ForgotPasswordTests {

        @Test
        @DisplayName("sends reset email for registered active account")
        void forgotPassword_registeredActive_sendsEmail() {
            when(userRepository.findByEmailIgnoreCase("test@pgcrm.com"))
                    .thenReturn(Optional.of(activeUser));

            authService.processForgotPassword("test@pgcrm.com");

            verify(userRepository).save(activeUser);
            verify(emailService).sendPasswordResetEmail(eq(activeUser), anyString());
            assertThat(activeUser.isMustChangePassword()).isTrue();
            assertThat(activeUser.isFirstLogin()).isTrue();
        }

        @Test
        @DisplayName("silently ignores unregistered email (no exception, no email)")
        void forgotPassword_unregisteredEmail_silentlyIgnored() {
            when(userRepository.findByEmailIgnoreCase("nobody@pgcrm.com"))
                    .thenReturn(Optional.empty());

            // Must NOT throw
            assertThatNoException().isThrownBy(
                    () -> authService.processForgotPassword("nobody@pgcrm.com"));
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("silently ignores inactive account (no email sent)")
        void forgotPassword_inactiveAccount_silentlyIgnored() {
            activeUser.setActive(false);
            when(userRepository.findByEmailIgnoreCase("test@pgcrm.com"))
                    .thenReturn(Optional.of(activeUser));

            assertThatNoException().isThrownBy(
                    () -> authService.processForgotPassword("test@pgcrm.com"));
            verifyNoInteractions(emailService);
        }
    }

    // ── changePassword() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("changePassword()")
    class ChangePasswordTests {

        @Test
        @DisplayName("updates password and clears mustChangePassword flag on success")
        void changePassword_validInputs_updatesPasswordAndClearsFlag() {
            when(userRepository.findById("user-001")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("currentRaw", "$2a$encoded-hash")).thenReturn(true);
            when(passwordEncoder.encode("newSecure123")).thenReturn("$2a$new-encoded");

            authService.changePassword("user-001", "currentRaw", "newSecure123");

            assertThat(activeUser.getPassword()).isEqualTo("$2a$new-encoded");
            assertThat(activeUser.isMustChangePassword()).isFalse();
            assertThat(activeUser.isFirstLogin()).isFalse();
            verify(userRepository).save(activeUser);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when current password is wrong")
        void changePassword_wrongCurrentPassword_throwsIllegalArgument() {
            when(userRepository.findById("user-001")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrong", "$2a$encoded-hash")).thenReturn(false);

            assertThatThrownBy(() -> authService.changePassword("user-001", "wrong", "newSecure123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Current password is incorrect");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws IllegalArgumentException when new password is too short (< 8 chars)")
        void changePassword_shortNewPassword_throwsIllegalArgument() {
            when(userRepository.findById("user-001")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("currentRaw", "$2a$encoded-hash")).thenReturn(true);

            assertThatThrownBy(() -> authService.changePassword("user-001", "currentRaw", "short"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 8 characters");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws IllegalArgumentException when new password is null")
        void changePassword_nullNewPassword_throwsIllegalArgument() {
            when(userRepository.findById("user-001")).thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("currentRaw", "$2a$encoded-hash")).thenReturn(true);

            assertThatThrownBy(() -> authService.changePassword("user-001", "currentRaw", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user ID does not exist")
        void changePassword_unknownUser_throwsResourceNotFound() {
            when(userRepository.findById("ghost-id")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.changePassword("ghost-id", "any", "newSecure123"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
