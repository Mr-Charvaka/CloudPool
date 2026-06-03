package com.cloudpool.util;

import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class JniTest {
    public static void main(String[] args) {
        log.info("==================================================");
        log.info("Checking JNI Library Loading...");
        if (!RustBridge.isLibraryLoaded()) {
            log.error("ERROR: JNI Library not loaded!");
            System.exit(1);
        }
        log.info("SUCCESS: JNI Library loaded!");

        String testStr = "hello world native FFI check";
        byte[] bytes = testStr.getBytes(StandardCharsets.UTF_8);

        // 1. Test checksum
        log.info("Calculating checksum...");
        String checksum = RustBridge.calculateChecksum(bytes);
        log.info("SUCCESS: Checksum: {}", checksum);
        if (checksum == null || checksum.length() != 64) {
            log.error("ERROR: Invalid checksum length!");
            System.exit(1);
        }

        // 2. Test compression
        log.info("Compressing content...");
        byte[] compressed = RustBridge.compress(bytes);
        log.info("SUCCESS: Compressed size: {} bytes", compressed.length);

        // 3. Test decompression
        log.info("Decompressing content...");
        byte[] decompressed = RustBridge.decompress(compressed);
        String decompressedStr = new String(decompressed, StandardCharsets.UTF_8);
        log.info("SUCCESS: Decompressed content: \"{}\"", decompressedStr);

        if (!testStr.equals(decompressedStr)) {
            log.error("ERROR: Content mismatch after decompressing!");
            System.exit(1);
        }

        log.info("ALL JNI BINDINGS STABLE AND WORKING 100% CORRECTLY!");
        log.info("==================================================");
    }
}
