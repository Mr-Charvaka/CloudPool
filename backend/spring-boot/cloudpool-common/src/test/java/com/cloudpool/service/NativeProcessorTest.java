package com.cloudpool.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class NativeProcessorTest {

    private final NativeProcessor nativeProcessor = new NativeProcessor();

    @Test
    @DisplayName("calculateChecksum should return SHA-256 hex string for valid input")
    void testJvmChecksum() {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        String checksum = nativeProcessor.calculateChecksum(data);
        assertEquals(64, checksum.length());
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", checksum);
    }

    @Test
    @DisplayName("calculateChecksum should be deterministic for identical inputs")
    void testChecksumDeterministic() {
        byte[] data = "deterministic test".getBytes(StandardCharsets.UTF_8);
        String first = nativeProcessor.calculateChecksum(data);
        String second = nativeProcessor.calculateChecksum(data);
        assertEquals(first, second);
    }

    @Test
    @DisplayName("calculateChecksum should produce different results for different inputs")
    void testChecksumDifferentInputs() {
        String first = nativeProcessor.calculateChecksum("data one".getBytes(StandardCharsets.UTF_8));
        String second = nativeProcessor.calculateChecksum("data two".getBytes(StandardCharsets.UTF_8));
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("calculateChecksum should handle empty byte array")
    void testChecksumEmptyInput() {
        String checksum = nativeProcessor.calculateChecksum(new byte[0]);
        assertEquals(64, checksum.length());
    }

    @Test
    @DisplayName("calculateChecksum should handle large input")
    void testChecksumLargeInput() {
        byte[] large = new byte[1024 * 1024];
        String checksum = nativeProcessor.calculateChecksum(large);
        assertEquals(64, checksum.length());
    }

    @Test
    @DisplayName("compress and decompress should round-trip byte-for-byte identical")
    void testCompressDecompressRoundtrip() {
        byte[] original = "This is a test payload that should survive compression and decompression."
                .repeat(100).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = nativeProcessor.compress(original);
        byte[] decompressed = nativeProcessor.decompress(compressed);
        assertArrayEquals(original, decompressed);
    }

    @Test
    @DisplayName("compress should reduce size for compressible data")
    void testCompressReducesSize() {
        byte[] repetitive = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                .getBytes(StandardCharsets.UTF_8);
        byte[] compressed = nativeProcessor.compress(repetitive);
        assertTrue(compressed.length < repetitive.length,
                () -> "Compressed size " + compressed.length + " should be < original " + repetitive.length);
    }

    @Test
    @DisplayName("compress should handle empty input")
    void testCompressEmptyInput() {
        byte[] compressed = nativeProcessor.compress(new byte[0]);
        assertNotNull(compressed);
        byte[] decompressed = nativeProcessor.decompress(compressed);
        assertArrayEquals(new byte[0], decompressed);
    }

    @Test
    @DisplayName("decompress should throw RuntimeException for invalid gzip data")
    void testDecompressInvalidData() {
        byte[] invalid = "not gzip data".getBytes(StandardCharsets.UTF_8);
        assertThrows(RuntimeException.class, () -> nativeProcessor.decompress(invalid));
    }

    @Test
    @DisplayName("convertToWebp should return original data when native library is unavailable")
    void testConvertToWebpFallback() {
        byte[] original = "test image data".getBytes(StandardCharsets.UTF_8);
        byte[] result = nativeProcessor.convertToWebp(original);
        assertArrayEquals(original, result);
    }

    @Test
    @DisplayName("compress should handle binary data")
    void testCompressBinaryData() {
        byte[] binary = new byte[256];
        for (int i = 0; i < 256; i++) {
            binary[i] = (byte) i;
        }
        byte[] compressed = nativeProcessor.compress(binary);
        byte[] decompressed = nativeProcessor.decompress(compressed);
        assertArrayEquals(binary, decompressed);
    }

    @Test
    @DisplayName("calculateChecksum should produce hex characters only")
    void testChecksumHexOnly() {
        String checksum = nativeProcessor.calculateChecksum("hex test".getBytes(StandardCharsets.UTF_8));
        assertTrue(checksum.matches("[0-9a-f]+"), "Checksum should contain only hex characters");
    }
}