package com.pgcrm.multitenancy;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * MultiTenantConnectionProvider performing per-connection tenant switching via PostgreSQL's
 * {@code SET search_path TO <schema>, public}.
 *
 * <p><strong>Tenant Isolation Contract:</strong></p>
 * <ul>
 *   <li>Single Hikari connection pool is shared across all tenants.</li>
 *   <li>On connection acquisition for a tenant, schema is set to {@code <tenantSchema>, public}.</li>
 *   <li>On connection release in shared mode, schema is reset to {@code platform, public} before returning
 *       to the pool to eliminate cross-tenant connection leakage.</li>
 *   <li>In dedicated mode, connection defaults and resets to {@code t_<dedicatedTenant>, public}.</li>
 *   <li>Schema names are strictly validated against {@code ^[a-z][a-z0-9_]{2,40}$} prior to execution.</li>
 * </ul>
 */
@Component
@Slf4j
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;
    private final DeploymentProperties deploymentProperties;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource, DeploymentProperties deploymentProperties) {
        this.dataSource = dataSource;
        this.deploymentProperties = deploymentProperties;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        resetSearchPath(connection);
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        try {
            resetSearchPath(connection);
        } finally {
            connection.close();
        }
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        if (tenantIdentifier != null && !tenantIdentifier.isBlank()) {
            TenantContext.validateIdentifier("schema_name", tenantIdentifier);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET search_path TO " + tenantIdentifier + ", public");
            } catch (SQLException e) {
                connection.close();
                log.error("Failed to switch search_path to '{}': {}", tenantIdentifier, e.getMessage());
                throw e;
            }
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            resetSearchPath(connection);
        } finally {
            connection.close();
        }
    }

    private void resetSearchPath(Connection connection) {
        String defaultPath = deploymentProperties.isDedicated()
                ? "t_" + deploymentProperties.getDedicatedTenant() + ", public"
                : "platform, public";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO " + defaultPath);
        } catch (SQLException e) {
            log.warn("Failed to reset connection search_path: {}", e.getMessage());
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <U> U unwrap(Class<U> unwrapType) {
        throw new UnsupportedOperationException("Cannot unwrap to " + unwrapType);
    }
}

