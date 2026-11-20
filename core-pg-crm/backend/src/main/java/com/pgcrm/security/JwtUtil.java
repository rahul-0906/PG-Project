package com.pgcrm.security;

import com.pgcrm.entity.enums.Role;
import com.pgcrm.multitenancy.TenantContext;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for issuing and validating JSON Web Tokens (JWT).
 *
 * <p><strong>Multi-Tenancy Specification:</strong></p>
 * <ul>
 *   <li>One global JWT signing secret from {@code app.jwt.secret}.</li>
 *   <li>Every token carries a {@code tenant} claim identifying the tenant slug.</li>
 *   <li>Cross-tenant tokens are rejected at the security filter layer.</li>
 * </ul>
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String userId, Role role, String branchId) {
        String tenantSlug = TenantContext.getCurrentTenantSlug();
        return generateAccessToken(userId, role, branchId, tenantSlug != null ? tenantSlug : "default");
    }

    public String generateAccessToken(String userId, Role role, String branchId, String tenantSlug) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role.name());
        claims.put("tenant", tenantSlug);
        if (branchId != null) claims.put("branch_id", branchId);

        return Jwts.builder()
                .claims(claims)
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String userId) {
        String tenantSlug = TenantContext.getCurrentTenantSlug();
        return generateRefreshToken(userId, tenantSlug != null ? tenantSlug : "default");
    }

    public String generateRefreshToken(String userId, String tenantSlug) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tenant", tenantSlug);
        return Jwts.builder()
                .claims(claims)
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractTenantSlug(String token) {
        Claims claims = extractAllClaims(token);
        String tenant = claims.get("tenant", String.class);
        if (tenant == null) {
            tenant = claims.get("tenant_id", String.class);
        }
        return tenant;
    }

    public String extractTenantId(String token) {
        return extractTenantSlug(token);
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public String extractBranchId(String token) {
        return extractAllClaims(token).get("branch_id", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
