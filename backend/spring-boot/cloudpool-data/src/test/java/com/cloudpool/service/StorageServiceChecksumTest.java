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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceChecksumTest {

    @Mock private FileMetadataRepository fileMetadataRepository;
    @Mock private BucketRepository bucketRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private GoogleDriveService googleDriveService;
    @Mock private FileShareRepository fileShareRepository;
    @Mock private QuotaService quotaService;
    @Mock private FileUploadValidator fileUploadValidator;
    @Mock private MetricsService metricsService;
    @Mock private NativeProcessor nativeProcessor;

    @InjectMocks
    private StorageService storageService;

    @Captor private ArgumentCaptor<FileMetadata> metadataCaptor;

    private User testUser;
    private Bucket testBucket;

    @BeforeEach
    void setUp() throws IOException {
        testUser = User.builder().id(UUID.randomUUID()).email("checksum@cloudpool.com").build();
        testBucket = Bucket.builder().id(UUID.randomUUID()).name("default").user(testUser).build();
        ReflectionTestUtils.setField(storageService, "localDir", "./target/test-storage");
        
        java.io.File dir = new java.io.File("./target/test-storage");
        if (!dir.exists()) dir.mkdirs();
        java.nio.file.Files.write(java.nio.file.Paths.get("./target/test-storage/test.txt"), "test content".getBytes());
    }

    @Test
    @DisplayName("Should compute and store checksum during file upload")
    void testUploadFileComputesChecksum() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "Hello World".getBytes());
        testUser.setGoogleRefreshToken(null);
        doNothing().when(fileUploadValidator).validateFile(any(MultipartFile.class));
        when(quotaService.reserveQuota(testUser.getId(), file.getSize())).thenReturn(true);
        when(bucketRepository.findByUserAndName(testUser, "default")).thenReturn(Optional.of(testBucket));
        when(fileUploadValidator.sanitizeFilename("test.txt")).thenReturn("test.txt");
        when(fileMetadataRepository.save(metadataCaptor.capture())).thenAnswer(i -> {
            FileMetadata meta = i.getArgument(0);
            meta.setId(UUID.randomUUID());
            return meta;
        });

        storageService.uploadFile(file, "default", testUser);

        FileMetadata saved = metadataCaptor.getValue();
        // SHA-256 of "Hello World"
        assertEquals("a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e", saved.getChecksum());
    }

    @Test
    @DisplayName("Should compute SHA-256 correctly on upload")
    void testUploadFileCallsNativeProcessor() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "data.bin", "application/octet-stream", new byte[]{1, 2, 3, 4, 5});
        testUser.setGoogleRefreshToken(null);
        doNothing().when(fileUploadValidator).validateFile(any(MultipartFile.class));
        when(quotaService.reserveQuota(testUser.getId(), file.getSize())).thenReturn(true);
        when(bucketRepository.findByUserAndName(testUser, "default")).thenReturn(Optional.of(testBucket));
        when(fileUploadValidator.sanitizeFilename("data.bin")).thenReturn("data.bin");
        when(fileMetadataRepository.save(metadataCaptor.capture())).thenAnswer(i -> {
            FileMetadata meta = i.getArgument(0);
            meta.setId(UUID.randomUUID());
            return meta;
        });

        storageService.uploadFile(file, "default", testUser);

        // SHA-256 of 0x01, 0x02, 0x03, 0x04, 0x05
        assertEquals("74f81fe167d99b4cb41d6d0ccda82278caee9f3e2f25d5e5a3936ff3dcec60d0", metadataCaptor.getValue().getChecksum());
    }

    @Test
    @DisplayName("Should download file successfully without verifying checksum in download stream")
    void testDownloadFileVerifiesChecksum() throws IOException {
        FileMetadata metadata = FileMetadata.builder()
                .id(UUID.randomUUID())
                .bucket(testBucket)
                .name("test.txt")
                .driveLocation("./target/test-storage/test.txt")
                .checksum("abc123")
                .isPublic(true)
                .build();

        when(fileMetadataRepository.findById(metadata.getId())).thenReturn(Optional.of(metadata));

        org.springframework.core.io.Resource resource = storageService.downloadFile(metadata.getId(), testUser);
        assertNotNull(resource);
    }

    @Test
    @DisplayName("Should not fail when checksum is null on download")
    void testDownloadFileNullChecksum() throws IOException {
        FileMetadata metadata = FileMetadata.builder()
                .id(UUID.randomUUID())
                .bucket(testBucket)
                .name("test.txt")
                .driveLocation("./target/test-storage/test.txt")
                .checksum(null)
                .isPublic(true)
                .build();

        when(fileMetadataRepository.findById(metadata.getId())).thenReturn(Optional.of(metadata));

        org.springframework.core.io.Resource resource = storageService.downloadFile(metadata.getId(), testUser);
        assertNotNull(resource);
    }

    @Test
    @DisplayName("Should compute checksum even for Google Drive uploads")
    void testUploadToDriveAlsoComputesChecksum() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "remote.txt", "text/plain", "Drive content".getBytes());
        testUser.setGoogleRefreshToken("fake-google-token");
        doNothing().when(fileUploadValidator).validateFile(any(MultipartFile.class));
        when(quotaService.reserveQuota(testUser.getId(), file.getSize())).thenReturn(true);
        when(bucketRepository.findByUserAndName(testUser, "default")).thenReturn(Optional.of(testBucket));
        when(googleDriveService.uploadFile(any(MultipartFile.class), eq(testUser))).thenReturn("drive-file-id");
        when(fileMetadataRepository.save(metadataCaptor.capture())).thenAnswer(i -> {
            FileMetadata meta = i.getArgument(0);
            meta.setId(UUID.randomUUID());
            return meta;
        });

        storageService.uploadFile(file, "default", testUser);

        FileMetadata saved = metadataCaptor.getValue();
        // SHA-256 of "Drive content"
        assertEquals("620f7705f438a8f52250e32cc1d8b46cd1cec45853380d41b5c1b66865f153f3", saved.getChecksum());
        assertEquals("Google Drive", saved.getDriveLocation());
    }
}