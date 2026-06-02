package com.pgcrm.service;

import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.repository.*;
import com.pgcrm.security.JwtUtil;
import com.pgcrm.dto.AuthResponse;
import com.pgcrm.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Transactional(readOnly = true)
    public AuthResponse login(String email, String password) {
        // Lookup user across all tenants
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid credentials"));

        if (!user.isActive()) throw new RuntimeException("Account is deactivated");

        // Verify password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResourceNotFoundException("Invalid credentials");
        }

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getRole(), user.getBranchId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .userId(user.getId())
                .fullName(user.getFullName() != null ? user.getFullName() : "")
                .firstLogin(user.isFirstLogin())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }
        String userId = jwtUtil.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String newAccess = jwtUtil.generateAccessToken(
                user.getId(), user.getRole(), user.getBranchId());
        return AuthResponse.builder()
                .accessToken(newAccess)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .userId(user.getId())
                .fullName(user.getFullName() != null ? user.getFullName() : "")
                .firstLogin(user.isFirstLogin())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    @Transactional
    public void processForgotPassword(String email) {
        java.util.Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!user.isActive()) {
                log.info("Password reset requested for inactive user: {}", email);
                return;
            }
            String tempPassword = generateTempPassword(10);
            user.setPassword(passwordEncoder.encode(tempPassword));
            user.setMustChangePassword(true);
            user.setFirstLogin(true);
            userRepository.save(user);

            emailService.sendPasswordResetEmail(user, tempPassword);
        } else {
            log.info("Password reset requested for unregistered email: {}", email);
        }
    }

    private String generateTempPassword(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        java.security.SecureRandom rng = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
