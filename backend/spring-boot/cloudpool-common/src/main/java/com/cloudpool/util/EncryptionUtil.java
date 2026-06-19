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
import java.util.Arrays;
 
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
 
    public EncryptionUtil(@Value("${cloudpool.encryption.master-key}") String masterKeyBase64,
                          @Value("${cloudpool.encryption.salt}") String saltBase64) {
        try {
            // Strip whitespace/newlines
            String cleanKey = masterKeyBase64.trim();
            byte[] salt = Base64.getDecoder().decode(saltBase64.trim());
            
            byte[] derivedKey = hkdfSha256(cleanKey.getBytes(StandardCharsets.UTF_8),
                                salt,
                                "cloudpool-aes-gcm-v1".getBytes(StandardCharsets.UTF_8), 32);
            this.secretKey = new SecretKeySpec(derivedKey, 0, 32, ALGORITHM);
            
            log.info("✅ Encryption key initialized successfully with HKDF");
        } catch (Exception e) {
            throw new com.cloudpool.exception.CloudPoolException("Failed to initialize encryption master key config", e);
        }
    }
 
    /**
     * Encrypt plaintext using AES-GCM and Envelope Encryption
     */
    public byte[] encrypt(byte[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            return null;
        }
        
        try {
            // Envelope Encryption: Generate DEK
            byte[] dekBytes = new byte[32];
            secureRandom.nextBytes(dekBytes);
            SecretKey dek = new SecretKeySpec(dekBytes, ALGORITHM);

            // Encrypt DEK with KEK (secretKey)
            byte[] kekIv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(kekIv);
            Cipher kekCipher = Cipher.getInstance(TRANSFORMATION);
            kekCipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, kekIv));
            byte[] encryptedDek = kekCipher.doFinal(dekBytes);

            // Encrypt data with DEK
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, dek, parameterSpec);
            
            byte[] ciphertext = cipher.doFinal(plaintext);
            
            // Format: [kekIv: 12] [encryptedDek length: 2] [encryptedDek] [iv: 12] [ciphertext]
            byte[] encryptedBuffer = ByteBuffer.allocate(kekIv.length + 2 + encryptedDek.length + iv.length + ciphertext.length)
                    .put(kekIv)
                    .putShort((short) encryptedDek.length)
                    .put(encryptedDek)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            
            Arrays.fill(dekBytes, (byte) 0); // Zeroize DEK
            return encryptedBuffer;
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new com.cloudpool.exception.CloudPoolException("Failed to encrypt data", e);
        }
    }
 
    /**
     * Decrypt ciphertext using Envelope Encryption
     */
    public byte[] decrypt(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length == 0) {
            return null;
        }
        
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(ciphertext);
            
            byte[] kekIv = new byte[IV_LENGTH_BYTES];
            byteBuffer.get(kekIv);
            
            short encryptedDekLength = byteBuffer.getShort();
            byte[] encryptedDek = new byte[encryptedDekLength];
            byteBuffer.get(encryptedDek);
            
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byteBuffer.get(iv);
            
            byte[] ciphertextBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertextBytes);
            
            // Decrypt DEK
            Cipher kekCipher = Cipher.getInstance(TRANSFORMATION);
            kekCipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, kekIv));
            byte[] dekBytes = kekCipher.doFinal(encryptedDek);
            SecretKey dek = new SecretKeySpec(dekBytes, ALGORITHM);
            
            // Decrypt Data
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, dek, parameterSpec);
            
            byte[] decryptedBytes = cipher.doFinal(ciphertextBytes);
            
            Arrays.fill(dekBytes, (byte) 0); // Zeroize DEK
            return decryptedBytes;
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

    private static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = hmac.doFinal(ikm);

        hmac.init(new SecretKeySpec(prk, "HmacSHA256"));
        hmac.update(info);
        hmac.update((byte) 1);
        return java.util.Arrays.copyOf(hmac.doFinal(), length);
    }
}

