package com.cloudpool.controller;

import com.cloudpool.service.DatabaseBackupService;
import java.nio.file.Path;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/db")
@RequiredArgsConstructor
public class DatabaseBackupController {

    private final DatabaseBackupService backupService;

    @PostMapping("/backup")
    public ResponseEntity<Map<String, Object>> triggerBackup() {
        try {
            Path backup = backupService.createBackup();
            return ResponseEntity.ok(Map.of(
                    "message", "Backup created successfully",
                    "file", backup.getFileName().toString()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
