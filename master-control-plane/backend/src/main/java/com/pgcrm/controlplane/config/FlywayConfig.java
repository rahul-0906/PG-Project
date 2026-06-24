package com.pgcrm.controlplane.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

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
