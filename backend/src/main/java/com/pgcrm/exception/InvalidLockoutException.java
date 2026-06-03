package com.pgcrm.exception;

/**
 * Thrown when a guest attempts to modify a meal opt-in or opt-out after
 * the configured daily lockout (cutoff) time has passed.
 *
 * <p>This exception is raised by the {@code DailyLogService} when a guest
 * submits a meal preference change (breakfast, lunch, or dinner opt-in/opt-out)
 * for a date whose corresponding cutoff window has already closed according to
 * {@link com.pgcrm.entity.BuildingConfig#getBreakfastCutoffTime()} or
 * {@link com.pgcrm.entity.BuildingConfig#getDinnerCutoffTime()}.</p>
 *
 * <p><strong>Lockout Model:</strong> In the default arrears-billing model
 * ({@link com.pgcrm.entity.BuildingConfig#isPreviousDay()} = {@code true}),
 * the breakfast cutoff on the current evening governs tomorrow's breakfast booking.
 * After the cutoff time passes, further changes to that day's log are blocked
 * to ensure kitchen preparation accuracy and billing consistency.</p>
 *
 * <p><strong>HTTP Mapping:</strong> Caught by the
 * {@link com.pgcrm.controller.GlobalExceptionHandler#handleInvalidLockout(InvalidLockoutException)}
 * handler and mapped to {@code 400 Bad Request} with a structured JSON error body.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * if (LocalTime.now().isAfter(config.getBreakfastCutoffTime())) {
 *     throw new InvalidLockoutException(
 *         "Breakfast opt-in is locked after " + config.getBreakfastCutoffTime() + ". "
 *         + "Changes for tomorrow are no longer accepted.");
 * }
 * }</pre>
 *
 * @see com.pgcrm.entity.BuildingConfig
 * @see com.pgcrm.controller.GlobalExceptionHandler
 */
public class InvalidLockoutException extends RuntimeException {

    /**
     * Constructs a new {@code InvalidLockoutException} with the specified detail message.
     *
     * @param message a human-readable description identifying which meal slot is locked
     *                and the cutoff time that was exceeded, included verbatim in the
     *                {@code 400 Bad Request} API error response.
     */
    public InvalidLockoutException(final String message) {
        super(message);
    }
}
