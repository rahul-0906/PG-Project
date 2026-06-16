package com.pgcrm.exception;

/**
 * Thrown when a Razorpay webhook signature cannot be verified against
 * the expected HMAC-SHA256 signature.
 *
 * <p>This exception is raised by the {@code PaymentService} (or its Razorpay
 * integration component) when processing an incoming Razorpay payment webhook.
 * The Razorpay gateway signs each webhook payload using HMAC-SHA256 with the
 * webhook secret configured in {@code application.yml}. If the computed signature
 * does not match the {@code X-Razorpay-Signature} header value, this exception
 * is thrown to reject the request and prevent unauthorised payment confirmations
 * from being processed.</p>
 *
 * <p><strong>Security Contract:</strong> Failing this check means the webhook
 * request is either malformed, tampered with, or originated from an untrusted source.
 * Under no circumstances should an invoice be marked as paid when this exception
 * is thrown. The rejection is logged at {@code WARN} level for security audit purposes.</p>
 *
 * <p><strong>HTTP Mapping:</strong> Caught by the
 * {@link com.pgcrm.controller.GlobalExceptionHandler#handleSignatureVerification(SignatureVerificationException)}
 * handler and mapped to {@code 400 Bad Request} with a structured JSON error body.
 * Note: {@code 400} is returned (rather than {@code 401}) because the webhook endpoint
 * is not authenticated via JWT; the HMAC signature <em>is</em> the authentication mechanism.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * String expectedSignature = hmacSha256(razorpayOrderId + "|" + razorpayPaymentId, webhookSecret);
 * if (!expectedSignature.equals(receivedSignature)) {
 *     throw new SignatureVerificationException(
 *         "Razorpay webhook signature mismatch. Request rejected.");
 * }
 * }</pre>
 *
 * @see com.pgcrm.controller.GlobalExceptionHandler
 */
public class SignatureVerificationException extends RuntimeException {

    /**
     * Constructs a new {@code SignatureVerificationException} with the specified detail message.
     *
     * @param message a human-readable description of the signature failure, included
     *                verbatim in the {@code 400 Bad Request} API error response.
     *                <strong>Must not include</strong> the raw signature values or
     *                webhook secret to avoid leaking cryptographic material in logs.
     */
    public SignatureVerificationException(final String message) {
        super(message);
    }
}
