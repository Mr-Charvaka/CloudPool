package com.cloudpool.controller;

import com.cloudpool.service.DatabaseConsoleService;
import com.cloudpool.service.RedisConsoleService;
import com.cloudpool.repository.DatabaseConnectionRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/console")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConsoleController {

    private final DatabaseConsoleService consoleService;
    private final RedisConsoleService redisConsoleService;
    private final DatabaseConnectionRepository databaseConnectionRepository;

    @PostMapping("/execute")
    public ResponseEntity<?> executeSql(
            @RequestBody ExecuteSqlRequest request,
            @RequestHeader(value = "X-Project-Id", required = false) String projectIdStr) {
        if (request.getSql() == null || request.getSql().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("SQL query cannot be empty");
        }

        com.cloudpool.model.DatabaseConnection conn = null;
        com.cloudpool.model.User user = null;
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.cloudpool.model.User) {
            user = (com.cloudpool.model.User) auth.getPrincipal();
        }

        if (projectIdStr != null && !projectIdStr.trim().isEmpty()) {
            try {
                UUID projectId = UUID.fromString(projectIdStr);
                conn = databaseConnectionRepository.findByProjectIdAndDbType(projectId, "POSTGRESQL")
                        .filter(com.cloudpool.model.DatabaseConnection::isActive)
                        .orElse(null);
                if (conn != null && user != null) {
                    if (!conn.getProject().getUserId().equals(user.getId())) {
                        return ResponseEntity.status(403).body("Access denied to requested project database connection");
                    }
                }
            } catch (Exception e) {
                // Ignore and default to local H2
            }
        }

        return ResponseEntity.ok(consoleService.executeQuery(request.getSql(), conn, user));
    }

    @PostMapping("/redis/execute")
    public ResponseEntity<?> executeRedis(
            @RequestBody ExecuteRedisRequest request,
            @RequestHeader(value = "X-Project-Id", required = false) String projectIdStr) {
        if (request.getCommand() == null || request.getCommand().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Redis command cannot be empty");
        }

        com.cloudpool.model.DatabaseConnection conn = null;
        com.cloudpool.model.User user = null;
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.cloudpool.model.User) {
            user = (com.cloudpool.model.User) auth.getPrincipal();
        }

        if (projectIdStr != null && !projectIdStr.trim().isEmpty()) {
            try {
                UUID projectId = UUID.fromString(projectIdStr);
                conn = databaseConnectionRepository.findByProjectIdAndDbType(projectId, "REDIS")
                        .filter(com.cloudpool.model.DatabaseConnection::isActive)
                        .orElse(null);
                if (conn != null && user != null) {
                    if (!conn.getProject().getUserId().equals(user.getId())) {
                        return ResponseEntity.status(403).body("Access denied to requested project Redis connection");
                    }
                }
            } catch (Exception e) {
                // Ignore and default to localhost
            }
        }

        return ResponseEntity.ok(redisConsoleService.executeCommand(conn, request.getCommand()));
    }

    @Data
    public static class ExecuteSqlRequest {
        private String sql;
    }

    @Data
    public static class ExecuteRedisRequest {
        private String command;
    }
}
