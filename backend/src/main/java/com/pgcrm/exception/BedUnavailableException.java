package com.pgcrm.exception;

/**
 * Thrown when a requested bed cannot be assigned because it is not {@code VACANT}.
 *
 * <p>This exception is raised by the {@code GuestService} and {@code BedService}
 * during check-in or bed-switch operations when the target {@link com.pgcrm.entity.Bed}
 * is found to have a status of {@code OCCUPIED} or {@code MAINTENANCE} rather than
 * the required {@code VACANT} state.</p>
 *
 * <p><strong>HTTP Mapping:</strong> Caught by the
 * {@link com.pgcrm.controller.GlobalExceptionHandler#handleBedUnavailable(BedUnavailableException)}
 * handler and mapped to {@code 400 Bad Request} with a structured JSON error body.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * if (bed.getStatus() != BedStatus.VACANT) {
 *     throw new BedUnavailableException(
 *         "Bed " + bed.getBedLabel() + " is currently " + bed.getStatus() + " and cannot be assigned.");
 * }
 * }</pre>
 *
 * @see com.pgcrm.entity.enums.BedStatus
 * @see com.pgcrm.controller.GlobalExceptionHandler
 */
public class BedUnavailableException extends RuntimeException {

    /**
     * Constructs a new {@code BedUnavailableException} with the specified detail message.
     *
     * @param message a human-readable description of why the bed is unavailable,
     *                included verbatim in the {@code 400 Bad Request} API error response.
     */
    public BedUnavailableException(final String message) {
        super(message);
    }
}
