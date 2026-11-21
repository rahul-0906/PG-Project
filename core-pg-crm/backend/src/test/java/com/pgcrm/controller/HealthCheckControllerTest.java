package com.pgcrm.controller;

import com.pgcrm.multitenancy.DeploymentProperties;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthCheckControllerTest {

    @Mock
    private HikariDataSource dataSource;

    @Mock
    private HikariPoolMXBean poolMxBean;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DeploymentProperties deploymentProperties;
    private HealthCheckController controller;

    @BeforeEach
    void setUp() {
        deploymentProperties = new DeploymentProperties();
        deploymentProperties.setDeploymentMode(DeploymentProperties.DeploymentMode.SHARED);
        controller = new HealthCheckController(dataSource, jdbcTemplate, deploymentProperties);
    }

    @Test
    void testHealthCheckHealthy() {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM platform.tenants WHERE status = 'ACTIVE'"), eq(Long.class))).thenReturn(3L);

        when(dataSource.getPoolName()).thenReturn("HikariPool-1");
        when(dataSource.getMaximumPoolSize()).thenReturn(25);
        when(dataSource.getMinimumIdle()).thenReturn(5);
        when(dataSource.getHikariPoolMXBean()).thenReturn(poolMxBean);
        when(poolMxBean.getActiveConnections()).thenReturn(2);
        when(poolMxBean.getIdleConnections()).thenReturn(8);
        when(poolMxBean.getTotalConnections()).thenReturn(10);
        when(poolMxBean.getThreadsAwaitingConnection()).thenReturn(0);

        ResponseEntity<Map<String, Object>> response = controller.getHealth();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("UP", body.get("status"));
        assertEquals("shared", body.get("deploymentMode"));
        assertEquals(3L, body.get("activeTenants"));

        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) body.get("database");
        assertEquals("UP", db.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> pool = (Map<String, Object>) body.get("connectionPool");
        assertEquals("HikariPool-1", pool.get("poolName"));
        assertEquals(2, pool.get("activeConnections"));
        assertEquals(8, pool.get("idleConnections"));
    }

    @Test
    void testHealthCheckDatabaseDown() {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        ResponseEntity<Map<String, Object>> response = controller.getHealth();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("DOWN", body.get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) body.get("database");
        assertEquals("DOWN", db.get("status"));
    }
}
