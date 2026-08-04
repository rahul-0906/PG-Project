package com.pgcrm.controller;

import com.pgcrm.dto.AuthRequest;
import com.pgcrm.dto.AuthResponse;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.service.AuditService;
import com.pgcrm.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for all authentication lifecycle endpoints.
 *
 * <p>This controller is intentionally thin — it handles only HTTP concerns
 * (request parsing, response building, HTTP status codes) and delegates all
 * business logic to {@link AuthService}. No repositories or encoders are injected
 * here directly.</p>
 *
 * <p><strong>QUAL-01:</strong> The {@code changePassword} endpoint previously contained
 * business logic inline (user lookup, password verification, encoding, persistence).
 * This has been moved to {@link AuthService#changePassword(String, String, String)}.</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest body) {
        return ResponseEntity.ok(authService.login(body.getEmail(), body.getPassword()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.refresh(body.get("refreshToken")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }
        authService.processForgotPassword(email.trim());
        return ResponseEntity.ok(Map.of("message", "If the email is registered, password reset instructions have been sent."));
    }

    /**
     * Forces a password change for the authenticated user.
     *
     * <p>Required when {@code mustChangePassword = true} (first login after check-in or
     * after a password reset). Delegates all validation and persistence logic to
     * {@link AuthService#changePassword(String, String, String)}.</p>
     *
     * <p>Errors from the service layer ({@link IllegalArgumentException}) are handled
     * by {@link GlobalExceptionHandler} and returned as {@code 400 Bad Request}.</p>
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestBody Map<String, String> body,
            Authentication auth) {

        if (auth == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Unauthorized: Session expired or invalid. Please login again."));
        }

        authService.changePassword(
            auth.getName(),
            body.get("currentPassword"),
            body.get("newPassword")
        );

        auditService.log(AuditAction.PASSWORD_CHANGED, "User", auth.getName(), "Password changed by user");

        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
