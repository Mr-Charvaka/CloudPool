package com.cloudpool.controller;

import com.cloudpool.model.Bucket;
import com.cloudpool.model.User;
import com.cloudpool.repository.BucketRepository;
import com.cloudpool.repository.UserRepository;
import com.cloudpool.security.JwtUtils;
import com.cloudpool.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import com.cloudpool.service.AuditLogService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor

public class AuthController {

    private final UserRepository userRepository;
    private final BucketRepository bucketRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final com.cloudpool.service.CacheService cacheService;
    private final AuditLogService auditLogService;
    private final com.cloudpool.service.MetricsService metricsService;
    private final RefreshTokenService refreshTokenService;
    private final com.cloudpool.security.RefreshRateLimiter refreshRateLimiter;

    @Value("${cloudpool.jwt.expiration-ms:3600000}")
    private long jwtExpirationMs;

    @Value("${cloudpool.jwt.refresh-token-expiration-days:7}")
    private int refreshTokenExpirationDays;

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            return ResponseEntity.ok(Map.of(
                "name", user.getName(),
                "email", user.getEmail()
            ));
        }
        return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already in use"));
        }

        User user = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(com.cloudpool.model.enums.Role.USER)
                .active(true)
                .build();

        User savedUser = userRepository.save(user);

        // Auto-create default storage pool (bucket)
        Bucket defaultBucket = Bucket.builder()
                .user(savedUser)
                .name("default-pool")
                .description("Default storage pool created automatically")
                .isPublic(false)
                .build();
        bucketRepository.save(defaultBucket);

        var tokenPair = refreshTokenService.createTokenPair(savedUser.getId(), savedUser.getEmail());
        setTokenCookie(httpRequest, response, "cp_token", tokenPair.accessToken(), (int) (jwtExpirationMs / 1000));
        setTokenCookie(httpRequest, response, "cp_refresh", tokenPair.refreshToken(), refreshTokenExpirationDays * 86400);

        auditLogService.log(savedUser, AuditLogService.ACTION_REGISTER, null, null, "User registered successfully");

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("token", tokenPair.accessToken());
        responseBody.put("refreshToken", tokenPair.refreshToken());
        responseBody.put("name", savedUser.getName());
        responseBody.put("email", savedUser.getEmail());

        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        // Dummy hash for bcrypt to prevent timing attacks
        String dummyHash = "$2a$10$xyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyzxyz";
        boolean passwordMatches = passwordEncoder.matches(
                request.getPassword(), 
                user != null ? user.getPasswordHash() : dummyHash
        );

        if (user == null || !passwordMatches) {
            auditLogService.log(null, AuditLogService.ACTION_LOGIN_FAILED, null, null, "Failed login attempt for email: " + request.getEmail());
            metricsService.incrementAuthFailure();
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
        }

        if (!user.isActive()) {
            auditLogService.log(user, "USER_SUSPENDED_LOGIN_ATTEMPT", null, null, "Suspended user login attempt");
            metricsService.incrementAuthFailure();
            return ResponseEntity.status(403).body(Map.of("error", "User account is suspended"));
        }

        var tokenPair = refreshTokenService.createTokenPair(user.getId(), user.getEmail());
        setTokenCookie(httpRequest, response, "cp_token", tokenPair.accessToken(), (int) (jwtExpirationMs / 1000));
        setTokenCookie(httpRequest, response, "cp_refresh", tokenPair.refreshToken(), refreshTokenExpirationDays * 86400);

        auditLogService.log(user, AuditLogService.ACTION_LOGIN, null, null, "User logged in successfully");
        metricsService.incrementAuthSuccess();

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("token", tokenPair.accessToken());
        responseBody.put("refreshToken", tokenPair.refreshToken());
        responseBody.put("name", user.getName());
        responseBody.put("email", user.getEmail());

        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body,
                                     HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = body.get("refreshToken");
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "refreshToken is required"));
        }

        String ip = request.getRemoteAddr();
        String familyHint = rawRefreshToken.length() > 16 ? rawRefreshToken.substring(0, 16) : rawRefreshToken;
        try {
            refreshRateLimiter.checkRateLimit(ip, familyHint);
        } catch (Exception e) {
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        }

        try {
            var tokenPair = refreshTokenService.rotateRefreshToken(rawRefreshToken);
            setTokenCookie(request, response, "cp_token", tokenPair.accessToken(), (int) (jwtExpirationMs / 1000));
            setTokenCookie(request, response, "cp_refresh", tokenPair.refreshToken(), refreshTokenExpirationDays * 86400);
            return ResponseEntity.ok(Map.of(
                "token", tokenPair.accessToken(),
                "refreshToken", tokenPair.refreshToken()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/refresh-cookie")
    public ResponseEntity<?> refreshFromCookie(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = null;
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("cp_refresh".equals(c.getName())) {
                    rawRefreshToken = c.getValue();
                    break;
                }
            }
        }

        if (rawRefreshToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No refresh token cookie"));
        }

        String ip = request.getRemoteAddr();
        String familyHint = rawRefreshToken.length() > 16 ? rawRefreshToken.substring(0, 16) : rawRefreshToken;
        try {
            refreshRateLimiter.checkRateLimit(ip, familyHint);
        } catch (Exception e) {
            return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
        }

        try {
            var tokenPair = refreshTokenService.rotateRefreshToken(rawRefreshToken);
            setTokenCookie(request, response, "cp_token", tokenPair.accessToken(), (int) (jwtExpirationMs / 1000));
            setTokenCookie(request, response, "cp_refresh", tokenPair.refreshToken(), refreshTokenExpirationDays * 86400);
            return ResponseEntity.ok(Map.of("token", tokenPair.accessToken(), "refreshToken", tokenPair.refreshToken()));
        } catch (Exception e) {
            clearTokenCookie(response, "cp_token");
            clearTokenCookie(response, "cp_refresh");
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        if (token == null && request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("cp_token".equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        if (token != null) {
            cacheService.blacklistToken(token, (int) jwtExpirationMs);
        }

        clearTokenCookie(response, "cp_token");
        clearTokenCookie(response, "cp_refresh");

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            refreshTokenService.revokeAllForUser(user.getId());
            auditLogService.log(user, AuditLogService.ACTION_LOGOUT, null, null, "User logged out");
        }

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    private void setTokenCookie(HttpServletRequest request, HttpServletResponse response, String name, String value, int maxAgeSecs) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String scheme = request.getScheme();
        cookie.setSecure("https".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(forwardedProto));
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSecs);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private void clearTokenCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    @PostMapping("/oauth-credentials")
    public ResponseEntity<?> saveOAuthCredentials(@Valid @RequestBody OAuthCredentialsRequest request) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof User)) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }
        User user = (User) principal;
        User dbUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        dbUser.setCustomClientId(request.getClientId() != null ? request.getClientId().trim() : null);
        dbUser.setCustomClientSecret(request.getClientSecret() != null ? request.getClientSecret().trim() : null);
        userRepository.save(dbUser);

        return ResponseEntity.ok(Map.of("message", "OAuth App credentials saved successfully"));
    }

    @Data
    public static class OAuthCredentialsRequest {
        @jakarta.validation.constraints.NotBlank

        private String clientId;
        @jakarta.validation.constraints.NotBlank
        private String clientSecret;
    }

    @Data
    public static class RegisterRequest {
        @jakarta.validation.constraints.NotBlank

        @jakarta.validation.constraints.Email

        private String email;
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(min=8)
        private String password;
        @jakarta.validation.constraints.NotBlank
        private String name;
    }

    @Data
    public static class LoginRequest {
        @jakarta.validation.constraints.NotBlank

        @jakarta.validation.constraints.Email

        private String email;
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(min=8)
        private String password;
    }
}

