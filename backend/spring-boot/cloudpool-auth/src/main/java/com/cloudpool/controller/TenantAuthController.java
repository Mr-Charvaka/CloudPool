package com.cloudpool.controller;

import com.cloudpool.model.TenantUser;
import com.cloudpool.model.User;
import com.cloudpool.service.ProjectService;
import com.cloudpool.service.TenantAuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TenantAuthController {

    private final TenantAuthService tenantAuthService;
    private final com.cloudpool.repository.TenantUserRepository tenantUserRepository;
    private final ProjectService projectService;

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private void validateProjectAccess(UUID projectId) {
        User user = getAuthenticatedUser();
        projectService.getProject(projectId, user.getId()); // Throws if unauthorized
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@PathVariable UUID projectId) {
        validateProjectAccess(projectId);
        return ResponseEntity.ok(tenantUserRepository.findByProjectId(projectId));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(
            @PathVariable UUID projectId,
            @PathVariable UUID userId) {
        validateProjectAccess(projectId);
        TenantUser user = tenantUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant user not found"));
        if (!user.getProjectId().equals(projectId)) {
            return ResponseEntity.status(403).build();
        }
        tenantUserRepository.delete(user);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(
            @PathVariable UUID projectId,
            @RequestBody SignupRequest request) {
        validateProjectAccess(projectId);
        try {
            TenantUser user = tenantAuthService.register(
                    projectId, request.getEmail(), request.getPassword(),
                    request.getDisplayName(), request.getMetadata()
            );
            return ResponseEntity.ok(Map.of(
                    "message", "User created successfully",
                    "userId", user.getId()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @PathVariable UUID projectId,
            @RequestBody LoginRequest request) {
        validateProjectAccess(projectId);
        try {
            TenantAuthService.AuthResult result = tenantAuthService.login(projectId, request.getEmail(), request.getPassword());
            return ResponseEntity.ok(Map.of(
                    "accessToken", result.accessToken(),
                    "refreshToken", result.refreshToken(),
                    "user", Map.of(
                            "id", result.user().getId(),
                            "email", result.user().getEmail(),
                            "displayName", result.user().getDisplayName() != null ? result.user().getDisplayName() : ""
                    )
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @PathVariable UUID projectId,
            @RequestBody RefreshRequest request) {
        validateProjectAccess(projectId);
        try {
            TenantAuthService.AuthResult result = tenantAuthService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(Map.of(
                    "accessToken", result.accessToken(),
                    "refreshToken", result.refreshToken()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @PathVariable UUID projectId,
            @RequestBody RefreshRequest request) {
        validateProjectAccess(projectId);
        tenantAuthService.logout(request.getRefreshToken());
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @Data
    public static class SignupRequest {
        private String email;
        private String password;
        private String displayName;
        private String metadata;
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }
}
