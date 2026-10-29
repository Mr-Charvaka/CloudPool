package com.cloudpool.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudpool.common.util.ApiKeyUtils;
import com.cloudpool.model.ApiKey;
import com.cloudpool.model.User;
import com.cloudpool.repository.ApiKeyRepository;
import com.cloudpool.service.ApiKeyUsageService;
import com.cloudpool.service.AuditLogService;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ApiKeyControllerRotationTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;
    @Mock
    private ApiKeyUsageService apiKeyUsageService;
    @Mock
    private AuditLogService auditLogService;

    private ApiKeyController controller;
    private User owner;
    private ApiKey key;
    private String oldHash;

    @BeforeEach
    void setUp() {
        controller = new ApiKeyController(apiKeyRepository, apiKeyUsageService, auditLogService);
        owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        authenticateAs(owner);

        oldHash = ApiKeyUtils.hashApiKey("cp_live_old");
        key = ApiKey.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .name("ci key")
                .keyHash(oldHash)
                .active(true)
                .createdAt(LocalDateTime.now().minusDays(10))
                .expiresAt(LocalDateTime.now().plusDays(20))
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, java.util.List.of()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rotateReplacesTheHashAndReturnsTheNewPlainKeyOnce() {
        when(apiKeyRepository.findById(key.getId())).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<?> response = controller.rotateKey(key.getId());

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        String newPlainKey = (String) body.get("apiKey");
        assertTrue(newPlainKey.startsWith("cp_live_"));
        assertEquals(key.getId(), body.get("id"));

        assertNotEquals(oldHash, key.getKeyHash());
        assertEquals(ApiKeyUtils.hashApiKey(newPlainKey), key.getKeyHash());
        assertTrue(key.isActive());
        verify(apiKeyRepository).save(key);
    }

    @Test
    void rotateKeepsTheOriginalLifetime() {
        LocalDateTime created = LocalDateTime.now().minusDays(10);
        key.setCreatedAt(created);
        key.setExpiresAt(created.plusDays(30));
        when(apiKeyRepository.findById(key.getId())).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(i -> i.getArgument(0));

        controller.rotateKey(key.getId());

        long days = java.time.temporal.ChronoUnit.DAYS.between(key.getCreatedAt(), key.getExpiresAt());
        assertEquals(30, days);
        assertTrue(key.getCreatedAt().isAfter(created));
    }

    @Test
    void rotateWritesAnAuditLogEntry() {
        when(apiKeyRepository.findById(key.getId())).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(i -> i.getArgument(0));

        controller.rotateKey(key.getId());

        verify(auditLogService).log(eq(owner), eq(AuditLogService.ACTION_API_KEY_ROTATE), eq("API_KEY"),
                eq(key.getId().toString()), any(String.class));
    }

    @Test
    void rotateReturnsNotFoundForUnknownKey() {
        UUID unknown = UUID.randomUUID();
        when(apiKeyRepository.findById(unknown)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.rotateKey(unknown);

        assertEquals(404, response.getStatusCode().value());
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void rotateIsForbiddenForKeysOfAnotherUser() {
        User other = User.builder().id(UUID.randomUUID()).email("other@example.com").build();
        key.setUser(other);
        when(apiKeyRepository.findById(key.getId())).thenReturn(Optional.of(key));

        ResponseEntity<?> response = controller.rotateKey(key.getId());

        assertEquals(403, response.getStatusCode().value());
        assertEquals(oldHash, key.getKeyHash());
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void rotateIsRejectedForRevokedKeys() {
        key.setActive(false);
        when(apiKeyRepository.findById(key.getId())).thenReturn(Optional.of(key));

        ResponseEntity<?> response = controller.rotateKey(key.getId());

        assertEquals(409, response.getStatusCode().value());
        assertFalse(key.isActive());
        assertEquals(oldHash, key.getKeyHash());
        verify(apiKeyRepository, never()).save(any());
    }
}
