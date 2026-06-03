package com.cloudpool.controller;

import com.cloudpool.dto.ApiKeyUsageLogDto;
import com.cloudpool.model.ApiKey;
import com.cloudpool.model.User;
import com.cloudpool.repository.ApiKeyRepository;
import com.cloudpool.service.ApiKeyUsageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.cloudpool.service.AuditLogService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ApiKeyController {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyUsageService apiKeyUsageService;
    private final AuditLogService auditLogService;

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping
    public ResponseEntity<List<ApiKey>> listKeys() {
        User user = getAuthenticatedUser();
        List<ApiKey> keys = apiKeyRepository.findByUser(user);
        // Clear hashes before returning for safety
        keys.forEach(k -> k.setKeyHash("[REDACTED]"));
        return ResponseEntity.ok(keys);
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateKey(@RequestBody GenerateKeyRequest request) {
        User user = getAuthenticatedUser();
        
        // Generate plain key: cp_live_ + 32 random alphanumeric characters
        String plainKey = "cp_live_" + generateRandomString(32);
        String hashedKey = hashApiKey(plainKey);

        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .name(request.getName())
                .description(request.getDescription())
                .keyHash(hashedKey)
                .active(true)
                .createdAt(LocalDateTime.now())
                .expiresAt(request.getDaysToLive() > 0 ? LocalDateTime.now().plusDays(request.getDaysToLive()) : null)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
 
        auditLogService.log(user, AuditLogService.ACTION_API_KEY_CREATE, "API_KEY", saved.getId().toString(), "Generated API key: " + saved.getName());

        Map<String, Object> response = new HashMap<>();
        response.put("id", saved.getId());
        response.put("name", saved.getName());
        response.put("apiKey", plainKey); // Plaintext key shown only once!
        response.put("createdAt", saved.getCreatedAt());
        response.put("expiresAt", saved.getExpiresAt());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteKey(@PathVariable UUID id) {
        User user = getAuthenticatedUser();
        Optional<ApiKey> keyOpt = apiKeyRepository.findById(id);
        if (keyOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ApiKey key = keyOpt.get();
        if (!key.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        apiKeyRepository.delete(key);
        auditLogService.log(user, AuditLogService.ACTION_API_KEY_DELETE, "API_KEY", key.getId().toString(), "Deleted API key: " + key.getName());
        return ResponseEntity.ok(Map.of("message", "API key deleted successfully"));
    }

    @GetMapping("/analytics/logs")
    public ResponseEntity<List<ApiKeyUsageLogDto>> getAnalyticsLogs() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(apiKeyUsageService.getLogsForUser(user));
    }

    @GetMapping("/analytics/by-key")
    public ResponseEntity<List<Map<String, Object>>> getAnalyticsByKey() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(apiKeyUsageService.getUsageByKey(user));
    }

    @GetMapping("/analytics/by-status")
    public ResponseEntity<List<Map<String, Object>>> getAnalyticsByStatus() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(apiKeyUsageService.getUsageByStatus(user));
    }

    @GetMapping("/analytics/by-endpoint")
    public ResponseEntity<List<Map<String, Object>>> getAnalyticsByEndpoint() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(apiKeyUsageService.getUsageByEndpoint(user));
    }

    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String hashApiKey(String apiKeyRaw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKeyRaw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    @Data
    public static class GenerateKeyRequest {
        private String name;
        private String description;
        private int daysToLive;
    }
}
