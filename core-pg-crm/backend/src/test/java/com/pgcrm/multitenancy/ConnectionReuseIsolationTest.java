package com.pgcrm.multitenancy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies Section 10 requirement:
 * "Connection reuse: after a tenant A request, the next request on the same pooled connection starts with no tenant."
 */
@ExtendWith(MockitoExtension.class)
public class ConnectionReuseIsolationTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    private DeploymentProperties deploymentProperties;
    private SchemaMultiTenantConnectionProvider provider;

    @BeforeEach
    void setUp() throws SQLException {
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.createStatement()).thenReturn(statement);

        deploymentProperties = new DeploymentProperties();
        deploymentProperties.setDeploymentMode(DeploymentProperties.DeploymentMode.SHARED);
        deploymentProperties.setBaseDomain("pgcrm.com");

        provider = new SchemaMultiTenantConnectionProvider(dataSource, deploymentProperties);
    }

    @Test
    void testTenantConnectionSetsSearchPath_AndResetsOnReleaseInSharedMode() throws SQLException {
        // 1. Borrow connection for tenant Alpha (schema: t_alpha)
        Connection conn = provider.getConnection("t_alpha");
        assertNotNull(conn);

        // Verify search_path set to tenant schema
        verify(statement).execute("SET search_path TO t_alpha, public");

        // 2. Release connection back to pool
        provider.releaseConnection("t_alpha", conn);

        // Verify search_path was RESET to platform schema so pooled connection does not leak tenant data
        verify(statement).execute("SET search_path TO platform, public");
    }

    @Test
    void testReleaseConnectionInDedicatedMode_ResetsToDedicatedSchema() throws SQLException {
        deploymentProperties.setDeploymentMode(DeploymentProperties.DeploymentMode.DEDICATED);
        deploymentProperties.setDedicatedTenant("my_pg");

        SchemaMultiTenantConnectionProvider dedicatedProvider =
                new SchemaMultiTenantConnectionProvider(dataSource, deploymentProperties);

        Connection conn = dedicatedProvider.getConnection("t_my_pg");
        dedicatedProvider.releaseConnection("t_my_pg", conn);

        // Executed once on borrow and once on release
        verify(statement, times(2)).execute("SET search_path TO t_my_pg, public");
    }

    @Test
    void testInvalidSchemaIdentifier_IsRejectedToPreventSqlInjection() {
        assertThrows(IllegalArgumentException.class, () -> {
            provider.getConnection("t_malicious; DROP TABLE users; --");
        });
    }
}
