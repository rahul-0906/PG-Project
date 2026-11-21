package com.pgcrm.controller;

import com.pgcrm.multitenancy.DeploymentProperties;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Uptime, connection pool status, and system health monitoring endpoint.
 *
 * <p><strong>Hardening Requirement (Spec Phase 7):</strong></p>
 * <ul>
 *   <li>Exposes {@code /api/health} public probe for uptime monitoring (Pingdom, UptimeRobot, Datadog).</li>
 *   <li>Verifies database connectivity with response latency.</li>
 *   <li>Inspects HikariCP connection pool health to detect pool exhaustion or thread contention.</li>
 *   <li>Reports tenant registry health and deployment mode.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Slf4j
public class HealthCheckController {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final DeploymentProperties deploymentProperties;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("deploymentMode", deploymentProperties.getDeploymentMode().name().toLowerCase());
        health.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);

        // 1. Check Database Connectivity
        long start = System.currentTimeMillis();
        boolean dbUp = false;
        long latencyMs = -1;
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            latencyMs = System.currentTimeMillis() - start;
            dbUp = (result != null && result == 1);
        } catch (Exception e) {
            log.error("Database healthcheck query failed: {}", e.getMessage());
        }

        Map<String, Object> dbHealth = new HashMap<>();
        dbHealth.put("status", dbUp ? "UP" : "DOWN");
        dbHealth.put("responseTimeMs", latencyMs);
        health.put("database", dbHealth);

        // 2. Inspect Connection Pool (HikariCP)
        if (dataSource instanceof HikariDataSource hikariDs) {
            Map<String, Object> poolHealth = new HashMap<>();
            poolHealth.put("poolName", hikariDs.getPoolName());
            poolHealth.put("maximumPoolSize", hikariDs.getMaximumPoolSize());
            poolHealth.put("minimumIdle", hikariDs.getMinimumIdle());

            HikariPoolMXBean poolMxBean = hikariDs.getHikariPoolMXBean();
            if (poolMxBean != null) {
                poolHealth.put("activeConnections", poolMxBean.getActiveConnections());
                poolHealth.put("idleConnections", poolMxBean.getIdleConnections());
                poolHealth.put("totalConnections", poolMxBean.getTotalConnections());
                poolHealth.put("threadsAwaitingConnection", poolMxBean.getThreadsAwaitingConnection());
            }
            health.put("connectionPool", poolHealth);
        }

        // 3. Count Active Tenants in platform.tenants
        try {
            Long activeTenants = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM platform.tenants WHERE status = 'ACTIVE'", Long.class);
            health.put("activeTenants", activeTenants != null ? activeTenants : 0);
        } catch (Exception e) {
            health.put("activeTenants", "UNAVAILABLE");
        }

        if (!dbUp) {
            health.put("status", "DOWN");
            return ResponseEntity.status(503).body(health);
        }

        return ResponseEntity.ok(health);
    }
}
