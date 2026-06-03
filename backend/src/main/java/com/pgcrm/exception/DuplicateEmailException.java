package com.pgcrm.exception;

/**
 * Thrown when an attempt is made to create a user account with an email address
 * that already exists in the system.
 *
 * <p>This exception is raised by the {@code GuestService} and {@code UserService}
 * during account creation or manager provisioning when the supplied email is found
 * to already be registered in the {@code users} table. Since the {@code email}
 * column carries a database-level unique constraint, this guard is applied at the
 * service layer <em>before</em> the database flush to produce a clean, user-friendly
 * error rather than a raw {@code DataIntegrityViolationException}.</p>
 *
 * <p><strong>HTTP Mapping:</strong> Caught by the
 * {@link com.pgcrm.controller.GlobalExceptionHandler#handleDuplicateEmail(DuplicateEmailException)}
 * handler and mapped to {@code 400 Bad Request} with a structured JSON error body.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * if (userRepository.existsByEmail(request.getEmail())) {
 *     throw new DuplicateEmailException(
 *         "An account with email '" + request.getEmail() + "' already exists.");
 * }
 * }</pre>
 *
 * @see com.pgcrm.entity.User
 * @see com.pgcrm.controller.GlobalExceptionHandler
 */
public class DuplicateEmailException extends RuntimeException {

    /**
     * Constructs a new {@code DuplicateEmailException} with the specified detail message.
     *
     * @param message a human-readable description indicating which email caused the conflict,
     *                included verbatim in the {@code 400 Bad Request} API error response.
     */
    public DuplicateEmailException(final String message) {
        super(message);
    }
}
