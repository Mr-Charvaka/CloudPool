package com.cloudpool.service;

import com.cloudpool.model.RefreshToken;
import com.cloudpool.model.User;
import com.cloudpool.repository.RefreshTokenRepository;
import com.cloudpool.repository.UserRepository;
import com.cloudpool.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;

    @Value("${cloudpool.jwt.refresh-token-expiration-days:7}")
    private int refreshTokenExpirationDays;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public record TokenResult(String accessToken, String refreshToken, UUID familyId) {}

    @Transactional
    public TokenResult createTokenPair(UUID userId, String email) {
        String accessToken = jwtUtils.generateToken(email);
        String rawRefreshToken = generateRawToken();
        UUID familyId = UUID.randomUUID();
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken entity = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(LocalDateTime.now().plusDays(refreshTokenExpirationDays))
                .build();
        refreshTokenRepository.save(entity);

        return new TokenResult(accessToken, rawRefreshToken, familyId);
    }

    @Transactional
    public TokenResult rotateRefreshToken(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        Optional<RefreshToken> opt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (opt.isEmpty()) {
            throw new com.cloudpool.exception.CloudPoolException("Refresh token not found");
        }

        RefreshToken current = opt.get();

        if (current.isRevoked()) {
            log.warn("REUSE DETECTED: refresh token family {} for user {} was already rotated. " +
                     "Revoking ALL sessions for this user (token theft indicator).",
                     current.getFamilyId(), current.getUserId());
            refreshTokenRepository.revokeAllForUser(current.getUserId());
            throw new com.cloudpool.exception.CloudPoolException("Refresh token reuse detected. All sessions revoked for security.");
        }

        if (current.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new com.cloudpool.exception.CloudPoolException("Refresh token expired");
        }

        current.setRevoked(true);
        refreshTokenRepository.save(current);

        User user = userRepository.findById(current.getUserId())
                .orElseThrow(() -> new com.cloudpool.exception.CloudPoolException("User not found"));

        return createTokenPair(user.getId(), user.getEmail());
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        long deleted = refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired refresh tokens", deleted);
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return "cp_refresh_" + HexFormat.of().formatHex(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
