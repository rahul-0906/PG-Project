package com.pgcrm.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Flyway migration strategy configuration.
 *
 * <p>DB_WIPE_ON_STARTUP is intentionally restricted to dev/test profiles only.
 * The prod profile uses a separate no-op strategy that only calls migrate().
 * This prevents accidental data loss in production.</p>
 */
@Configuration
@Profile("dev | test")
public class FlywayConfig {

    /**
     * When true, runs flyway.clean() before migrate() — DROPS ALL TABLES.
     * This env var is only read in dev/test profiles. Never set in production.
     */
    @Value("${DB_WIPE_ON_STARTUP:false}")
    private boolean wipeOnStartup;

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            if (wipeOnStartup) {
                flyway.clean();
            }
            flyway.migrate();
        };
    }
}
