package com.pgcrm.multitenancy;

import java.util.regex.Pattern;

/**
 * ThreadLocal-backed holder for the current request's tenant context.
 *
 * <p>Set by {@code TenantResolutionFilter} before entering security/controllers,
 * and cleared in a {@code finally} block upon request completion.</p>
 */
public final class TenantContext {

    /**
     * Regex pattern enforcing slug and schema_name validity:
     * Starts with lowercase letter, followed by 2 to 40 lowercase alphanumeric or underscore characters.
     */
    public static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,40}$");

    /**
     * Immutable representation of an active tenant context.
     */
    public record TenantInfo(
            String id,
            String slug,
            String schemaName,
            String displayName,
            String status
    ) {
        public TenantInfo {
            validateIdentifier("slug", slug);
            validateIdentifier("schemaName", schemaName);
        }
    }

    private static final ThreadLocal<TenantInfo> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenant(TenantInfo tenant) {
        if (tenant != null) {
            validateIdentifier("slug", tenant.slug());
            validateIdentifier("schemaName", tenant.schemaName());
        }
        CURRENT_TENANT.set(tenant);
    }

    public static void setCurrentTenant(String slug, String schemaName) {
        if (slug == null || schemaName == null) {
            clear();
        } else {
            setTenant(new TenantInfo(slug, slug, schemaName, slug, "ACTIVE"));
        }
    }

    public static TenantInfo getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static String getCurrentTenantSlug() {
        TenantInfo info = CURRENT_TENANT.get();
        return info != null ? info.slug() : null;
    }

    public static String getCurrentTenantSchema() {
        TenantInfo info = CURRENT_TENANT.get();
        return info != null ? info.schemaName() : null;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Validates that an identifier matches ^[a-z][a-z0-9_]{2,40}$.
     * Prevents SQL injection when constructing search_path.
     *
     * @param fieldName identifier description for exception messages
     * @param value     string value to check
     * @throws IllegalArgumentException if invalid
     */
    public static void validateIdentifier(String fieldName, String value) {
        if (value == null || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid " + fieldName + ": '" + value + "'. Must match pattern ^[a-z][a-z0-9_]{2,40}$"
            );
        }
    }
}
