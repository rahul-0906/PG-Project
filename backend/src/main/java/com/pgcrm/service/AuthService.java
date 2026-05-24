package com.pgcrm.service;

import com.pgcrm.entity.*;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.repository.*;
import com.pgcrm.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Transactional(readOnly = true)
    public Map<String, String> login(String email, String password) {
        // Lookup user across all tenants
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!user.isActive()) throw new RuntimeException("Account is deactivated");

        // Verify password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getRole(), user.getBranchId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        return Map.of(
                "accessToken",        accessToken,
                "refreshToken",       refreshToken,
                "role",               user.getRole().name(),
                "userId",             user.getId(),

                "firstLogin",         String.valueOf(user.isFirstLogin()),
                "mustChangePassword", String.valueOf(user.isMustChangePassword())
        );
    }

    @Transactional
    public Map<String, String> refresh(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }
        String userId = jwtUtil.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newAccess = jwtUtil.generateAccessToken(
                user.getId(), user.getRole(), user.getBranchId());
        return Map.of("accessToken", newAccess);
    }
}
