package com.pgcrm.multitenancy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Service managing access to the platform registry schema ({@code platform.tenants}
 * and {@code platform.tenant_domains}).
 *
 * <p>Results are cached with a short TTL (60 seconds) to ensure sub-millisecond host
 * resolution on web requests while reflecting tenant status updates rapidly.</p>
 */
@Service
@Slf4j
public class TenantRegistryService {

    private final JdbcTemplate jdbcTemplate;
    private final DeploymentProperties deploymentProperties;

    /**
     * Short-TTL cache: host -> Optional<TenantInfo>.
     */
    private final Cache<String, Optional<TenantContext.TenantInfo>> hostCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(1000)
            .build();

    /**
     * Short-TTL cache: slug -> Optional<TenantInfo>.
     */
    private final Cache<String, Optional<TenantContext.TenantInfo>> slugCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(1000)
            .build();

    public TenantRegistryService(JdbcTemplate jdbcTemplate, DeploymentProperties deploymentProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.deploymentProperties = deploymentProperties;
    }

    /**
     * Resolves a tenant by request host header.
     *
     * <ol>
     *   <li>Strip port if present (e.g., "sri-sai.pgcrm.com:8080" -> "sri-sai.pgcrm.com").</li>
     *   <li>Check if subdomain of app.base-domain (e.g. "sri-sai.pgcrm.com" -> slug "sri-sai").</li>
     *   <li>If not subdomain, look up custom domain in platform.tenant_domains.</li>
     * </ol>
     *
     * @param rawHost HTTP Host header
     * @return Optional containing TenantInfo if found, empty otherwise
     */
    public Optional<TenantContext.TenantInfo> resolveTenantByHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            return Optional.empty();
        }

        String host = rawHost.trim().toLowerCase();
        int colonIdx = host.indexOf(':');
        if (colonIdx > 0) {
            host = host.substring(0, colonIdx);
        }

        return hostCache.get(host, this::lookupTenantByHost);
    }

    /**
     * Finds a tenant directly by slug.
     *
     * @param slug tenant slug matching ^[a-z][a-z0-9_]{2,40}$
     * @return Optional containing TenantInfo if found
     */
    public Optional<TenantContext.TenantInfo> findTenantBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        String cleanSlug = slug.trim().toLowerCase();
        return slugCache.get(cleanSlug, this::lookupTenantBySlug);
    }

    /**
     * Evicts cached tenant entries for a given host and slug.
     */
    public void evict(String host, String slug) {
        if (host != null) hostCache.invalidate(host.toLowerCase());
        if (slug != null) slugCache.invalidate(slug.toLowerCase());
    }

    private Optional<TenantContext.TenantInfo> lookupTenantByHost(String host) {
        String baseDomain = deploymentProperties.getBaseDomain().toLowerCase();

        // 1. Check subdomain of base domain (e.g. "tenant.baseDomain")
        if (host.endsWith("." + baseDomain)) {
            String potentialSlug = host.substring(0, host.length() - baseDomain.length() - 1);
            if (TenantContext.IDENTIFIER_PATTERN.matcher(potentialSlug).matches()) {
                Optional<TenantContext.TenantInfo> tenant = findTenantBySlug(potentialSlug);
                if (tenant.isPresent()) {
                    return tenant;
                }
            }
        }

        // 2. Check custom domain mapping in platform.tenant_domains
        final String sql = """
                SELECT t.id, t.slug, t.schema_name, t.display_name, t.status
                FROM platform.tenants t
                JOIN platform.tenant_domains d ON t.id = d.tenant_id
                WHERE LOWER(d.host) = LOWER(?)
                """;

        try {
            TenantContext.TenantInfo info = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new TenantContext.TenantInfo(
                    rs.getString("id"),
                    rs.getString("slug"),
                    rs.getString("schema_name"),
                    rs.getString("display_name"),
                    rs.getString("status")
            ), host);
            return Optional.ofNullable(info);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to query platform.tenant_domains for host '{}': {}", host, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<TenantContext.TenantInfo> lookupTenantBySlug(String slug) {
        if (!TenantContext.IDENTIFIER_PATTERN.matcher(slug).matches()) {
            return Optional.empty();
        }

        final String sql = """
                SELECT id, slug, schema_name, display_name, status
                FROM platform.tenants
                WHERE slug = ?
                """;

        try {
            TenantContext.TenantInfo info = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new TenantContext.TenantInfo(
                    rs.getString("id"),
                    rs.getString("slug"),
                    rs.getString("schema_name"),
                    rs.getString("display_name"),
                    rs.getString("status")
            ), slug);
            return Optional.ofNullable(info);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to query platform.tenants for slug '{}': {}", slug, e.getMessage());
            return Optional.empty();
        }
    }
}
