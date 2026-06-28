package com.cloudpool.service;

import com.cloudpool.model.DevTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudpool.CloudpoolDataApplication;

import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = CloudpoolDataApplication.class)
@Testcontainers(disabledWithoutDocker = true)
class DatabaseServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("cloudpool_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private DatabaseService databaseService;

    private UUID userA;
    private UUID userB;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        projectId = UUID.randomUUID();
    }

    @Test
    void testTenantIsolation_UserBCannotAccessUserAsTable() {
        // User A creates a table
        DevTable tableA = databaseService.createTable(
                userA, projectId, "users", "Users Table", "Stores user data",
                List.of(
                        new DatabaseService.FieldRequest("username", "VARCHAR", true),
                        new DatabaseService.FieldRequest("age", "INTEGER", false)
                )
        );

        assertNotNull(tableA.getId());

        // User A can fetch it
        DevTable fetchedTable = databaseService.getTable(tableA.getId(), userA);
        assertEquals("users", fetchedTable.getDisplayName());

        // User B attempts to fetch it (should throw SecurityException)
        SecurityException thrown = assertThrows(SecurityException.class, () -> {
            databaseService.getTable(tableA.getId(), userB);
        });
        assertEquals("Access denied to requested table", thrown.getMessage());
    }

    @Test
    void testJsonbInsertionAndRetrieval() {
        // User A creates a table
        DevTable tableA = databaseService.createTable(
                userA, projectId, "products", "Products Table", "Stores product data",
                List.of(
                        new DatabaseService.FieldRequest("title", "VARCHAR", true),
                        new DatabaseService.FieldRequest("price", "DOUBLE", true),
                        new DatabaseService.FieldRequest("in_stock", "BOOLEAN", false)
                )
        );

        // Insert Record
        Map<String, Object> data = Map.of(
                "title", "Laptop",
                "price", 999.99,
                "in_stock", true
        );
        Map<String, Object> inserted = databaseService.insertRecord(tableA.getId(), data, userA);
        
        assertNotNull(inserted.get("id"));
        assertEquals("Laptop", inserted.get("title"));
        assertEquals(999.99, inserted.get("price"));
        assertEquals(true, inserted.get("in_stock"));

        // Retrieve Record
        List<Map<String, Object>> records = databaseService.queryRecords(tableA.getId(), userA);
        assertEquals(1, records.size());
        assertEquals("Laptop", records.get(0).get("title"));

        // Delete Record
        databaseService.deleteRecord(tableA.getId(), inserted.get("id").toString(), userA);
        List<Map<String, Object>> emptyRecords = databaseService.queryRecords(tableA.getId(), userA);
        assertTrue(emptyRecords.isEmpty());
    }
}
