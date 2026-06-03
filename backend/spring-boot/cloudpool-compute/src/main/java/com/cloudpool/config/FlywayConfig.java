package com.cloudpool.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Flyway configuration that auto-repairs failed migrations before applying new ones.
 * Only active in local/dev profiles where H2 is used (not in production CI).
 */
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
