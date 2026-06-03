package com.cloudpool.service;

import com.cloudpool.model.CronExecution;
import com.cloudpool.model.CronJob;
import com.cloudpool.repository.CronExecutionRepository;
import com.cloudpool.repository.CronJobRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class CronJobService {

    private final CronJobRepository cronJobRepository;
    private final CronExecutionRepository cronExecutionRepository;
    private final TaskScheduler taskScheduler; // ThreadPoolTaskScheduler provided by Spring Boot

    private final Map<UUID, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @PostConstruct
    public void init() {
        log.info("Initializing existing active Cron Jobs...");
        List<CronJob> jobs = cronJobRepository.findAll();
        for (CronJob job : jobs) {
            if (job.isActive()) {
                scheduleJob(job);
            }
        }
    }

    @Transactional
    public CronJob createOrUpdateJob(UUID projectId, String name, String cronExpression, String targetUrl, String httpMethod, String payload, String headers, boolean isActive) {
        CronJob job = cronJobRepository.findByProjectIdAndName(projectId, name)
                .orElseGet(() -> {
                    CronJob newJob = new CronJob();
                    newJob.setProjectId(projectId);
                    newJob.setName(name);
                    return newJob;
                });

        job.setCronExpression(cronExpression);
        job.setTargetUrl(targetUrl);
        job.setHttpMethod(httpMethod != null ? httpMethod.toUpperCase() : "POST");
        job.setPayload(payload);
        job.setHeaders(headers);
        job.setActive(isActive);

        CronJob saved = cronJobRepository.save(job);

        // Manage scheduling
        cancelJob(saved.getId());
        if (saved.isActive()) {
            scheduleJob(saved);
        }

        return saved;
    }

    @Transactional
    public void deleteJob(UUID projectId, UUID jobId) {
        CronJob job = cronJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        if (!job.getProjectId().equals(projectId)) {
            throw new SecurityException("Unauthorized");
        }

        cancelJob(jobId);
        cronJobRepository.delete(job);
    }

    private void scheduleJob(CronJob job) {
        try {
            CronTrigger trigger = new CronTrigger(job.getCronExpression());
            ScheduledFuture<?> future = taskScheduler.schedule(() -> executeJob(job.getId()), trigger);
            scheduledTasks.put(job.getId(), future);
            log.info("Scheduled cron job '{}' ({}) with expression: {}", job.getName(), job.getId(), job.getCronExpression());
        } catch (IllegalArgumentException e) {
            log.error("Invalid cron expression for job '{}': {}", job.getName(), job.getCronExpression());
        }
    }

    private void cancelJob(UUID jobId) {
        ScheduledFuture<?> future = scheduledTasks.remove(jobId);
        if (future != null) {
            future.cancel(false);
            log.info("Cancelled cron job ({})", jobId);
        }
    }

    @Transactional
    protected void executeJob(UUID jobId) {
        CronJob job = cronJobRepository.findById(jobId).orElse(null);
        if (job == null || !job.isActive()) return;

        log.info("Executing Cron Job '{}' targeting {}", job.getName(), job.getTargetUrl());
        CronExecution execution = new CronExecution();
        execution.setJob(job);
        
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(job.getTargetUrl()))
                    .timeout(Duration.ofSeconds(30));

            // Setup method and body
            HttpRequest.BodyPublisher bodyPublisher = job.getPayload() != null && !job.getPayload().isBlank()
                    ? HttpRequest.BodyPublishers.ofString(job.getPayload())
                    : HttpRequest.BodyPublishers.noBody();

            builder.method(job.getHttpMethod(), bodyPublisher);

            // Add basic JSON content type if payload exists and no headers provided
            if (job.getPayload() != null && !job.getPayload().isBlank()) {
                 builder.header("Content-Type", "application/json");
            }

            // We could parse custom headers here if job.getHeaders() is a JSON map

            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            execution.setHttpStatus(response.statusCode());
            execution.setResponseBody(response.body().length() > 2000 ? response.body().substring(0, 2000) : response.body());
            execution.setSuccess(response.statusCode() >= 200 && response.statusCode() < 300);

            log.info("Cron Job '{}' completed with status {}", job.getName(), response.statusCode());

        } catch (Exception e) {
            log.error("Cron Job '{}' failed: {}", job.getName(), e.getMessage());
            execution.setSuccess(false);
            execution.setResponseBody("Execution failed: " + e.getMessage());
        }

        cronExecutionRepository.save(execution);
    }
}
