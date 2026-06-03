package com.cloudpool.controller;

import com.cloudpool.model.DevTable;
import com.cloudpool.model.DevTableField;
import com.cloudpool.model.User;
import com.cloudpool.service.DatabaseService;
import com.cloudpool.service.DatabaseService.FieldRequest;
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
@RequestMapping("/api/v1/db")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DatabaseRestController {

    private final DatabaseService databaseService;
    private final ProjectService projectService;

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @PostMapping("/tables")
    public ResponseEntity<?> createTable(
            @RequestBody CreateTableRequest request,
            @RequestHeader(value = "X-Project-Id", required = false) String projectIdHeader) {
        try {
            User user = getAuthenticatedUser();
            UUID projectId = request.getProjectId();
            if (projectId == null && projectIdHeader != null && !projectIdHeader.trim().isEmpty()) {
                try {
                    projectId = UUID.fromString(projectIdHeader);
                } catch (Exception e) {
                    // Ignore invalid format
                }
            }
            if (projectId == null) {
                projectId = projectService.listProjects(user.getId()).get(0).getId();
            }
            DevTable table = databaseService.createTable(
                    user.getId(),
                    projectId,
                    request.getName(),
                    request.getDisplayName(),
                    request.getDescription(),
                    request.getFields()
            );
            return ResponseEntity.ok(table);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tables")
    public ResponseEntity<List<DevTable>> listTables(
            @RequestParam(value = "projectId", required = false) UUID projectId,
            @RequestHeader(value = "X-Project-Id", required = false) String projectIdHeader) {
        User user = getAuthenticatedUser();
        if (projectId == null && projectIdHeader != null && !projectIdHeader.trim().isEmpty()) {
            try {
                projectId = UUID.fromString(projectIdHeader);
            } catch (Exception e) {
                // Ignore invalid format
            }
        }
        if (projectId == null) {
            projectId = projectService.listProjects(user.getId()).get(0).getId();
        }
        List<DevTable> tables = databaseService.listTables(user.getId(), projectId);
        return ResponseEntity.ok(tables);
    }

    @GetMapping("/tables/{tableId}")
    public ResponseEntity<?> getTable(@PathVariable UUID tableId) {
        try {
            User user = getAuthenticatedUser();
            DevTable table = databaseService.getTable(tableId, user.getId());
            return ResponseEntity.ok(table);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tables/{tableId}/fields")
    public ResponseEntity<?> getTableFields(@PathVariable UUID tableId) {
        try {
            User user = getAuthenticatedUser();
            List<DevTableField> fields = databaseService.getTableFields(tableId, user.getId());
            return ResponseEntity.ok(fields);
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/tables/{tableId}")
    public ResponseEntity<?> deleteTable(@PathVariable UUID tableId) {
        try {
            User user = getAuthenticatedUser();
            databaseService.deleteTable(tableId, user.getId());
            return ResponseEntity.ok(Map.of("message", "Table deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/tables/{tableId}/records")
    public ResponseEntity<?> insertRecord(@PathVariable UUID tableId, @RequestBody Map<String, Object> data) {
        try {
            User user = getAuthenticatedUser();
            Map<String, Object> record = databaseService.insertRecord(tableId, data, user.getId());
            return ResponseEntity.ok(record);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tables/{tableId}/records")
    public ResponseEntity<?> queryRecords(@PathVariable UUID tableId) {
        try {
            User user = getAuthenticatedUser();
            List<Map<String, Object>> records = databaseService.queryRecords(tableId, user.getId());
            return ResponseEntity.ok(records);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/tables/{tableId}/records/{recordId}")
    public ResponseEntity<?> deleteRecord(@PathVariable UUID tableId, @PathVariable String recordId) {
        try {
            User user = getAuthenticatedUser();
            databaseService.deleteRecord(tableId, recordId, user.getId());
            return ResponseEntity.ok(Map.of("message", "Record deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @Data
    public static class CreateTableRequest {
        private UUID projectId;
        private String name;
        private String displayName;
        private String description;
        private List<FieldRequest> fields;
    }
}
