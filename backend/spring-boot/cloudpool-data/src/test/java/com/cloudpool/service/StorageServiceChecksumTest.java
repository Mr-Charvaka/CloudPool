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
    void setUp() {
        testUser = User.builder().id(UUID.randomUUID()).email("checksum@cloudpool.com").build();
        testBucket = Bucket.builder().id(UUID.randomUUID()).name("default").user(testUser).build();
        ReflectionTestUtils.setField(storageService, "localDir", "./target/test-storage");
    }

    @Test
    @DisplayName("Should compute and store checksum during file upload")
    void testUploadFileComputesChecksum() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "Hello World".getBytes());
        testUser.setGoogleRefreshToken(null);
        doNothing().when(fileUploadValidator).validateFile(file);
        when(quotaService.reserveQuota(testUser.getId(), file.getSize())).thenReturn(true);
        when(bucketRepository.findByUserAndName(testUser, "default")).thenReturn(Optional.of(testBucket));
        when(fileUploadValidator.sanitizeFilename("test.txt")).thenReturn("test.txt");
        when(fileMetadataRepository.save(metadataCaptor.capture())).thenAnswer(i -> {
            FileMetadata meta = i.getArgument(0);
            meta.setId(UUID.randomUUID());
            return meta;
        });
        when(nativeProcessor.calculateChecksum(any(byte[].class))).thenReturn("abc123checksum");

        storageService.uploadFile(file, "default", testUser);

        FileMetadata saved = metadataCaptor.getValue();
        assertEquals("abc123checksum", saved.getChecksum());
    }

    @Test
    @DisplayName("Should compute SHA-256 via NativeProcessor on upload")
    void testUploadFileCallsNativeProcessor() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "data.bin", "application/octet-stream", new byte[]{1, 2, 3, 4, 5});
        testUser.setGoogleRefreshToken(null);
        doNothing().when(fileUploadValidator).validateFile(file);
        when(quotaService.reserveQuota(testUser.getId(), file.getSize())).thenReturn(true);
        when(bucketRepository.findByUserAndName(testUser, "default")).thenReturn(Optional.of(testBucket));
        when(fileUploadValidator.sanitizeFilename("data.bin")).thenReturn("data.bin");
        when(fileMetadataRepository.save(any())).thenAnswer(i -> {
            FileMetadata meta = i.getArgument(0);
            meta.setId(UUID.randomUUID());
            return meta;
        });
        when(nativeProcessor.calculateChecksum(any(byte[].class))).thenReturn("a1b2c3d4");

        storageService.uploadFile(file, "default", testUser);

        verify(nativeProcessor).calculateChecksum(new byte[]{1, 2, 3, 4, 5});
    }

    @Test
    @DisplayName("Should verify checksum during download and warn on mismatch")
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
        when(nativeProcessor.calculateChecksum(any(byte[].class))).thenReturn("abc123");

        storageService.downloadFile(metadata.getId(), testUser);

        verify(nativeProcessor).calculateChecksum(any(byte[].class));
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

        storageService.downloadFile(metadata.getId(), testUser);

        verify(nativeProcessor, never()).calculateChecksum(any());
    }

    @Test
    @DisplayName("Should compute checksum even for Google Drive uploads")
    void testUploadToDriveAlsoComputesChecksum() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "remote.txt", "text/plain", "Drive content".getBytes());
        testUser.setGoogleRefreshToken("fake-google-token");
        doNothing().when(fileUploadValidator).validateFile(file);
        when(quotaService.reserveQuota(testUser.getId(), file.getSize())).thenReturn(true);
        when(bucketRepository.findByUserAndName(testUser, "default")).thenReturn(Optional.of(testBucket));
        when(googleDriveService.uploadFile(file, testUser)).thenReturn("drive-file-id");
        when(fileMetadataRepository.save(metadataCaptor.capture())).thenAnswer(i -> {
            FileMetadata meta = i.getArgument(0);
            meta.setId(UUID.randomUUID());
            return meta;
        });
        when(nativeProcessor.calculateChecksum(any(byte[].class))).thenReturn("driveChecksum");

        storageService.uploadFile(file, "default", testUser);

        FileMetadata saved = metadataCaptor.getValue();
        assertEquals("driveChecksum", saved.getChecksum());
        assertEquals("Google Drive", saved.getDriveLocation());
    }
}