package com.cloudpool.security;
 
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
 
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
 
@Component
@Slf4j
public class JwtUtils {
 
    private final SecretKey signKey;
    private final long jwtExpirationMs;
    private static final int MIN_SECRET_LENGTH = 64; // 512 bits minimum
 
    public JwtUtils(@Value("${cloudpool.jwt.secret}") String jwtSecret,
                    @Value("${cloudpool.jwt.expiration-ms:3600000}") long jwtExpirationMs) {
        
        // Validate that secret is configured
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalArgumentException(
                "❌ CRITICAL: cloudpool.jwt.secret must be configured. " +
                "Set it in application.yml or environment variable JWT_SECRET / CLOUDPOOL_JWT_SECRET");
        }
        
        // Validate secret length
        if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException(
                String.format("❌ JWT secret must be at least %d characters. " +
                    "Current length: %d. Generate a new secret with a secure key.", MIN_SECRET_LENGTH, jwtSecret.length()));
        }
        
        // Check if using default weak secret (basic sanity check)
        if (jwtSecret.contains("your-super-secret") || jwtSecret.equals("my-secret-key")) {
            throw new IllegalArgumentException(
                "❌ CRITICAL: Using default JWT secret is forbidden! " +
                "Generate a strong secret and set it in cloudpool.jwt.secret");
        }
        
        try {
            byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            this.signKey = Keys.hmacShaKeyFor(keyBytes); // Uses HS512 automatically
            this.jwtExpirationMs = jwtExpirationMs;
            log.info("✅ JWT configuration validated successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JWT signing key", e);
        }
    }
 
    /**
     * Generate JWT token with user email as subject
     */
    public String generateToken(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        
        try {
            return Jwts.builder()
                    .subject(email)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                    .signWith(signKey)  // Uses HS512
                    .compact();
        } catch (Exception e) {
            log.error("Failed to generate JWT token for email: {}", email);
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }
 
    /**
     * Extract email from JWT token
     */
    public String getEmailFromToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }
        
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException e) {
            log.warn("Invalid JWT token received: {}", e.getMessage());
            throw new JwtException("Invalid JWT token", e);
        }
    }
 
    /**
     * Validate JWT token
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        
        try {
            Jwts.parser().verifyWith(signKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }
 
    /**
     * Get remaining validity in milliseconds
     */
    public long getTokenRemainingValidity(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration().getTime() - System.currentTimeMillis();
        } catch (JwtException e) {
            return 0;
        }
    }
}
