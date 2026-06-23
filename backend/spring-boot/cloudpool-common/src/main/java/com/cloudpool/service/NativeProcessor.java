package com.cloudpool.service;

import com.cloudpool.util.RustBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service
public class NativeProcessor {

    public String calculateChecksum(byte[] data) {
        if (RustBridge.isLibraryLoaded()) {
            return RustBridge.calculateChecksum(data);
        }
        return jvmChecksum(data);
    }

    public byte[] compress(byte[] data) {
        if (RustBridge.isLibraryLoaded()) {
            return RustBridge.compress(data);
        }
        return jvmGzipCompress(data);
    }

    public byte[] decompress(byte[] data) {
        if (RustBridge.isLibraryLoaded()) {
            return RustBridge.decompress(data);
        }
        return jvmGzipDecompress(data);
    }

    public byte[] convertToWebp(byte[] data) {
        if (RustBridge.isLibraryLoaded()) {
            return RustBridge.convertToWebp(data);
        }
        log.warn("WebP conversion not available without native library");
        return data;
    }

    private String jvmChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private byte[] jvmGzipCompress(byte[] data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
                gzip.write(data);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("GZip compression failed", e);
        }
    }

    private byte[] jvmGzipDecompress(byte[] data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPInputStream gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(data))) {
                byte[] buffer = new byte[8192];
                int len;
                int totalLen = 0;
                int MAX_SIZE = 100 * 1024 * 1024; // 100MB limit for gzip bomb protection
                while ((len = gzip.read(buffer)) != -1) {
                    totalLen += len;
                    if (totalLen > MAX_SIZE) {
                        throw new RuntimeException("GZip payload exceeds maximum allowed size (potential zip bomb)");
                    }
                    bos.write(buffer, 0, len);
                }
            }
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("GZip decompression failed", e);
        }
    }
}