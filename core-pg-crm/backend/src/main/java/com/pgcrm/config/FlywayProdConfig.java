package com.pgcrm.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Production-safe Flyway migration strategy.
 *
 * <p>This configuration is loaded ONLY in the prod profile.
 * It never calls flyway.clean() — only migrate() — to prevent data loss.</p>
 */
@Configuration
@Profile("prod")
public class FlywayProdConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return Flyway::migrate;
    }
}
