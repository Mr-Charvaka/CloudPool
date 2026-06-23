package com.cloudpool.service;

import com.cloudpool.CloudpoolDataApplication;
import com.cloudpool.model.Bucket;
import com.cloudpool.model.FileMetadata;
import com.cloudpool.model.User;
import com.cloudpool.repository.BucketRepository;
import com.cloudpool.repository.FileMetadataRepository;
import com.cloudpool.repository.UserRepository;
import com.cloudpool.test.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = CloudpoolDataApplication.class)
@ActiveProfiles("test") // Use test profile to prevent triggering startup migrations that conflict if needed
@Transactional // Rollback after every test to keep DB clean
class StorageServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private StorageService storageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BucketRepository bucketRepository;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Seed the PostgreSQL testcontainer with a user and bucket
        testUser = User.builder()
                .email("integration_test@cloudpool.com")
                .name("Integration User")
                .storageQuotaBytes(104857600L) // 100MB
                .usedStorageBytes(0L)
                .build();
        
        testUser = userRepository.saveAndFlush(testUser);

        Bucket defaultBucket = Bucket.builder()
                .name("default")
                .user(testUser)
                .region("us-east-1")
                .build();
                
        bucketRepository.saveAndFlush(defaultBucket);
    }

    @Test
    @DisplayName("Should successfully upload a file and persist metadata to PostgreSQL Testcontainer")
    void testFileUploadPersistsToDatabase() throws IOException {
        // Arrange
        MultipartFile file = new MockMultipartFile(
                "file", 
                "integration_test.txt", 
                "text/plain", 
                "Real database test content".getBytes()
        );

        // Act
        FileMetadata savedMetadata = storageService.uploadFile(file, "default", testUser);

        // Assert
        assertNotNull(savedMetadata, "StorageService should return saved metadata");
        assertNotNull(savedMetadata.getId(), "Database should have generated a UUID via RANDOM_UUID()");
        
        // Verify it actually exists in the database
        boolean exists = fileMetadataRepository.existsById(savedMetadata.getId());
        assertTrue(exists, "Record must exist in the PostgreSQL Testcontainer");
    }
}
