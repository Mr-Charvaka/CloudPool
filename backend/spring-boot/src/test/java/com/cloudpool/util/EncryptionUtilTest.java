package com.cloudpool.util;
 
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
 
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
 
import static org.junit.jupiter.api.Assertions.*;
 
class EncryptionUtilTest {
 
    private EncryptionUtil encryptionUtil;
    private final String defaultKey = "d1f88c8078c1db294e82b71be5e8f6e80b2a75ffca79b9e6e6a1a8c3d6e5a6b0c2e3f4g5h6j7k8l9m0n1p2q3r4s5t6u7v8w9x0y1z2a3b4c5d6e7f8g9";
 
    @BeforeEach
    void setUp() {
        encryptionUtil = new EncryptionUtil(defaultKey);
    }
 
    @Test
    void testEncryptAndDecryptGcm() {
        String plaintext = "my-secure-password-456";
        String ciphertext = encryptionUtil.encrypt(plaintext);
        
        assertNotNull(ciphertext);
        assertNotEquals(plaintext, ciphertext);
        
        String decrypted = encryptionUtil.decrypt(ciphertext);
        assertEquals(plaintext, decrypted);
    }
 
    @Test
    void testEncryptionIsSemantic() {
        // Different ciphertexts should be generated for the same plaintext due to random IVs
        String plaintext = "same-password";
        String ciphertext1 = encryptionUtil.encrypt(plaintext);
        String ciphertext2 = encryptionUtil.encrypt(plaintext);
        
        assertNotNull(ciphertext1);
        assertNotNull(ciphertext2);
        assertNotEquals(ciphertext1, ciphertext2);
        
        // Both must decrypt to the same plaintext
        assertEquals(plaintext, encryptionUtil.decrypt(ciphertext1));
        assertEquals(plaintext, encryptionUtil.decrypt(ciphertext2));
    }
 
    @Test
    void testDecryptLegacyEcbFallback() throws Exception {
        String plaintext = "legacy-password-789";
        
        // Manually encrypt using legacy AES-ECB
        byte[] keyBytes = Base64.getDecoder().decode(defaultKey);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, 0, 32, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        String legacyCiphertext = Base64.getEncoder().encodeToString(encryptedBytes);
        
        // Assert EncryptionUtil.decrypt can decrypt it via fallback
        String decrypted = encryptionUtil.decrypt(legacyCiphertext);
        assertEquals(plaintext, decrypted);
    }
 
    @Test
    void testDecryptNullOrBlank() {
        assertNull(encryptionUtil.decrypt(null));
        assertNull(encryptionUtil.decrypt(""));
        assertNull(encryptionUtil.decrypt("   "));
    }
 
    @Test
    void testEncryptNullOrBlank() {
        assertNull(encryptionUtil.encrypt(null));
        assertNull(encryptionUtil.encrypt(""));
        assertNull(encryptionUtil.encrypt("   "));
    }
}
