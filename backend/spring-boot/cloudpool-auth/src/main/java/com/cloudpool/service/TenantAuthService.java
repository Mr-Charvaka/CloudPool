package com.cloudpool.service;

import com.cloudpool.model.TenantSession;
import com.cloudpool.model.TenantUser;
import com.cloudpool.repository.TenantSessionRepository;
import com.cloudpool.repository.TenantUserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantAuthService {

    private final TenantUserRepository userRepository;
    private final TenantSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${cloudpool.jwt.secret}")
    private String jwtSecret;

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public TenantUser register(UUID projectId, String email, String password, String displayName, String metadata) {
        if (userRepository.findByProjectIdAndEmail(projectId, email).isPresent()) {
            throw new IllegalArgumentException("User with this email already exists in this project");
        }

        TenantUser user = new TenantUser();
        user.setProjectId(projectId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setMetadata(metadata);

        return userRepository.save(user);
    }

    @Transactional
    public AuthResult login(UUID projectId, String email, String password) {
        TenantUser user = userRepository.findByProjectIdAndEmail(projectId, email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Generate Access Token (JWT)
        String accessToken = Jwts.builder()
                .subject(user.getId().toString())
                .claim("projectId", projectId.toString())
                .claim("email", user.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000)) // 1 hour
                .signWith(getSignKey())
                .compact();

        // Generate Refresh Token
        String refreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        TenantSession session = new TenantSession();
        session.setUser(user);
        session.setProjectId(projectId);
        session.setRefreshToken(refreshToken);
        session.setExpiresAt(LocalDateTime.now().plusDays(30)); // 30 days
        sessionRepository.save(session);

        return new AuthResult(accessToken, refreshToken, user);
    }

    @Transactional
    public AuthResult refreshToken(String refreshToken) {
        TenantSession session = sessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            sessionRepository.delete(session);
            throw new IllegalArgumentException("Refresh token expired");
        }

        TenantUser user = session.getUser();

        String accessToken = Jwts.builder()
                .subject(user.getId().toString())
                .claim("projectId", user.getProjectId().toString())
                .claim("email", user.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(getSignKey())
                .compact();

        return new AuthResult(accessToken, refreshToken, user);
    }

    @Transactional
    public void logout(String refreshToken) {
        sessionRepository.deleteByRefreshToken(refreshToken);
    }

    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional
    public void purgeExpiredSessions() {
        int deleted = sessionRepository.deleteExpiredSessions(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Purged {} expired tenant auth sessions", deleted);
        }
    }

    public record AuthResult(String accessToken, String refreshToken, TenantUser user) {}
}
