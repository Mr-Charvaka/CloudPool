package com.cloudpool.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class FlywayMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cloudpool_test")
            .withUsername("cloudpool")
            .withPassword("cloudpool");

    private static Flyway flyway;

    @BeforeAll
    static void setUp() {
        flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();
    }

    @Test
    void allMigrationsApplySuccessfully() {
        MigrateResult result = flyway.migrate();

        assertNotNull(result);
        assertEquals(0, result.warnings.size(), "Migrations completed with warnings: " + result.warnings);
        assertTrue(result.success, "Flyway migration should succeed");
    }

    @Test
    void noPendingMigrationsAfterApply() {
        flyway.migrate();

        MigrationInfo[] pending = flyway.info().pending();
        assertEquals(0, pending.length, "There should be no pending migrations after applying all");
    }

    @Test
    void migrationsAreIdempotent() {
        flyway.migrate();

        MigrateResult secondRun = flyway.migrate();
        assertTrue(secondRun.success);
        assertEquals(0, secondRun.migrationsExecuted, "Second migrate should execute 0 migrations");
    }

    @Test
    void allMigrationsHaveUniqueVersions() {
        flyway.migrate();

        MigrationInfo[] applied = flyway.info().applied();
        long uniqueVersions = java.util.Arrays.stream(applied)
                .map(MigrationInfo::getVersion)
                .distinct()
                .count();
        assertEquals(applied.length, uniqueVersions, "Each migration must have a unique version");
    }
}