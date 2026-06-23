package com.cloudpool.service;

import com.cloudpool.model.Bucket;
import com.cloudpool.model.FileMetadata;
import com.cloudpool.model.User;
import com.cloudpool.repository.BucketRepository;
import com.cloudpool.repository.FileMetadataRepository;
import com.cloudpool.repository.FileShareRepository;
import com.cloudpool.util.FileUploadValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock private FileMetadataRepository fileMetadataRepository;
    @Mock private BucketRepository bucketRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private GoogleDriveService googleDriveService;
    @Mock private FileShareRepository fileShareRepository;
    @Mock private QuotaService quotaService;
    @Mock private FileUploadValidator fileUploadValidator;
    @Mock private MetricsService metricsService;

    @InjectMocks
    private StorageService storageService;

    private User testUser;
    private Bucket testBucket;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(UUID.randomUUID()).email("storage@cloudpool.com").build();
        testBucket = Bucket.builder().id(UUID.randomUUID()).name("default").user(testUser).build();
        
        // Inject localDir property using Reflection (since it's an @Value injected field)
        ReflectionTestUtils.setField(storageService, "localDir", "./target/test-storage");
    }

    @Test
    @DisplayName("Upload should abort and throw exception if quota is exceeded")
    void testUploadFailsWhenQuotaExceeded() {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "Hello World".getBytes());
        
        // Arrange validator pass, but quota reserve fail
        doNothing().when(fileUploadValidator).validateFile(file);
        when(quotaService.reserveQuota(testUser.getId(), file.getSize())).thenReturn(false);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class, 
            () -> storageService.uploadFile(file, "default", testUser)
        );
        
        assertTrue(exception.getMessage().contains("quota exceeded"));
        verify(bucketRepository, never()).findByUserAndName(any(), any());
        verify(fileMetadataRepository, never()).save(any());
    }

    @Test
    @DisplayName("Upload should fallback to local disk if no Google Drive token is present")
    void testUploadLocalDiskFallback() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "Hello World".getBytes());
        
        // Arrange
        testUser.setGoogleRefreshToken(null); // No token
        doNothing().when(fileUploadValidator).validateFile(file);
        when(quotaService.reserveQuota(testUser.getId(), file.getSize())).thenReturn(true);
        when(bucketRepository.findByUserAndName(testUser, "default")).thenReturn(Optional.of(testBucket));
        when(fileUploadValidator.sanitizeFilename("test.txt")).thenReturn("test.txt");
        
        when(fileMetadataRepository.save(any(FileMetadata.class))).thenAnswer(i -> {
            FileMetadata meta = i.getArgument(0);
            meta.setId(UUID.randomUUID());
            return meta;
        });

        // Act
        FileMetadata metadata = storageService.uploadFile(file, "default", testUser);

        // Assert
        assertNotNull(metadata);
        assertNotNull(metadata.getDriveLocation());
        assertTrue(metadata.getDriveLocation().contains("test-storage")); // Ensure it points to localDir
        assertNull(metadata.getDriveFileId());
        
        verify(googleDriveService, never()).uploadFile(any(), any());
        verify(auditLogService).log(eq(testUser), eq("FILE_UPLOAD"), eq("FILE"), anyString(), anyString());
        verify(metricsService).incrementFileUploads();
    }

    @Test
    @DisplayName("Attempting to download a file belonging to another user should throw SecurityException")
    void testDownloadUnauthorizedAccess() {
        // Arrange
        UUID fileId = UUID.randomUUID();
        User differentUser = User.builder().id(UUID.randomUUID()).build();
        Bucket differentBucket = Bucket.builder().user(differentUser).build();
        
        FileMetadata privateFile = FileMetadata.builder()
            .id(fileId)
            .bucket(differentBucket)
            .isPublic(false)
            .build();
            
        when(fileMetadataRepository.findById(fileId)).thenReturn(Optional.of(privateFile));

        // Act & Assert
        assertThrows(SecurityException.class, () -> storageService.downloadFile(fileId, testUser));
    }
}
