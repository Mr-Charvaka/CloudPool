package com.cloudpool.util;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class FileUploadValidator {

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB global limit
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("pdf", "png", "jpg", "jpeg", "docx", "csv", "txt");
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "application/pdf", "image/png", "image/jpeg", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/csv", "text/plain"
    );

    // Magic number signatures: first bytes that identify a file format
    private static final Map<String, List<byte[]>> MAGIC_SIGNATURES = Map.of(
        "pdf", List.of(new byte[]{0x25, 0x50, 0x44, 0x46}),                    // %PDF
        "png", List.of(new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}), // PNG
        "jpg", List.of(new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF}),        // JPEG
        "jpeg", List.of(new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF})        // JPEG
    );

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed (100MB)");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("File must have an extension");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type not allowed: ." + extension);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Invalid content type: " + contentType);
        }

        // Magic number validation — check actual file bytes, not just extension
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[16];
            int read = is.read(header);
            if (read < 4) {
                throw new SecurityException("File too small to validate");
            }
            List<byte[]> signatures = MAGIC_SIGNATURES.get(extension);
            if (signatures != null) {
                boolean matches = false;
                for (byte[] sig : signatures) {
                    if (bytesStartWith(header, sig, read)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) {
                    throw new SecurityException("File content does not match extension: " + extension);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read file content for validation", e);
        }
    }

    private boolean bytesStartWith(byte[] data, byte[] prefix, int dataLen) {
        if (dataLen < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    public String sanitizeFilename(String filename) {
        if (filename == null) return "unknown";
        String sanitized = filename.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
        if (sanitized.startsWith(".") || sanitized.startsWith("..")) {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }
}
