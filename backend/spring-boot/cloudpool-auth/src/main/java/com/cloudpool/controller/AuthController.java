package com.cloudpool.controller;

import com.cloudpool.model.Bucket;
import com.cloudpool.model.User;
import com.cloudpool.repository.BucketRepository;
import com.cloudpool.repository.UserRepository;
import com.cloudpool.security.JwtUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.cloudpool.service.AuditLogService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final BucketRepository bucketRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final com.cloudpool.service.CacheService cacheService;
    private final AuditLogService auditLogService;
    private final com.cloudpool.service.MetricsService metricsService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already in use"));
        }

        User user = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role("USER")
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

        String token = jwtUtils.generateToken(savedUser.getEmail());
        
        auditLogService.log(savedUser, AuditLogService.ACTION_REGISTER, null, null, "User registered successfully");
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("name", savedUser.getName());
        response.put("email", savedUser.getEmail());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            auditLogService.log(null, AuditLogService.ACTION_LOGIN_FAILED, null, null, "Failed login attempt for email: " + request.getEmail());
            metricsService.incrementAuthFailure();
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
        }

        if (!user.isActive()) {
            auditLogService.log(user, "USER_SUSPENDED_LOGIN_ATTEMPT", null, null, "Suspended user login attempt");
            metricsService.incrementAuthFailure();
            return ResponseEntity.status(403).body(Map.of("error", "User account is suspended"));
        }

        String token = jwtUtils.generateToken(user.getEmail());
        auditLogService.log(user, AuditLogService.ACTION_LOGIN, null, null, "User logged in successfully");
        metricsService.incrementAuthSuccess();

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("name", user.getName());
        response.put("email", user.getEmail());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // Blacklist for 1 hour (matching standard JWT lifespan)
            cacheService.blacklistToken(token, 3600000);
            
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof User user) {
                auditLogService.log(user, AuditLogService.ACTION_LOGOUT, null, null, "User logged out");
            }
            
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Missing Authorization header"));
    }

    @PostMapping("/oauth-credentials")
    public ResponseEntity<?> saveOAuthCredentials(@RequestBody OAuthCredentialsRequest request) {
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
        private String clientId;
        private String clientSecret;
    }

    @Data
    public static class RegisterRequest {
        private String email;
        private String password;
        private String name;
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }
}
