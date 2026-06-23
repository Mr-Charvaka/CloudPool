package com.cloudpool.util;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class FileUploadValidator {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("pdf", "png", "jpg", "jpeg", "docx", "csv", "txt");
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "application/pdf", "image/png", "image/jpeg", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/csv", "text/plain"
    );

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed (10MB)");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("File must have an extension");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type not allowed");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Invalid content type");
        }

        // Basic Malware/Signature Check
        try {
            byte[] bytes = file.getBytes();
            String contentStart = new String(bytes, 0, Math.min(bytes.length, 1024)).toLowerCase();
            if (contentStart.contains("<script>") || contentStart.contains("<?php") || contentStart.contains("mz")) {
                // "mz" is DOS executable header, `<script>` for XSS, `<?php` for code exec.
                // Simple heuristics for an MVP.
                throw new SecurityException("Malicious file content detected");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read file content for validation", e);
        }
    }

    public String sanitizeFilename(String filename) {
        if (filename == null) return "unknown";
        return filename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }
}
