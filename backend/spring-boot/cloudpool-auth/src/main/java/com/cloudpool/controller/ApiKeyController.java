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
import jakarta.validation.Valid;
import com.cloudpool.common.util.ApiKeyUtils;
import com.cloudpool.service.AuditLogService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor

public class ApiKeyController {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyUsageService apiKeyUsageService;
    private final AuditLogService auditLogService;

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping
    public ResponseEntity<?> listKeys() {
        User user = getAuthenticatedUser();
        List<ApiKey> keys = apiKeyRepository.findByUser(user);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (ApiKey k : keys) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", k.getId());
            map.put("name", k.getName());
            map.put("description", k.getDescription());
            map.put("active", k.isActive());
            map.put("createdAt", k.getCreatedAt());
            map.put("expiresAt", k.getExpiresAt());
            map.put("keyHash", "[REDACTED]");
            result.add(map);
        }
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateKey(@Valid @RequestBody GenerateKeyRequest request) {
        User user = getAuthenticatedUser();
        
        // Generate plain key: cp_live_ + 32 random alphanumeric characters
        String plainKey = "cp_live_" + ApiKeyUtils.generateRandomString(32);
        String hashedKey = ApiKeyUtils.hashApiKey(plainKey);

        int days = request.getDaysToLive();
        if (days <= 0 || days > 90) {
            days = 90;
        }

        ApiKey apiKey = ApiKey.builder()
                .user(user)
                .name(request.getName())
                .description(request.getDescription())
                .keyHash(hashedKey)
                .active(true)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(days))
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

    /**
     * Replaces the secret of an existing key. The old secret stops working immediately because
     * only the hash is stored, and the key keeps its id so usage analytics stay linked to it.
     */
    @PostMapping("/{id}/rotate")
    public ResponseEntity<?> rotateKey(@PathVariable UUID id) {
        User user = getAuthenticatedUser();
        Optional<ApiKey> keyOpt = apiKeyRepository.findById(id);
        if (keyOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ApiKey key = keyOpt.get();
        if (!key.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        if (!key.isActive()) {
            return ResponseEntity.status(409).body(Map.of("error", "Revoked API keys cannot be rotated"));
        }

        long lifetimeDays = 90;
        if (key.getCreatedAt() != null && key.getExpiresAt() != null) {
            lifetimeDays = Math.max(1, Math.min(90, ChronoUnit.DAYS.between(key.getCreatedAt(), key.getExpiresAt())));
        }

        String plainKey = "cp_live_" + ApiKeyUtils.generateRandomString(32);
        LocalDateTime now = LocalDateTime.now();
        key.setKeyHash(ApiKeyUtils.hashApiKey(plainKey));
        key.setCreatedAt(now);
        key.setExpiresAt(now.plusDays(lifetimeDays));
        ApiKey saved = apiKeyRepository.save(key);

        auditLogService.log(user, AuditLogService.ACTION_API_KEY_ROTATE, "API_KEY", saved.getId().toString(), "Rotated API key: " + saved.getName());

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

    @Data
    public static class GenerateKeyRequest {
        @jakarta.validation.constraints.NotBlank

        private String name;
        private String description;
        private int daysToLive;
    }
}

