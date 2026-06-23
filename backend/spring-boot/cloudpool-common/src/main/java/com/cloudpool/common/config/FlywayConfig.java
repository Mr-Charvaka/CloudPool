package com.cloudpool.common.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile({"local", "dev"})
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayRepairStrategy() {
        return flyway -> {
            try {
                log.info("Flyway: Running repair before migration to fix any previously failed migrations...");
                flyway.repair();
                log.info("Flyway: Repair completed successfully.");
            } catch (Exception e) {
                log.warn("Flyway: Repair failed (may be safe to ignore): {}", e.getMessage());
            }
            flyway.migrate();
        };
    }
}
