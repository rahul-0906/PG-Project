package com.pgcrm.controller;

import com.pgcrm.entity.User;
import com.pgcrm.entity.enums.AuditAction;
import com.pgcrm.repository.UserRepository;
import com.pgcrm.service.AuditService;
import com.pgcrm.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.login(body.get("email"), body.get("password")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.refresh(body.get("refreshToken")));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Forces a password change for the authenticated user.
     * Required when mustChangePassword = true (first login after check-in).
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestBody Map<String, String> body,
            Authentication auth) {

        String userId = auth.getName();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String currentPassword = body.get("currentPassword");
        String newPassword     = body.get("newPassword");

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Current password is incorrect"));
        }

        if (newPassword == null || newPassword.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "New password must be at least 8 characters"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setFirstLogin(false);
        userRepository.save(user);

        auditService.log(AuditAction.PASSWORD_CHANGED, "User", userId, "Password changed by user");

        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
