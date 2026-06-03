package com.cloudpool.controller;

import com.cloudpool.model.*;
import com.cloudpool.service.AuditLogService;
import com.cloudpool.repository.FileMetadataRepository;
import com.cloudpool.repository.FileShareRepository;
import com.cloudpool.service.StorageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FileController {

    private final StorageService storageService;
    private final AuditLogService auditLogService;
    private final FileShareRepository fileShareRepository;
    private final FileMetadataRepository fileMetadataRepository;

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bucket", defaultValue = "default-pool") String bucket) {
        try {
            User user = getAuthenticatedUser();
            FileMetadata metadata = storageService.uploadFile(file, bucket, user);
            return ResponseEntity.ok(metadata);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<?> downloadFile(@PathVariable("id") UUID id) {
        try {
            User user = getAuthenticatedUser();
            byte[] data = storageService.downloadFile(id, user);
            
            // To find original filename
            String filename = "downloaded_file";
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<FileMetadata>> listFiles() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(storageService.listUserFiles(user));
    }

    @GetMapping("/buckets")
    public ResponseEntity<List<Bucket>> listBuckets() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(storageService.listUserBuckets(user));
    }

    @GetMapping("/logs")
    public ResponseEntity<List<AuditLog>> getLogs() {
        User user = getAuthenticatedUser();
        List<AuditLog> logs = auditLogService.getRecentLogs(user.getId(), 10);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/quota")
    public ResponseEntity<?> getQuota() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(storageService.getStorageQuota(user));
    }

    @PostMapping("/{fileId}/share")
    public ResponseEntity<?> shareFile(
            @PathVariable("fileId") UUID fileId,
            @RequestBody(required = false) ShareFileRequest request) {
        try {
            User user = getAuthenticatedUser();
            String email = request != null ? request.getSharedWithEmail() : null;
            Integer hours = request != null ? request.getExpiryHours() : null;
            FileShare share = storageService.shareFile(fileId, email, hours, user);
            return ResponseEntity.ok(share);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/shared/{token}")
    public ResponseEntity<?> downloadSharedFile(@PathVariable("token") String token) {
        try {
            FileShare share = fileShareRepository.findByToken(token)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid share link or token"));

            if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(LocalDateTime.now())) {
                return ResponseEntity.status(410).body(Map.of("error", "This share link has expired"));
            }

            FileMetadata metadata = fileMetadataRepository.findById(share.getFileId())
                    .orElseThrow(() -> new IllegalArgumentException("File not found"));

            byte[] data = storageService.downloadFileDirectly(metadata);

            // Audit Log (system action / anonymous download)
            auditLogService.log(metadata.getBucket().getUser(), "DOWNLOAD_SHARED_FILE", "FILE", metadata.getId().toString(),
                    String.format("Shared file '%s' downloaded via token by anonymous user", metadata.getOriginalName()));

            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (metadata.getMimeType() != null) {
                try {
                    mediaType = MediaType.parseMediaType(metadata.getMimeType());
                } catch (Exception ignored) {}
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getOriginalName() + "\"")
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @Data
    public static class ShareFileRequest {
        private String sharedWithEmail;
        private Integer expiryHours;
    }
}
