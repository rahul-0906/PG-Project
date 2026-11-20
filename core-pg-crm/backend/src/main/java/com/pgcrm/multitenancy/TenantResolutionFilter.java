package com.pgcrm.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Filter resolving the active tenant context prior to Spring Security evaluation.
 *
 * <p><strong>Rules:</strong></p>
 * <ul>
 *   <li>Runs with highest precedence so that {@code TenantContext} and {@code MDC} are available
 *       to downstream security filters and controllers.</li>
 *   <li>Public endpoints (e.g. {@code /api/auth/login}, {@code /api/config/public}) MUST resolve a tenant
 *       because branding, auth credentials, and settings are per tenant.</li>
 *   <li>In {@code DEDICATED} mode, ignores the request host and resolves {@code app.dedicated-tenant}.</li>
 *   <li>In {@code SHARED} mode, resolves by request host (subdomain or mapped custom domain).</li>
 *   <li>Fail-closed: returns 404 for unknown tenant host, 403 for suspended or inactive tenant.</li>
 *   <li>Platform admin endpoints ({@code /platform/api/**}) are enabled ONLY in shared mode and never on a tenant host.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@Slf4j
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final DeploymentProperties deploymentProperties;
    private final TenantRegistryService tenantRegistryService;

    public TenantResolutionFilter(DeploymentProperties deploymentProperties, TenantRegistryService tenantRegistryService) {
        this.deploymentProperties = deploymentProperties;
        this.tenantRegistryService = tenantRegistryService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String path = request.getRequestURI();
        final String rawHost = request.getHeader("Host");

        // 1. Bypass static assets and Swagger/OpenAPI documentation
        if (isStaticOrDocsPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Handle Platform Admin API endpoints (/platform/api/**)
        if (path.startsWith("/platform/api/")) {
            if (deploymentProperties.isDedicated()) {
                writeErrorResponse(response, HttpStatus.NOT_FOUND, "Platform admin API is not available in dedicated mode");
                return;
            }

            // In shared mode: ensure request is hitting platform host / base domain, not a tenant host
            if (isTenantSubdomain(rawHost)) {
                writeErrorResponse(response, HttpStatus.NOT_FOUND, "Platform admin API is not reachable on a tenant host");
                return;
            }

            // Platform admin requests run on platform context
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Resolve Tenant Context for business routes (/api/**)
        TenantContext.TenantInfo tenantInfo;

        if (deploymentProperties.isDedicated()) {
            String dedicatedSlug = deploymentProperties.getDedicatedTenant();
            Optional<TenantContext.TenantInfo> opt = tenantRegistryService.findTenantBySlug(dedicatedSlug);
            if (opt.isEmpty()) {
                log.error("Dedicated tenant '{}' not found in platform registry!", dedicatedSlug);
                writeErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, "Dedicated tenant configuration error");
                return;
            }
            tenantInfo = opt.get();
        } else {
            // SHARED mode — resolve by host
            if (rawHost == null || rawHost.isBlank()) {
                writeErrorResponse(response, HttpStatus.BAD_REQUEST, "Missing Host header for tenant resolution");
                return;
            }

            Optional<TenantContext.TenantInfo> opt = tenantRegistryService.resolveTenantByHost(rawHost);
            if (opt.isEmpty()) {
                log.warn("No tenant found for host '{}' on request path '{}'", rawHost, path);
                writeErrorResponse(response, HttpStatus.NOT_FOUND, "Tenant not found for host: " + rawHost);
                return;
            }
            tenantInfo = opt.get();
        }

        // 4. Status Check — Fail Closed
        if ("SUSPENDED".equalsIgnoreCase(tenantInfo.status())) {
            log.warn("Access attempt to SUSPENDED tenant '{}' ({})", tenantInfo.slug(), tenantInfo.id());
            writeErrorResponse(response, HttpStatus.FORBIDDEN, "Tenant account is suspended");
            return;
        }

        if (!"ACTIVE".equalsIgnoreCase(tenantInfo.status())) {
            log.warn("Access attempt to inactive tenant '{}' (status: {})", tenantInfo.slug(), tenantInfo.status());
            writeErrorResponse(response, HttpStatus.FORBIDDEN, "Tenant account is not active");
            return;
        }

        // 5. Bind context and proceed
        try {
            TenantContext.setTenant(tenantInfo);
            MDC.put("tenant", tenantInfo.slug());
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenant");
        }
    }

    private boolean isStaticOrDocsPath(String path) {
        return path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api-docs")
                || path.endsWith(".js")
                || path.endsWith(".css")
                || path.endsWith(".ico")
                || path.endsWith(".png")
                || path.endsWith(".json")
                || path.endsWith(".webmanifest");
    }

    private boolean isTenantSubdomain(String rawHost) {
        if (rawHost == null) return false;
        String host = rawHost.trim().toLowerCase();
        int colonIdx = host.indexOf(':');
        if (colonIdx > 0) host = host.substring(0, colonIdx);

        String baseDomain = deploymentProperties.getBaseDomain().toLowerCase();
        if (host.endsWith("." + baseDomain)) {
            String sub = host.substring(0, host.length() - baseDomain.length() - 1);
            return !sub.equalsIgnoreCase("admin") && !sub.equalsIgnoreCase("platform");
        }
        return false;
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format("{\"error\": \"%s\", \"status\": %d}", message, status.value()));
    }
}
