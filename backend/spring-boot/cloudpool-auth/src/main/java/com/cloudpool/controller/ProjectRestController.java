package com.cloudpool.controller;

import com.cloudpool.model.*;
import com.cloudpool.service.ProjectService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProjectRestController {

    private final ProjectService projectService;

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    // ── PROJECTS CRUD ──

    @GetMapping
    public ResponseEntity<List<Project>> listProjects() {
        User user = getAuthenticatedUser();
        List<Project> projects = projectService.listProjects(user.getId());
        return ResponseEntity.ok(projects);
    }

    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody CreateProjectRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Project name is required"));
        }
        try {
            User user = getAuthenticatedUser();
            Project project = projectService.createProject(user.getId(), request.getName(), request.getDescription());
            return ResponseEntity.ok(project);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<?> deleteProject(@PathVariable UUID projectId) {
        try {
            User user = getAuthenticatedUser();
            projectService.deleteProject(projectId, user.getId());
            return ResponseEntity.ok(Map.of("message", "Project deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // ── SECRETS VAULT ──

    @GetMapping("/{projectId}/secrets")
    public ResponseEntity<?> listSecrets(@PathVariable UUID projectId) {
        try {
            User user = getAuthenticatedUser();
            List<ProjectSecret> secrets = projectService.listSecrets(projectId, user.getId());
            return ResponseEntity.ok(secrets);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{projectId}/secrets")
    public ResponseEntity<?> addSecret(@PathVariable UUID projectId, @RequestBody SaveSecretRequest request) {
        if (request.getKey() == null || request.getKey().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Secret key is required"));
        }
        try {
            User user = getAuthenticatedUser();
            ProjectSecret secret = projectService.addSecret(projectId, request.getKey(), request.getValue(), user.getId());
            return ResponseEntity.ok(secret);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/secrets/{secretId}")
    public ResponseEntity<?> deleteSecret(@PathVariable UUID secretId) {
        try {
            User user = getAuthenticatedUser();
            projectService.deleteSecret(secretId, user.getId());
            return ResponseEntity.ok(Map.of("message", "Secret deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // ── DATABASE CONNECTIONS ──

    @GetMapping("/{projectId}/connections")
    public ResponseEntity<?> listConnections(@PathVariable UUID projectId) {
        try {
            User user = getAuthenticatedUser();
            List<DatabaseConnection> connections = projectService.listConnections(projectId, user.getId());
            return ResponseEntity.ok(connections);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{projectId}/connections")
    public ResponseEntity<?> saveConnection(@PathVariable UUID projectId, @RequestBody SaveConnectionRequest request) {
        if (request.getDbType() == null || request.getHost() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "dbType and host are required"));
        }
        try {
            User user = getAuthenticatedUser();
            DatabaseConnection conn = projectService.saveConnection(
                    projectId,
                    request.getDbType(),
                    request.getHost(),
                    request.getPort(),
                    request.getDatabaseName(),
                    request.getUsername(),
                    request.getPassword(),
                    request.isActive(),
                    user.getId()
            );
            return ResponseEntity.ok(conn);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/connections/{connectionId}")
    public ResponseEntity<?> deleteConnection(@PathVariable UUID connectionId) {
        try {
            User user = getAuthenticatedUser();
            projectService.deleteConnection(connectionId, user.getId());
            return ResponseEntity.ok(Map.of("message", "Connection deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{projectId}/connections/test")
    public ResponseEntity<?> testConnection(@PathVariable UUID projectId, @RequestBody SaveConnectionRequest request) {
        try {
            boolean success = projectService.testConnection(
                    request.getDbType(),
                    request.getHost(),
                    request.getPort(),
                    request.getDatabaseName(),
                    request.getUsername(),
                    request.getPassword()
            );
            return ResponseEntity.ok(Map.of("success", success));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ── VERSIONED SNAPSHOTS ──

    @GetMapping("/{projectId}/snapshots")
    public ResponseEntity<?> listSnapshots(@PathVariable UUID projectId) {
        try {
            User user = getAuthenticatedUser();
            List<ProjectSnapshot> snapshots = projectService.listSnapshots(projectId, user.getId());
            return ResponseEntity.ok(snapshots);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{projectId}/snapshots")
    public ResponseEntity<?> createSnapshot(@PathVariable UUID projectId, @RequestBody CreateSnapshotRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Snapshot name is required"));
        }
        try {
            User user = getAuthenticatedUser();
            ProjectSnapshot snapshot = projectService.createSnapshot(projectId, request.getName(), user.getId());
            return ResponseEntity.ok(snapshot);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{projectId}/snapshots/{snapshotId}/restore")
    public ResponseEntity<?> restoreSnapshot(@PathVariable UUID projectId, @PathVariable UUID snapshotId) {
        try {
            User user = getAuthenticatedUser();
            projectService.restoreSnapshot(projectId, snapshotId, user.getId());
            return ResponseEntity.ok(Map.of("message", "Snapshot restored successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    // ── REQUEST DTOS ──

    @Data
    public static class CreateProjectRequest {
        private String name;
        private String description;
    }

    @Data
    public static class SaveSecretRequest {
        private String key;
        private String value;
    }

    @Data
    public static class SaveConnectionRequest {
        private String dbType;
        private String host;
        private int port;
        private String databaseName;
        private String username;
        private String password;
        private boolean active;
    }

    @Data
    public static class CreateSnapshotRequest {
        private String name;
    }
}
