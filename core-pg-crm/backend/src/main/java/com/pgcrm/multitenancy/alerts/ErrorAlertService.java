package com.pgcrm.multitenancy.alerts;

import com.pgcrm.multitenancy.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Service to dispatch critical uptime and operational alerts formatted with tenant context.
 *
 * <p><strong>Hardening Requirement (Spec Phase 7):</strong></p>
 * <ul>
 *   <li>Uptime and error alerts captured with tenant context in SLF4J MDC.</li>
 *   <li>External webhook integration (Slack/Discord/OpsGenie/PagerDuty) when configured.</li>
 *   <li>Fail-safe: Alert delivery failures never crash business request processing.</li>
 * </ul>
 */
@Service
@Slf4j
public class ErrorAlertService {

    @Value("${app.alerts.webhook-url:}")
    private String webhookUrl;

    @Value("${app.alerts.admin-email:}")
    private String adminEmail;

    /**
     * Emits a critical alert for system, database, or background job failures.
     *
     * @param component affected component (e.g. "SCHEDULER", "DATABASE", "AUTH")
     * @param message failure description
     * @param throwable optional exception
     */
    public void sendAlert(String component, String message, Throwable throwable) {
        String tenant = TenantContext.getCurrentTenantSlug();
        if (tenant == null || tenant.isBlank()) {
            tenant = "SYSTEM_LEVEL";
        }

        String exceptionDetails = throwable != null ? throwable.getClass().getSimpleName() + ": " + throwable.getMessage() : "None";
        String logMessage = String.format("[CRITICAL_ALERT] [Tenant: %s] [Component: %s] %s | Details: %s",
                tenant, component, message, exceptionDetails);

        log.error(logMessage, throwable);

        if (webhookUrl != null && !webhookUrl.isBlank()) {
            dispatchWebhook(tenant, component, message, exceptionDetails);
        }
    }

    private void dispatchWebhook(String tenant, String component, String message, String details) {
        try {
            URL url = URI.create(webhookUrl.trim()).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);

            String payload = String.format("""
                    {
                        "text": "*[PG CRM CRITICAL ALERT]*\\n*Tenant:* %s\\n*Component:* %s\\n*Message:* %s\\n*Details:* %s\\n*Timestamp:* %s"
                    }
                    """, tenant, component, message, details, LocalDateTime.now());

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                log.info("✓ Dispatched alert webhook to external monitor (status {})", responseCode);
            } else {
                log.warn("Alert webhook returned status: {}", responseCode);
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch alert webhook: {}", e.getMessage());
        }
    }
}
