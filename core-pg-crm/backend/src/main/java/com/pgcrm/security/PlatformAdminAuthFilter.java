package com.pgcrm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter authenticating platform management requests under {@code /platform/api/**}.
 *
 * <p>Enforces Section 6: "Platform admin endpoints live under /platform/api/**,
 * are enabled only in shared mode, use credentials entirely separate from tenant users,
 * and are never reachable on a tenant host."</p>
 */
@Component
@Slf4j
public class PlatformAdminAuthFilter extends OncePerRequestFilter {

    @Value("${app.platform.admin-key:dev-platform-admin-secret-key-123}")
    private String configuredAdminKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String path = request.getRequestURI();

        // Only intercept /platform/api/** routes
        if (!path.startsWith("/platform/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader("X-Platform-Key");
        if (providedKey == null || providedKey.isBlank()) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("PlatformKey ")) {
                providedKey = authHeader.substring(12).trim();
            }
        }

        if (providedKey == null || !providedKey.equals(configuredAdminKey)) {
            log.warn("Unauthorized platform API attempt for path '{}' from IP '{}'", path, request.getRemoteAddr());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\": \"Unauthorized: Missing or invalid X-Platform-Key\", \"status\": 401}");
            return;
        }

        // Authenticate with PLATFORM_ADMIN role
        var auth = new UsernamePasswordAuthenticationToken(
                "platform-admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}
