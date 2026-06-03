package com.cloudpool.util;

import lombok.extern.slf4j.Slf4j;
import java.io.File;

@Slf4j
public class RustBridge {

    private static boolean isLoaded = false;

    static {
        // Try loading library from standard paths first
        try {
            System.loadLibrary("cloudpool_rust");
            isLoaded = true;
            log.info("Loaded cloudpool_rust library from java.library.path");
        } catch (UnsatisfiedLinkError e) {
            log.warn("Could not load cloudpool_rust from java.library.path: {}", e.getMessage());
            // Try loading from workspace-relative build targets
            try {
                String userDir = System.getProperty("user.dir");
                String libName = System.mapLibraryName("cloudpool_rust");
                
                // Paths to try relative to spring-boot directory
                String[] potentialPaths = {
                    "../rust/target/release/" + libName,
                    "../rust/target/debug/" + libName,
                    "backend/rust/target/release/" + libName,
                    "backend/rust/target/debug/" + libName,
                    "target/release/" + libName,
                    "target/debug/" + libName
                };

                for (String relPath : potentialPaths) {
                    File libFile = new File(userDir, relPath);
                    if (libFile.exists()) {
                        System.load(libFile.getAbsolutePath());
                        isLoaded = true;
                        log.info("Loaded cloudpool_rust from absolute path: {}", libFile.getAbsolutePath());
                        break;
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to load native library relative paths: {}", ex.getMessage());
            }
        }

        if (!isLoaded) {
            log.error("CRITICAL: cloudpool_rust library could not be loaded! Native methods will throw UnsatisfiedLinkError.");
        }
    }

    public static boolean isLibraryLoaded() {
        return isLoaded;
    }

    /**
     * Compute SHA-256 checksum of data using native Rust library
     */
    public static native String calculateChecksum(byte[] data);

    /**
     * Compress data using native Rust Gzip implementation
     */
    public static native byte[] compress(byte[] data);

    /**
     * Decompress data using native Rust Gzip implementation
     */
    public static native byte[] decompress(byte[] data);

    /**
     * Parse CSV file using native Rust implementation
     */
    public static native String parseCsv(String filePath);

    /**
     * Convert image data to WebP format using native Rust implementation
     */
    public static native byte[] convertToWebp(byte[] data);
}
