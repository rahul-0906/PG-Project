package com.pgcrm.multitenancy.ratelimit;

import com.pgcrm.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filter enforcing per-tenant rate limiting using a token-bucket algorithm.
 *
 * <p><strong>Hardening Guarantee (Spec Phase 7):</strong></p>
 * <ul>
 *   <li>Runs with {@code @Order(Ordered.HIGHEST_PRECEDENCE + 10)}, immediately after {@code TenantResolutionFilter}.</li>
 *   <li>Every tenant has an isolated token bucket. One tenant's traffic spike never impacts others.</li>
 *   <li>Exceeded limits return HTTP 429 Too Many Requests with {@code Retry-After} and {@code X-RateLimit-*} headers.</li>
 *   <li>Excludes static assets and healthcheck endpoints.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class TenantRateLimitingFilter extends OncePerRequestFilter {

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.rate-limit.requests-per-minute:180}")
    private int requestsPerMinute;

    private final ConcurrentHashMap<String, TokenBucket> tenantBuckets = new ConcurrentHashMap<>();

    public TenantRateLimitingFilter() {}

    public TenantRateLimitingFilter(boolean enabled, int requestsPerMinute) {
        this.enabled = enabled;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (isExemptPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String tenantSlug = TenantContext.getCurrentTenantSlug();
        String bucketKey = (tenantSlug != null && !tenantSlug.isBlank()) ? tenantSlug : "global";

        TokenBucket bucket = tenantBuckets.computeIfAbsent(bucketKey, k -> new TokenBucket(requestsPerMinute));

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for tenant '{}' on path '{}'", bucketKey, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "30");
            response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
            response.setHeader("X-RateLimit-Remaining", "0");

            String json = String.format("""
                    {
                        "status": 429,
                        "error": "Too Many Requests",
                        "message": "Rate limit exceeded for tenant '%s'. Please retry later.",
                        "tenant": "%s",
                        "retryAfterSeconds": 30
                    }
                    """, bucketKey, bucketKey);
            response.getWriter().write(json);
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
        response.setHeader("X-RateLimit-Remaining", String.valueOf((int) bucket.getTokens()));

        filterChain.doFilter(request, response);
    }

    private boolean isExemptPath(String path) {
        if (path == null) return false;
        return path.equals("/api/health") ||
                path.startsWith("/actuator") ||
                path.startsWith("/assets") ||
                path.startsWith("/static") ||
                path.endsWith(".js") ||
                path.endsWith(".css") ||
                path.endsWith(".png") ||
                path.endsWith(".ico") ||
                path.endsWith(".svg");
    }

    public void resetBuckets() {
        tenantBuckets.clear();
    }

    /**
     * Thread-safe Token Bucket for tenant rate limiting.
     */
    public static class TokenBucket {
        private final int capacity;
        private double tokens;
        private long lastRefillNano;

        public TokenBucket(int capacity) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillNano = System.nanoTime();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        public synchronized double getTokens() {
            refill();
            return tokens;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNano) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                double tokensToAdd = elapsedSeconds * (capacity / 60.0);
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillNano = now;
            }
        }
    }
}
