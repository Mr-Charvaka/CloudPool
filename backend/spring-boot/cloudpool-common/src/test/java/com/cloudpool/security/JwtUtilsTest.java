package com.cloudpool.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JwtUtilsTest {

    private static final String SECURE_SECRET = "d1f88c8078c1db294e82b71be5e8f6e80b2a75ffca79b9e6e6a1a8c3d6e5a6b0c2e3f4g5h6j7k8l9m0n1p2q3r4s5t6u7v8w9x0y1z2a3b4c5d6e7f8g9";
    private JwtUtils jwtUtils;

    @BeforeEach
    public void setUp() {
        jwtUtils = new JwtUtils(SECURE_SECRET, 3600000); // 1 hour expiration
    }

    @Test
    public void testTokenGenerationAndValidation() {
        String email = "test-user@cloudpool.com";
        String token = jwtUtils.generateToken(email);
        
        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));
        assertEquals(email, jwtUtils.getEmailFromToken(token));
    }

    @Test
    public void testTokenInvalidation() {
        assertFalse(jwtUtils.validateToken(null));
        assertFalse(jwtUtils.validateToken(""));
        assertFalse(jwtUtils.validateToken("invalid.jwt.token"));
        
        String email = "another-user@cloudpool.com";
        String token = jwtUtils.generateToken(email);
        String tamperedToken = token + "tamper";
        assertFalse(jwtUtils.validateToken(tamperedToken));
    }

    @Test
    public void testGetEmailFromInvalidTokenThrows() {
        assertThrows(IllegalArgumentException.class, () -> jwtUtils.getEmailFromToken(null));
        assertThrows(JwtException.class, () -> jwtUtils.getEmailFromToken("invalid-token"));
    }

    @Test
    public void testWeakSecretThrowsException() {
        String weakSecret = "short-secret-key-123456"; // Less than 64 characters
        assertThrows(IllegalArgumentException.class, () -> new JwtUtils(weakSecret, 3600000));
    }

    @Test
    public void testForbiddenDefaultSecretThrowsException() {
        String forbiddenSecret = "your-super-secret-key-that-is-at-least-64-characters-long-to-pass-length-check";
        assertThrows(IllegalArgumentException.class, () -> new JwtUtils(forbiddenSecret, 3600000));
    }

    @Test
    public void testTokenExpiration() throws InterruptedException {
        // Create JwtUtils with a 3000ms (3 seconds) expiration time to prevent flakiness under high CPU load
        JwtUtils transientJwtUtils = new JwtUtils(SECURE_SECRET, 3000);
        String email = "expiring-user@cloudpool.com";
        String token = transientJwtUtils.generateToken(email);
        
        assertTrue(transientJwtUtils.validateToken(token));
        
        // Wait for token to expire
        Thread.sleep(3500);
        
        assertFalse(transientJwtUtils.validateToken(token));
    }
}
