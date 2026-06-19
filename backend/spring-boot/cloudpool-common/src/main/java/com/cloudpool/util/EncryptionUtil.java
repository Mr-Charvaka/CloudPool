package com.cloudpool.util;
 
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
 
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
 
@Component
@Slf4j
public class EncryptionUtil {
 
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BIT = 128;
    
    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();
 
    public EncryptionUtil(@Value("${cloudpool.encryption.master-key}") String masterKeyBase64) {
        try {
            // Strip whitespace/newlines
            String cleanKey = masterKeyBase64.trim();
            
            byte[] derivedKey = hkdfSha256(cleanKey.getBytes(StandardCharsets.UTF_8),
                                "cloudpool-db-conn-v1".getBytes(StandardCharsets.UTF_8), 32);
            this.secretKey = new SecretKeySpec(derivedKey, 0, 32, ALGORITHM);
            
            log.info("✅ Encryption key initialized successfully with HKDF");
        } catch (Exception e) {
            throw new com.cloudpool.exception.CloudPoolException("Failed to initialize encryption master key config", e);
        }
    }
 
    /**
     * Encrypt plaintext using AES-GCM
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and ciphertext: [12 bytes IV][ciphertext]
            byte[] encryptedBuffer = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            
            return Base64.getEncoder().encodeToString(encryptedBuffer);
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new com.cloudpool.exception.CloudPoolException("Failed to encrypt data", e);
        }
    }
 
    /**
     * Decrypt ciphertext supporting both new AES-GCM format and legacy AES-ECB fallback
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            // GCM ciphertext payload must at least contain 12-byte IV + 16-byte GCM tag
            if (decoded.length < IV_LENGTH_BYTES + 16) {
                throw new IllegalArgumentException("Invalid ciphertext format");
            }
            
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byteBuffer.get(iv);
            
            byte[] ciphertextBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertextBytes);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] decryptedBytes = cipher.doFinal(ciphertextBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage());
            throw new com.cloudpool.exception.CloudPoolException("Failed to decrypt data", e);
        }
    }
 

 
    /**
     * Generate a new master key (run once during setup)
     */
    public static String generateMasterKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE);
            SecretKey key = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new com.cloudpool.exception.CloudPoolException("Failed to generate master key", e);
        }
    }

    private static byte[] hkdfSha256(byte[] ikm, byte[] info, int length) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(new byte[32], "HmacSHA256")); // salt = zeros, per HKDF spec when no salt given
        byte[] prk = hmac.doFinal(ikm);

        hmac.init(new SecretKeySpec(prk, "HmacSHA256"));
        hmac.update(info);
        hmac.update((byte) 1);
        return java.util.Arrays.copyOf(hmac.doFinal(), length);
    }
}

