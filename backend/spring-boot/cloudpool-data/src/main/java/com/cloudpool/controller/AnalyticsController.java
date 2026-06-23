package com.cloudpool.controller;

import com.cloudpool.model.AnalyticsApiLog;
import com.cloudpool.model.User;
import com.cloudpool.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    private UUID parseProjectId(String projectIdHeader) {
        if (projectIdHeader != null && !projectIdHeader.trim().isEmpty()) {
            try {
                return UUID.fromString(projectIdHeader.trim());
            } catch (Exception ignored) {}
        }
        return null;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(@RequestHeader(value = "X-Project-Id", required = false) String projectIdHeader) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID projectId = parseProjectId(projectIdHeader);
        return ResponseEntity.ok(analyticsService.getSummary(projectId, user));
    }

    @GetMapping("/logs")
    public ResponseEntity<List<AnalyticsApiLog>> getLogs(@RequestHeader(value = "X-Project-Id", required = false) String projectIdHeader) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID projectId = parseProjectId(projectIdHeader);
        return ResponseEntity.ok(analyticsService.getRecentLogs(projectId, user));
    }
}
