package com.cloudpool.util;
 
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
 
import java.util.*;
 
@Slf4j
@Component
public class FileUploadValidator {
 
    // Configuration
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
    private static final int MAX_FILENAME_LENGTH = 255;
    
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "docx", "xlsx", "pptx", "txt", "csv", "json",
        "jpg", "jpeg", "png", "gif", "webp",
        "mp4", "mov", "avi", "mkv",
        "zip", "rar", "7z", "tar", "gz"
    );
    
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
        "exe", "bat", "cmd", "com", "pif", "scr",
        "vbs", "js", "jar", "app", "bin"
    );
 
    /**
     * Validate file before upload
     */
    public void validateFile(MultipartFile file) throws IllegalArgumentException {
        // Check if file exists
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
 
        // Validate filename
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }
 
        // Check filename length
        if (originalName.length() > MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Filename exceeds maximum length of %d characters", MAX_FILENAME_LENGTH)
            );
        }
 
        // Prevent path traversal
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename: contains path separators");
        }
 
        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                String.format("File size (%d bytes) exceeds maximum limit of %d bytes", 
                    file.getSize(), MAX_FILE_SIZE)
            );
        }
 
        // Validate file extension
        String extension = getFileExtension(originalName).toLowerCase();
        
        if (DANGEROUS_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                String.format("File extension '%s' is not allowed for security reasons", extension)
            );
        }
 
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                String.format("File extension '%s' is not allowed. Allowed types: %s", 
                    extension, String.join(", ", ALLOWED_EXTENSIONS))
            );
        }
 
        // Validate MIME type matches extension
        validateMimeType(file);
        
        log.info("✅ File validation passed: {}", originalName);
    }
 
    /**
     * Validate MIME type
     */
    private void validateMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("MIME type cannot be determined");
        }
 
        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();
        
        Map<String, Set<String>> mimeTypeMap = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("txt", Set.of("text/plain")),
            Map.entry("csv", Set.of("text/csv", "application/csv", "application/vnd.ms-excel", "text/plain")),
            Map.entry("json", Set.of("application/json", "text/plain")),
            Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png", Set.of("image/png")),
            Map.entry("gif", Set.of("image/gif")),
            Map.entry("zip", Set.of("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        );
 
        if (mimeTypeMap.containsKey(extension)) {
            Set<String> allowedMimes = mimeTypeMap.get(extension);
            if (!allowedMimes.contains(contentType)) {
                throw new IllegalArgumentException(
                    String.format("Invalid MIME type '%s' for extension '%s'", contentType, extension)
                );
            }
        }
    }
 
    /**
     * Sanitize filename
     */
    public String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
 
    /**
     * Get file extension safely
     */
    public String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
