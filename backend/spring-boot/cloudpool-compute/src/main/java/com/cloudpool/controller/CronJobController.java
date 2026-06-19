package com.cloudpool.controller;

import com.cloudpool.model.CronJob;
import com.cloudpool.model.User;
import com.cloudpool.repository.CronExecutionRepository;
import com.cloudpool.repository.CronJobRepository;
import com.cloudpool.service.CronJobService;
import com.cloudpool.service.ProjectService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/cron")
@RequiredArgsConstructor

public class CronJobController {

    private final CronJobService cronJobService;
    private final CronJobRepository cronJobRepository;
    private final CronExecutionRepository cronExecutionRepository;
    private final ProjectService projectService;

    private void validateProjectAccess(UUID projectId) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        projectService.getProject(projectId, user.getId()); // Throws if unauthorized
    }

    @GetMapping
    public ResponseEntity<?> listJobs(@PathVariable UUID projectId) {
        validateProjectAccess(projectId);
        return ResponseEntity.ok(cronJobRepository.findByProjectId(projectId));
    }

    @PostMapping
    public ResponseEntity<?> createOrUpdateJob(
            @PathVariable UUID projectId,
            @Valid @RequestBody JobRequest request) {
        validateProjectAccess(projectId);
        try {
            CronJob job = cronJobService.createOrUpdateJob(
                    projectId,
                    request.getName(),
                    request.getCronExpression(),
                    request.getTargetUrl(),
                    request.getHttpMethod(),
                    request.getPayload(),
                    request.getHeaders(),
                    request.getIsActive() != null ? request.getIsActive() : true
            );
            return ResponseEntity.ok(job);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<?> deleteJob(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId) {
        validateProjectAccess(projectId);
        try {
            cronJobService.deleteJob(projectId, jobId);
            return ResponseEntity.ok(Map.of("message", "Job deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{jobId}/executions")
    public ResponseEntity<?> getExecutions(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId) {
        validateProjectAccess(projectId);
        // Ensure job belongs to project
        CronJob job = cronJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        if (!job.getProjectId().equals(projectId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(cronExecutionRepository.findByJobIdOrderByExecutedAtDesc(jobId, PageRequest.of(0, 50)));
    }

    @Data
    public static class JobRequest {
        @jakarta.validation.constraints.NotBlank

        private String name;
        private String cronExpression;
        private String targetUrl;
        private String httpMethod;
        private String payload;
        private String headers;
        private Boolean isActive;
    }
}
