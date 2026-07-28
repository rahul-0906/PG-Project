package com.pgcrm.controller;

import com.pgcrm.exception.BedUnavailableException;
import com.pgcrm.exception.DuplicateEmailException;
import com.pgcrm.exception.InvalidLockoutException;
import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.exception.SignatureVerificationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Centralised exception-to-HTTP-response mapping layer for the PG CRM REST API.
 *
 * <p>This class is the single point of truth for how all application exceptions are
 * translated into structured JSON error responses. It uses Spring's
 * {@link RestControllerAdvice @RestControllerAdvice} to intercept exceptions thrown
 * from any {@code @RestController} in the application context and produce consistent,
 * machine-readable error payloads without cluttering individual controller methods
 * with repetitive try/catch blocks.</p>
 *
 * <p><strong>Error Response Shape:</strong> Every handler delegates to the private
 * {@link #error(String, int)} factory method, which produces a uniform JSON object:</p>
 * <pre>{@code
 * {
 *   "error":     "Human-readable message describing what went wrong",
 *   "status":    400,
 *   "timestamp": "2026-06-03T11:42:00.123456"
 * }
 * }</pre>
 *
 * <p><strong>Handler Priority:</strong> Spring evaluates {@code @ExceptionHandler} methods
 * from most-specific to least-specific exception type. The ordering below reflects this
 * specificity — domain exceptions ({@code 4xx} errors) are handled before the catch-all
 * {@link RuntimeException} and {@link Exception} handlers ({@code 500}).</p>
 *
 * <p><strong>Security Note:</strong> The {@link #handleGeneral(Exception)} handler
 * includes the raw exception message in the response for developer visibility during
 * development. In a production hardening pass, consider replacing this with a generic
 * {@code "An unexpected error occurred."} message and routing the raw detail to a
 * structured log sink only.</p>
 *
 * @see ResourceNotFoundException
 * @see BedUnavailableException
 * @see InvalidLockoutException
 * @see SignatureVerificationException
 * @see DuplicateEmailException
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles {@link ResourceNotFoundException} — thrown when a requested domain
     * entity cannot be located in the database.
     *
     * @param ex the exception carrying the not-found detail message.
     * @return {@code 404 Not Found} with a structured error body.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(
            final ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error(ex.getMessage(), 404));
    }

    /**
     * Handles {@link BedUnavailableException} — thrown when a check-in or bed-switch
     * operation targets a bed that is {@code OCCUPIED} or {@code MAINTENANCE}.
     *
     * @param ex the exception carrying the unavailability detail message.
     * @return {@code 400 Bad Request} with a structured error body.
     */
    @ExceptionHandler(BedUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleBedUnavailable(
            final BedUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error(ex.getMessage(), 400));
    }

    /**
     * Handles {@link InvalidLockoutException} — thrown when a guest attempts to
     * modify a meal opt-in after the configured daily cutoff time has passed.
     *
     * @param ex the exception carrying the lockout detail message.
     * @return {@code 400 Bad Request} with a structured error body.
     */
    @ExceptionHandler(InvalidLockoutException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidLockout(
            final InvalidLockoutException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error(ex.getMessage(), 400));
    }

    /**
     * Handles {@link SignatureVerificationException} — thrown when a Razorpay webhook
     * payload fails HMAC-SHA256 signature verification, indicating a potentially
     * tampered or untrusted request.
     *
     * @param ex the exception carrying the signature failure detail message.
     * @return {@code 400 Bad Request} with a structured error body.
     */
    @ExceptionHandler(SignatureVerificationException.class)
    public ResponseEntity<Map<String, Object>> handleSignatureVerification(
            final SignatureVerificationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error(ex.getMessage(), 400));
    }

    /**
     * Handles {@link DuplicateEmailException} — thrown when an account creation
     * attempt is made with an email address that already exists in the system.
     *
     * @param ex the exception carrying the duplicate-email detail message.
     * @return {@code 400 Bad Request} with a structured error body.
     */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateEmail(
            final DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error(ex.getMessage(), 400));
    }

    /**
     * Handles Spring MVC {@link org.springframework.web.bind.MethodArgumentNotValidException} — thrown when DTO
     * validation annotations (e.g., {@code @NotBlank}, {@code @Email}) fail during request processing.
     *
     * @param ex the validation exception carrying field errors.
     * @return {@code 400 Bad Request} with a structured error body describing the validation error.
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            final org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error(msg, 400));
    }

    /**
     * Handles {@link IllegalArgumentException} and {@link IllegalStateException} — thrown when bad arguments
     * or disallowed state transitions are attempted.
     *
     * @param ex the exception carrying the detail message.
     * @return {@code 400 Bad Request} with a structured error body.
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentOrState(final RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error(ex.getMessage(), 400));
    }

    /**
     * Catch-all handler for any {@link RuntimeException} not covered by a more
     * specific handler above.
     *
     * @param ex the unhandled runtime exception.
     * @return {@code 400 Bad Request} with a structured error body containing the
     *         exception's message.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(final RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(error(ex.getMessage(), 400));
    }

    /**
     * Handles Spring Security's {@link AccessDeniedException}, thrown when an
     * authenticated user attempts to access a resource or operation that their
     * {@link com.pgcrm.entity.enums.Role} does not permit.
     *
     * @param ex the access denied exception (message suppressed in the response).
     * @return {@code 403 Forbidden} with a generic structured error body.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            final AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("Access denied", 403));
    }

    /**
     * Final catch-all handler for any checked or unchecked {@link Exception} not
     * intercepted by the handlers above.
     *
     * @param ex the unexpected exception.
     * @return {@code 500 Internal Server Error} with a structured error body.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(final Exception ex) {
        return ResponseEntity.internalServerError()
                .body(error("Internal server error: " + ex.getMessage(), 500));
    }

    /**
     * Private factory method that produces the standard PG CRM API error response body.
     *
     * <p>Returns an immutable {@link Map} with consistent keys across all error responses:</p>
     * <ul>
     *   <li>{@code "error"} — the human-readable error description.</li>
     *   <li>{@code "message"} — duplicate alias for frontend compatibility.</li>
     *   <li>{@code "status"} — the integer HTTP status code.</li>
     *   <li>{@code "timestamp"} — ISO-8601 datetime string.</li>
     * </ul>
     *
     * @param message the error description to include in the response body.
     * @param status  the HTTP status code integer to echo in the body.
     * @return an immutable {@link Map} conforming to the standard API error shape.
     */
    private Map<String, Object> error(final String message, final int status) {
        String safeMsg = (message != null && !message.isBlank()) ? message : "An unexpected error occurred.";
        return Map.of(
                "error",     safeMsg,
                "message",   safeMsg,
                "status",    status,
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
