package com.pgcrm.exception;

/**
 * Thrown when a requested domain entity cannot be located in the database.
 *
 * <p>This is the application's primary domain-not-found exception, used across
 * all service classes whenever a repository lookup by ID or unique key returns
 * an empty result. It replaces raw {@link java.util.NoSuchElementException} or
 * Spring's {@code EmptyResultDataAccessException} with a semantically meaningful,
 * application-scoped exception that the global handler maps to a clean HTTP response.</p>
 *
 * <p><strong>HTTP Mapping:</strong> Caught by the
 * {@link com.pgcrm.controller.GlobalExceptionHandler#handleResourceNotFound(ResourceNotFoundException)}
 * handler and mapped to {@code 404 Not Found} with a structured JSON error body.</p>
 *
 * <p><strong>Typical Callers:</strong></p>
 * <ul>
 *   <li>{@code GuestService} — guest not found by ID.</li>
 *   <li>{@code BedService} — bed not found by ID.</li>
 *   <li>{@code InvoiceService} — invoice not found by ID or guest/period lookup.</li>
 *   <li>{@code BuildingService} — building not found by ID.</li>
 *   <li>{@code MaintenanceService} — ticket not found by ID.</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * Guest guest = guestRepository.findById(guestId)
 *     .orElseThrow(() -> new ResourceNotFoundException(
 *         "Guest not found with id: " + guestId));
 * }</pre>
 *
 * @see com.pgcrm.controller.GlobalExceptionHandler
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Constructs a new {@code ResourceNotFoundException} with the specified detail message.
     *
     * @param message a human-readable description identifying the resource type and
     *                lookup key that could not be resolved, included verbatim in the
     *                {@code 404 Not Found} API error response.
     */
    public ResourceNotFoundException(final String message) {
        super(message);
    }
}
