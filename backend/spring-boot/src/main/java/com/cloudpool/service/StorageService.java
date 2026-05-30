package com.cloudpool.service;

import com.cloudpool.model.*;

import com.cloudpool.repository.BucketRepository;
import com.cloudpool.repository.FileMetadataRepository;
import com.cloudpool.repository.FileShareRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.cloudpool.util.FileUploadValidator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final FileMetadataRepository fileMetadataRepository;
    private final BucketRepository bucketRepository;
    private final AuditLogService auditLogService;
    private final GoogleDriveService googleDriveService;
    private final FileShareRepository fileShareRepository;
    private final QuotaService quotaService;
    private final FileUploadValidator fileUploadValidator;
    private final MetricsService metricsService;

    @Value("${cloudpool.storage.local-dir:./storage}")
    private String localDir;

    public FileMetadata uploadFile(MultipartFile file, String bucketName, User user) throws IOException {
        // Validate file uploads first
        fileUploadValidator.validateFile(file);
 
        // Reserve quota first (isolated write-locked transaction)
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

        // Extract extension
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }

        String driveFileId = null;
        String driveLocation = null;
        String name = null;

        try {
            if (user.getGoogleRefreshToken() != null) {
                // Upload directly to Google Drive!
                driveFileId = googleDriveService.uploadFile(file, user);
                driveLocation = "Google Drive";
                name = driveFileId;
            } else {
                // Ensure local storage directory exists
                Path uploadPath = Paths.get(localDir).toAbsolutePath().normalize();
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                String sanitized = fileUploadValidator.sanitizeFilename(originalFilename);
                name = UUID.randomUUID().toString() + "_" + sanitized;
                Path targetLocation = uploadPath.resolve(name);
                Files.copy(file.getInputStream(), targetLocation);
                driveLocation = targetLocation.toString();
            }
        } catch (Exception e) {
            // Rollback quota reservation on upload failure
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
                .build();

        FileMetadata saved = fileMetadataRepository.save(metadata);

        // Record Audit Log
        auditLogService.log(user, AuditLogService.ACTION_FILE_UPLOAD, "FILE", saved.getId().toString(),
                String.format("Uploaded file '%s' (%d bytes) to pool '%s' (Storage: %s)", 
                        saved.getOriginalName(), saved.getSize(), bucket.getName(), driveFileId != null ? "Google Drive" : "Local Disk"));

        metricsService.incrementFileUploads();
        return saved;
    }

    public FileShare shareFile(UUID fileId, String sharedWithEmail, Integer expiryHours, User user) {
        FileMetadata metadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        // Auth check: file must belong to user
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

        // Record Audit Log
        auditLogService.log(user, "SHARE_FILE", "FILE", fileId.toString(),
                String.format("Shared file '%s' via token (Shared with: %s, Expires: %s)", 
                        metadata.getOriginalName(), 
                        sharedWithEmail != null ? sharedWithEmail : "Anyone with link",
                        expiresAt != null ? expiresAt.toString() : "Never"));

        return savedShare;
    }

    public byte[] downloadFileDirectly(FileMetadata metadata) throws IOException {
        if (metadata.getDriveFileId() != null) {
            // Download directly from Google Drive!
            return googleDriveService.downloadFile(metadata.getDriveFileId(), metadata.getBucket().getUser());
        } else {
            Path filePath = Paths.get(metadata.getDriveLocation());
            return Files.readAllBytes(filePath);
        }
    }

    public byte[] downloadFile(UUID fileId, User user) throws IOException {
        FileMetadata metadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        // Auth check: file must belong to user unless public
        if (!metadata.isPublic() && !metadata.getBucket().getUser().getId().equals(user.getId())) {
            throw new SecurityException("Unauthorized access to file");
        }

        byte[] data = downloadFileDirectly(metadata);

        // Audit Log
        auditLogService.log(user, AuditLogService.ACTION_FILE_DOWNLOAD, "FILE", metadata.getId().toString(),
                String.format("Downloaded file '%s'", metadata.getOriginalName()));

        metricsService.incrementFileDownloads();
        return data;
    }

    public List<FileMetadata> listUserFiles(User user) {
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
}
