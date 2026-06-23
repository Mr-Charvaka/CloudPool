package com.cloudpool.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RustBridge {

    private static volatile boolean libraryLoaded = false;

    static {
        try {
            System.loadLibrary("cloudpool_rust"); // matches the Cargo crate output name
            libraryLoaded = true;
            log.info("Native cloudpool_rust library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            log.warn("Native cloudpool_rust library not found — falling back to JVM implementations: {}", e.getMessage());
        }
    }

    private RustBridge() {}

    public static boolean isLibraryLoaded() {
        return libraryLoaded;
    }

    public static native String calculateChecksum(byte[] data);
    public static native byte[] compress(byte[] data);
    public static native byte[] decompress(byte[] data);
    public static native byte[] convertToWebp(byte[] data);
    public static native float cosineSimilarity(float[] vec1, float[] vec2);
}
