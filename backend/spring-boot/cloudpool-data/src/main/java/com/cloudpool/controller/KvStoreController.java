package com.cloudpool.controller;

import com.cloudpool.model.User;
import com.cloudpool.service.KvStoreService;
import com.cloudpool.service.ProjectService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/kv")
@RequiredArgsConstructor

public class KvStoreController {

    private final KvStoreService kvStoreService;
    private final com.cloudpool.repository.KvStoreRepository kvStoreRepository;
    private final ProjectService projectService;

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private void validateProjectAccess(UUID projectId) {
        User user = getAuthenticatedUser();
        projectService.getProject(projectId, user.getId()); // Throws if unauthorized
    }

    @PutMapping("/{keyName}")
    public ResponseEntity<?> setKey(
            @PathVariable UUID projectId,
            @PathVariable String keyName,
            @Valid @RequestBody KvRequest request) {
        validateProjectAccess(projectId);
        kvStoreService.set(projectId, keyName, request.getValue(), request.getTtlSeconds());
        return ResponseEntity.ok(Map.of("message", "Key saved successfully"));
    }

    @GetMapping
    public ResponseEntity<?> getAllKeys(@PathVariable UUID projectId) {
        validateProjectAccess(projectId);
        return ResponseEntity.ok(kvStoreRepository.findByProjectId(projectId));
    }

    @GetMapping("/{keyName}")
    public ResponseEntity<?> getKey(
            @PathVariable UUID projectId,
            @PathVariable String keyName) {
        validateProjectAccess(projectId);
        return kvStoreService.get(projectId, keyName)
                .map(val -> ResponseEntity.ok(Map.of("key", keyName, "value", val)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{keyName}")
    public ResponseEntity<?> deleteKey(
            @PathVariable UUID projectId,
            @PathVariable String keyName) {
        validateProjectAccess(projectId);
        kvStoreService.delete(projectId, keyName);
        return ResponseEntity.ok(Map.of("message", "Key deleted successfully"));
    }

    @Data
    public static class KvRequest {
        private String value;
        private Integer ttlSeconds;
    }
}
