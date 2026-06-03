package com.cloudpool.service;

import com.cloudpool.model.AnalyticsApiLog;
import com.cloudpool.repository.AnalyticsApiLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final AnalyticsApiLogRepository logRepository;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public void logRequest(UUID projectId, String path, String method, int statusCode, long durationMs, String ipAddress, String userAgent) {
        executorService.submit(() -> {
            try {
                AnalyticsApiLog apiLog = AnalyticsApiLog.builder()
                        .projectId(projectId)
                        .requestPath(path)
                        .requestMethod(method)
                        .statusCode(statusCode)
                        .durationMs(durationMs)
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .timestamp(LocalDateTime.now())
                        .build();
                logRepository.save(apiLog);
            } catch (Exception e) {
                log.error("Failed to save API log in executor: {}", e.getMessage());
            }
        });
    }

    public List<AnalyticsApiLog> getRecentLogs(UUID projectId) {
        if (projectId != null) {
            return logRepository.findByProjectIdOrderByTimestampDesc(projectId);
        }
        return logRepository.findAllByOrderByTimestampDesc();
    }

    public Map<String, Object> getSummary(UUID projectId) {
        List<AnalyticsApiLog> logs = getRecentLogs(projectId);
        
        long totalRequests = logs.size();
        long successCount = 0;
        long errorCount = 0;
        long totalDuration = 0;
        
        Map<String, Long> pathCounts = new HashMap<>();
        Map<String, Long> statusCounts = new HashMap<>();

        for (AnalyticsApiLog log : logs) {
            totalDuration += log.getDurationMs();
            if (log.getStatusCode() >= 200 && log.getStatusCode() < 400) {
                successCount++;
            } else if (log.getStatusCode() >= 500) {
                errorCount++;
            }
            
            pathCounts.put(log.getRequestPath(), pathCounts.getOrDefault(log.getRequestPath(), 0L) + 1);
            
            String statusGroup = (log.getStatusCode() / 100) + "xx";
            statusCounts.put(statusGroup, statusCounts.getOrDefault(statusGroup, 0L) + 1);
        }

        double avgLatency = totalRequests > 0 ? (double) totalDuration / totalRequests : 0.0;
        double successRate = totalRequests > 0 ? (double) successCount / totalRequests * 100.0 : 100.0;

        List<Map<String, Object>> byPathList = new ArrayList<>();
        pathCounts.forEach((path, count) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("path", path);
            item.put("count", count);
            byPathList.add(item);
        });
        byPathList.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalRequests", totalRequests);
        summary.put("successRate", successRate);
        summary.put("errorCount", errorCount);
        summary.put("averageLatencyMs", avgLatency);
        summary.put("statusDistribution", statusCounts);
        summary.put("topPaths", byPathList.size() > 10 ? byPathList.subList(0, 10) : byPathList);

        return summary;
    }
}
