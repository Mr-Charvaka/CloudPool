package com.cloudpool.service;

import com.cloudpool.model.*;

import com.cloudpool.repository.BucketRepository;
import com.cloudpool.repository.FileMetadataRepository;
import com.cloudpool.repository.FileShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.cloudpool.util.FileUploadValidator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final FileMetadataRepository fileMetadataRepository;
    private final BucketRepository bucketRepository;
    private final AuditLogService auditLogService;
    private final GoogleDriveService googleDriveService;
    private final FileShareRepository fileShareRepository;
    private final QuotaService quotaService;
    private final FileUploadValidator fileUploadValidator;
    private final MetricsService metricsService;
    private final NativeProcessor nativeProcessor;
    private final java.util.Optional<com.cloudpool.service.S3Service> s3Service;

    @Value("${cloudpool.storage.local-dir:./storage}")
    private String localDir;

    @Value("${cloudpool.aws.s3.enabled:false}")
    private boolean isS3Enabled;

    @Value("${cloudpool.storage.compression-enabled:false}")
    private boolean compressionEnabled;

    private static final String COMPRESSED_SUFFIX = ".cpz";

    public FileMetadata uploadFile(MultipartFile file, String bucketName, User user) throws IOException {
        fileUploadValidator.validateFile(file);

        boolean reserved = quotaService.reserveQuota(user.getId(), file.getSize());
        if (!reserved) {
            throw new IllegalArgumentException("Storage quota exceeded. Cannot upload file.");
        }

        Bucket bucket = bucketRepository.findByUserAndName(user, bucketName)
                .orElseGet(() -> {
                    Bucket newBucket = Bucket.builder()
                            .user(user)
                            .name(bucketName)
                            .description("Auto-created bucket")
                            .build();
                    return bucketRepository.save(newBucket);
                });

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }

        String driveFileId = null;
        String driveLocation = null;
        String name = null;
        String checksum = null;

        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            
            if (user.getGoogleRefreshToken() != null || (isS3Enabled && s3Service.isPresent())) {
                // Single-pass: write to temp file while computing checksum
                Path tempFile = Files.createTempFile("cloudpool-upload-", ".tmp");
                try {
                    try (InputStream is = file.getInputStream();
                         java.security.DigestInputStream dis = new java.security.DigestInputStream(is, digest);
                         java.io.OutputStream os = Files.newOutputStream(tempFile)) {
                        dis.transferTo(os);
                    }
                    checksum = java.util.HexFormat.of().formatHex(digest.digest());
                    digest = java.security.MessageDigest.getInstance("SHA-256");

                    MultipartFile tempMultipart = new MultipartFile() {
                        @Override public String getName() { return file.getName(); }
                        @Override public String getOriginalFilename() { return file.getOriginalFilename(); }
                        @Override public String getContentType() { return file.getContentType(); }
                        @Override public boolean isEmpty() { return false; }
                        @Override public long getSize() {
                            try { return Files.size(tempFile); } catch (IOException e) { return 0; }
                        }
                        @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(tempFile); }
                        @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(tempFile); }
                        @Override public void transferTo(File dest) throws IOException, IllegalStateException {
                            Files.copy(tempFile, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    };

                    if (user.getGoogleRefreshToken() != null) {
                        driveFileId = googleDriveService.uploadFile(tempMultipart, user);
                        driveLocation = "Google Drive";
                        name = driveFileId;
                    } else {
                        driveFileId = s3Service.get().uploadFile(tempMultipart, user);
                        driveLocation = "AWS S3";
                        name = driveFileId;
                    }
                } finally {
                    Files.deleteIfExists(tempFile);
                }
            } else {
                Path uploadPath = Paths.get(localDir).toAbsolutePath().normalize();
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                String sanitized = fileUploadValidator.sanitizeFilename(originalFilename);
                name = UUID.randomUUID().toString() + "_" + sanitized;
                Path targetLocation = uploadPath.resolve(name).normalize();
                if (!targetLocation.startsWith(uploadPath)) {
                    throw new SecurityException("Invalid target file path (path traversal attempt)");
                }

                if (compressionEnabled) {
                    Path compressedPath = Paths.get(targetLocation + COMPRESSED_SUFFIX);
                    try (java.io.OutputStream os = Files.newOutputStream(compressedPath);
                         java.util.zip.GZIPOutputStream gzipOs = new java.util.zip.GZIPOutputStream(os);
                         InputStream is = file.getInputStream();
                         java.security.DigestInputStream dis = new java.security.DigestInputStream(is, digest)) {
                        dis.transferTo(gzipOs);
                    }
                    driveLocation = compressedPath.toString();
                } else {
                    try (java.io.OutputStream os = Files.newOutputStream(targetLocation);
                         InputStream is = file.getInputStream();
                         java.security.DigestInputStream dis = new java.security.DigestInputStream(is, digest)) {
                        dis.transferTo(os);
                    }
                    driveLocation = targetLocation.toString();
                }
            }

            if (checksum == null) {
                checksum = java.util.HexFormat.of().formatHex(digest.digest());
            }
        } catch (Exception e) {
            quotaService.releaseQuota(user.getId(), file.getSize());
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException(e);
        }

        FileMetadata metadata = FileMetadata.builder()
                .bucket(bucket)
                .name(name)
                .originalName(originalFilename != null ? originalFilename : name)
                .size(file.getSize())
                .mimeType(file.getContentType())
                .extension(extension)
                .driveLocation(driveLocation)
                .driveFileId(driveFileId)
                .checksum(checksum)
                .build();

        FileMetadata saved = fileMetadataRepository.save(metadata);

        auditLogService.log(user, AuditLogService.ACTION_FILE_UPLOAD, "FILE", saved.getId().toString(),
                String.format("Uploaded file '%s' (%d bytes) to pool '%s' (Storage: %s)",
                        saved.getOriginalName(), saved.getSize(), bucket.getName(), driveLocation));

        metricsService.incrementFileUploads();
        return saved;
    }

    public FileShare shareFile(UUID fileId, String sharedWithEmail, Integer expiryHours, User user) {
        FileMetadata metadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        if (!metadata.getBucket().getUser().getId().equals(user.getId())) {
            throw new SecurityException("Unauthorized to share this file");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = expiryHours != null && expiryHours > 0
                ? LocalDateTime.now().plusHours(expiryHours)
                : null;

        FileShare fileShare = FileShare.builder()
                .fileId(fileId)
                .sharedWithEmail(sharedWithEmail != null && !sharedWithEmail.trim().isEmpty() ? sharedWithEmail.trim() : null)
                .token(token)
                .expiresAt(expiresAt)
                .permission("READ")
                .build();

        FileShare savedShare = fileShareRepository.save(fileShare);

        auditLogService.log(user, "SHARE_FILE", "FILE", fileId.toString(),
                String.format("Shared file '%s' via token (Shared with: %s, Expires: %s)",
                        metadata.getOriginalName(),
                        sharedWithEmail != null ? sharedWithEmail : "Anyone with link",
                        expiresAt != null ? expiresAt.toString() : "Never"));

        return savedShare;
    }

    public org.springframework.core.io.Resource downloadFileDirectly(FileMetadata metadata) throws IOException {
        if ("Google Drive".equals(metadata.getDriveLocation()) && metadata.getDriveFileId() != null) {
            byte[] data = googleDriveService.downloadFile(metadata.getDriveFileId(), metadata.getBucket().getUser());
            return new org.springframework.core.io.ByteArrayResource(data);
        } else if ("AWS S3".equals(metadata.getDriveLocation()) && metadata.getDriveFileId() != null && s3Service.isPresent()) {
            byte[] data = s3Service.get().downloadFile(metadata.getDriveFileId());
            return new org.springframework.core.io.ByteArrayResource(data);
        } else {
            Path filePath = Paths.get(metadata.getDriveLocation());
            if (!Files.exists(filePath)) {
                throw new IOException("File not found on storage: " + filePath);
            }
            if (filePath.toString().endsWith(COMPRESSED_SUFFIX)) {
                byte[] fileBytes = Files.readAllBytes(filePath);
                fileBytes = nativeProcessor.decompress(fileBytes);
                return new org.springframework.core.io.ByteArrayResource(fileBytes);
            }
            return new org.springframework.core.io.FileSystemResource(filePath);
        }
    }

    public org.springframework.core.io.Resource downloadFile(UUID fileId, User user) throws IOException {
        FileMetadata metadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        if (!metadata.isPublic() && !metadata.getBucket().getUser().getId().equals(user.getId())) {
            throw new SecurityException("Unauthorized access to file");
        }

        org.springframework.core.io.Resource data = downloadFileDirectly(metadata);

        auditLogService.log(user, AuditLogService.ACTION_FILE_DOWNLOAD, "FILE", metadata.getId().toString(),
                String.format("Downloaded file '%s'", metadata.getOriginalName()));

        metricsService.incrementFileDownloads();
        return data;
    }

    public List<FileMetadata> listUserFiles(User user) {
        return fileMetadataRepository.findByUserId(user.getId());
    }

    public List<FileMetadata> listUserFiles(User user, org.springframework.data.domain.Pageable pageable) {
        return fileMetadataRepository.findByUserId(user.getId());
    }

    public List<Bucket> listUserBuckets(User user) {
        return bucketRepository.findByUser(user);
    }

    public java.util.Map<String, Long> getStorageQuota(User user) {
        if (user.getGoogleRefreshToken() != null) {
            java.util.Map<String, Long> driveQuota = googleDriveService.getStorageQuota(user);
            if (driveQuota != null) {
                return driveQuota;
            }
        }

        java.util.Map<String, Long> result = new java.util.HashMap<>();
        result.put("limit", user.getStorageQuota());
        result.put("usage", user.getCurrentUsage());
        return result;
    }

    @Scheduled(fixedRate = 86400000)
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "FileShare_purgeExpiredTokens", lockAtLeastFor = "5m", lockAtMostFor = "55m")
    @Transactional
    public void purgeExpiredShares() {
        int deleted = fileShareRepository.deleteExpiredShares(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Purged {} expired file sharing tokens from database.", deleted);
        }
    }
}
