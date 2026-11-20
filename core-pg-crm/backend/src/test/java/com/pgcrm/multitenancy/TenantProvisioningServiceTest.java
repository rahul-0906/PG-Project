package com.pgcrm.multitenancy;

import com.pgcrm.multitenancy.dto.CreateTenantRequest;
import com.pgcrm.multitenancy.dto.CreateTenantResult;
import com.pgcrm.multitenancy.dto.TenantSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TenantProvisioningServiceTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TenantRegistryService tenantRegistryService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private TenantProvisioningService service;

    @BeforeEach
    void setUp() throws SQLException {
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.getMetaData()).thenReturn(metaData);
        lenient().when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        lenient().when(metaData.getDatabaseProductVersion()).thenReturn("16.0");
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashed_password_123");

        service = new TenantProvisioningService(
                dataSource, jdbcTemplate, tenantRegistryService, passwordEncoder
        );
    }

    @Test
    void testCreateTenant_InvalidSlug_ThrowsValidationException() {
        CreateTenantRequest request = new CreateTenantRequest(
                "Invalid-Slug!", "My PG", "STANDARD", LocalDate.now(), List.of(), "owner@test.com", "Owner", "pass"
        );

        assertThrows(IllegalArgumentException.class, () -> service.createTenant(request));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void testSuspendTenant_Success() {
        when(jdbcTemplate.update(anyString(), eq("sri_sai"))).thenReturn(1);

        assertDoesNotThrow(() -> service.suspendTenant("sri_sai"));

        verify(jdbcTemplate).update(
                eq("UPDATE platform.tenants SET status = 'SUSPENDED' WHERE slug = ?"),
                eq("sri_sai")
        );
        verify(tenantRegistryService).evict(null, "sri_sai");
    }

    @Test
    void testActivateTenant_Success() {
        when(jdbcTemplate.update(anyString(), eq("sri_sai"))).thenReturn(1);

        assertDoesNotThrow(() -> service.activateTenant("sri_sai"));

        verify(jdbcTemplate).update(
                eq("UPDATE platform.tenants SET status = 'ACTIVE' WHERE slug = ?"),
                eq("sri_sai")
        );
        verify(tenantRegistryService).evict(null, "sri_sai");
    }

    @Test
    void testDeleteTenant_MarksDeletedOnly_NeverDropsSchema() {
        when(jdbcTemplate.update(anyString(), eq("sri_sai"))).thenReturn(1);

        assertDoesNotThrow(() -> service.deleteTenant("sri_sai"));

        // Must update status to DELETED
        verify(jdbcTemplate).update(
                eq("UPDATE platform.tenants SET status = 'DELETED' WHERE slug = ?"),
                eq("sri_sai")
        );
        // Must NEVER call DROP SCHEMA
        verify(jdbcTemplate, never()).execute(contains("DROP SCHEMA"));
        verify(tenantRegistryService).evict(null, "sri_sai");
    }

    @Test
    void testRollbackExecuted_OnProvisioningFailure() {
        CreateTenantRequest request = new CreateTenantRequest(
                "green_living", "Green Living PG", "STANDARD",
                LocalDate.now().plusYears(1), List.of("green.pgcrm.com"),
                "owner@green.com", "Owner", "secret123"
        );

        // Simulate error during schema creation or subsequent step
        doThrow(new RuntimeException("Schema creation disk error"))
                .when(jdbcTemplate).execute(contains("CREATE SCHEMA"));

        Exception ex = assertThrows(IllegalStateException.class, () -> service.createTenant(request));
        assertTrue(ex.getMessage().contains("Failed to provision tenant 'green_living'"));

        // Rollback verification: DROP SCHEMA must be executed
        verify(jdbcTemplate).execute(contains("DROP SCHEMA IF EXISTS t_green_living CASCADE"));

        // Rollback verification: Registry and domains must be deleted
        verify(jdbcTemplate).update(contains("DELETE FROM platform.tenants WHERE id = ?"), anyString());
        verify(tenantRegistryService).evict(null, "green_living");
    }
}
