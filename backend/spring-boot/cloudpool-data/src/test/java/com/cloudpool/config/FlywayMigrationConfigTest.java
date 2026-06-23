package com.cloudpool.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationConfigTest {

    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    @Test
    @DisplayName("All migration files should have unique version numbers")
    void testNoDuplicateMigrationVersions() throws IOException {
        Resource[] resources = resolver.getResources("classpath*:db/migration/**/V*__*.sql");

        Map<String, List<String>> versionMap = new LinkedHashMap<>();
        for (Resource r : resources) {
            String filename = r.getFilename();
            if (filename != null) {
                String version = filename.split("__")[0];
                versionMap.computeIfAbsent(version, k -> new ArrayList<>()).add(filename);
            }
        }

        List<String> duplicates = versionMap.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey() + " -> " + String.join(", ", e.getValue()))
                .collect(Collectors.toList());

        assertTrue(duplicates.isEmpty(),
                "Duplicate migration versions found: " + String.join("; ", duplicates));
    }

    @Test
    @DisplayName("Each migration file should follow V<version>__<description>.sql naming convention")
    void testMigrationFileNaming() throws IOException {
        Resource[] resources = resolver.getResources("classpath*:db/migration/**/V*__*.sql");
        assertTrue(resources.length > 0, "Should find at least one migration file");
        for (Resource r : resources) {
            String filename = r.getFilename();
            assertNotNull(filename, "Migration file should have a name");
            assertTrue(filename.matches("V\\d+__.*\\.sql"),
                    "Invalid migration filename format: " + filename);
        }
    }
}