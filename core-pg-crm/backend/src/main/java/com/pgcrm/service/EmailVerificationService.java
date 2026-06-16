package com.pgcrm.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory service for managing short-lived email address verification codes.
 *
 * <p>When a user requests an email change, the application generates a one-time
 * verification code, sends it to the new email address via {@link EmailService},
 * and simultaneously caches it here via {@link #storeCode(String, String, String)}.
 * The user then submits the code back via the verification endpoint, which calls
 * {@link #verifyCode(String, String, String)} to validate and consume it.</p>
 *
 * <p><strong>Expiry:</strong> Codes expire after 15 minutes. Expired codes are lazily
 * evicted: the expiry is checked at verification time and the entry is removed from the
 * cache if found to be stale. No background sweep is performed.</p>
 *
 * <p><strong>Thread Safety:</strong> The internal cache is a {@link ConcurrentHashMap},
 * making {@code storeCode} and {@code verifyCode} safe for concurrent access in a
 * multi-threaded servlet environment.</p>
 *
 * <p><strong>Single-Node Limitation:</strong> This implementation is in-memory and
 * therefore not safe for clustered / multi-pod deployments. For horizontal scaling,
 * replace the {@link ConcurrentHashMap} with a distributed cache (e.g., Redis via
 * Spring Data Redis) and persist codes with a TTL matching the 15-minute window.</p>
 *
 * @see EmailService#sendEmailVerificationCode(String, String, String)
 */
@Service
public class EmailVerificationService {

    /**
     * Immutable value object holding the details of a pending email verification request.
     */
    private static final class VerificationDetails {

        /** The new email address the user wants to switch to. */
        private final String        newEmail;

        /** The one-time verification code sent to {@code newEmail}. */
        private final String        code;

        /** The absolute timestamp after which this code is considered expired. */
        private final LocalDateTime expiryTime;

        /**
         * Constructs a new {@link VerificationDetails} instance.
         *
         * @param newEmail   the target email address being verified.
         * @param code       the one-time verification code.
         * @param expiryTime the absolute expiry timestamp for this code.
         */
        private VerificationDetails(final String newEmail, final String code, final LocalDateTime expiryTime) {
            this.newEmail   = newEmail;
            this.code       = code;
            this.expiryTime = expiryTime;
        }
    }

    /**
     * In-memory cache mapping {@code userId → VerificationDetails}.
     * Only one pending verification entry is held per user at a time; a new call to
     * {@link #storeCode} overwrites any previously pending entry for the same user.
     */
    private final Map<String, VerificationDetails> cache = new ConcurrentHashMap<>();

    /**
     * Stores a new email verification code for the given user, replacing any
     * previously pending verification entry for the same user.
     *
     * <p>The code expires 15 minutes after this call. If the user requests a new
     * code within the 15-minute window, the old code is atomically replaced and
     * becomes immediately invalid.</p>
     *
     * @param userId   the UUID of the user requesting the email change.
     * @param newEmail the new email address the user wants to verify.
     * @param code     the one-time verification code generated and sent to {@code newEmail}.
     */
    public void storeCode(final String userId, final String newEmail, final String code) {
        cache.put(userId, new VerificationDetails(newEmail, code, LocalDateTime.now().plusMinutes(15)));
    }

    /**
     * Validates the submitted verification code against the stored entry for the user.
     *
     * <p>The verification succeeds only if all three conditions hold simultaneously:</p>
     * <ol>
     *   <li>A pending entry exists for the given {@code userId}.</li>
     *   <li>The stored entry has not yet expired (current time is before {@code expiryTime}).</li>
     *   <li>Both {@code newEmail} (case-insensitive) and {@code code} (case-sensitive) match.</li>
     * </ol>
     *
     * <p>On success, the entry is removed from the cache to enforce single-use semantics.
     * On expiry, the entry is also removed. On a code mismatch with a valid (unexpired)
     * entry, the entry is <em>not</em> removed — this allows the user to retry if they
     * made a typo, until the code expires.</p>
     *
     * @param userId   the UUID of the user submitting the verification code.
     * @param newEmail the new email address the user claims to be verifying.
     * @param code     the verification code submitted by the user.
     * @return {@code true} if the code is valid, unexpired, and matches; {@code false} otherwise.
     */
    public boolean verifyCode(final String userId, final String newEmail, final String code) {
        final VerificationDetails details = cache.get(userId);
        if (details == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(details.expiryTime)) {
            cache.remove(userId);   // Lazily evict expired entry.
            return false;
        }
        if (details.newEmail.equalsIgnoreCase(newEmail) && details.code.equals(code)) {
            cache.remove(userId);   // Consume the code — single-use enforcement.
            return true;
        }
        return false;
    }
}
