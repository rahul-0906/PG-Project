package com.pgcrm.service;

import com.pgcrm.multitenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory service for managing short-lived email address verification codes (OTP).
 *
 * <p><strong>Tenant Isolation:</strong> In accordance with Section 6 of the multi-tenancy
 * specification, the in-memory cache is keyed by {@code tenantSlug:userId}. This guarantees
 * that codes for identical user identifiers in different tenant schemas cannot overwrite,
 * leak, or cross-verify each other.</p>
 *
 * <p>Codes expire after 15 minutes and are evicted lazily upon verification attempt.</p>
 */
@Service
public class EmailVerificationService {

    /**
     * Immutable value object holding the details of a pending email verification request.
     */
    private static final class VerificationDetails {
        private final String        newEmail;
        private final String        code;
        private final LocalDateTime expiryTime;

        private VerificationDetails(final String newEmail, final String code, final LocalDateTime expiryTime) {
            this.newEmail   = newEmail;
            this.code       = code;
            this.expiryTime = expiryTime;
        }
    }

    /**
     * In-memory cache mapping {@code tenantSlug:userId → VerificationDetails}.
     */
    private final Map<String, VerificationDetails> cache = new ConcurrentHashMap<>();

    /**
     * Stores a new email verification code for the given user, replacing any
     * previously pending verification entry for the same user in the active tenant.
     *
     * @param userId   the UUID of the user requesting the email change.
     * @param newEmail the new email address the user wants to verify.
     * @param code     the one-time verification code generated and sent to {@code newEmail}.
     */
    public void storeCode(final String userId, final String newEmail, final String code) {
        String cacheKey = buildTenantKey(userId);
        cache.put(cacheKey, new VerificationDetails(newEmail, code, LocalDateTime.now().plusMinutes(15)));
    }

    /**
     * Validates the submitted verification code against the stored entry for the user
     * within the active tenant context.
     *
     * @param userId   the UUID of the user submitting the verification code.
     * @param newEmail the new email address the user claims to be verifying.
     * @param code     the verification code submitted by the user.
     * @return {@code true} if the code is valid, unexpired, and matches; {@code false} otherwise.
     */
    public boolean verifyCode(final String userId, final String newEmail, final String code) {
        String cacheKey = buildTenantKey(userId);
        final VerificationDetails details = cache.get(cacheKey);
        if (details == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(details.expiryTime)) {
            cache.remove(cacheKey);   // Lazily evict expired entry.
            return false;
        }
        if (details.newEmail.equalsIgnoreCase(newEmail) && details.code.equals(code)) {
            cache.remove(cacheKey);   // Consume the code — single-use enforcement.
            return true;
        }
        return false;
    }

    private String buildTenantKey(final String userId) {
        String tenantSlug = TenantContext.getCurrentTenantSlug();
        String prefix = (tenantSlug != null && !tenantSlug.isBlank()) ? tenantSlug : "global";
        return prefix + ":" + (userId != null ? userId.trim() : "anonymous");
    }
}
