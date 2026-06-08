package com.cloudpool.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class HealthController {

    private final HttpClient httpClient;

    @Value("${cloudpool.services.auth.url:http://localhost:8082}")
    private String authUrl;

    @Value("${cloudpool.services.data.url:http://localhost:8083}")
    private String dataUrl;

    @Value("${cloudpool.services.compute.url:http://localhost:8084}")
    private String computeUrl;

    @Value("${cloudpool.services.network.url:http://localhost:8085}")
    private String networkUrl;

    public HealthController() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1000))
                .build();
    }

    @GetMapping({"/health", "/api/health"})
    public Mono<Map<String, Object>> getHealth(
            @RequestParam(value = "details", defaultValue = "false") boolean details,
            @RequestParam(value = "advanced", defaultValue = "false") boolean advanced) {

        boolean showAdvanced = details || advanced;

        long startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("uptime", formatUptime(uptimeMs));
        response.put("uptimeMs", uptimeMs);
        response.put("startTime", Instant.ofEpochMilli(startTime).toString());

        if (!showAdvanced) {
            return Mono.just(response);
        }

        // Add System Stats
        Map<String, Object> systemStats = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        systemStats.put("availableProcessors", runtime.availableProcessors());
        systemStats.put("systemLoadAverage", ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage());
        
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;

        systemStats.put("jvmTotalMemory", formatBytes(totalMemory));
        systemStats.put("jvmFreeMemory", formatBytes(freeMemory));
        systemStats.put("jvmMaxMemory", formatBytes(maxMemory));
        systemStats.put("jvmUsedMemory", formatBytes(usedMemory));
        response.put("system", systemStats);

        // Ping microservices asynchronously & reactively
        Mono<Map<String, Object>> authHealth = pingService(authUrl + "/actuator/health");
        Mono<Map<String, Object>> dataHealth = pingService(dataUrl + "/actuator/health");
        Mono<Map<String, Object>> computeHealth = pingService(computeUrl + "/actuator/health");
        Mono<Map<String, Object>> networkHealth = pingService(networkUrl + "/actuator/health");

        return Mono.zip(authHealth, dataHealth, computeHealth, networkHealth)
                .map(tuple -> {
                    Map<String, Object> services = new HashMap<>();
                    services.put("auth", tuple.getT1());
                    services.put("data", tuple.getT2());
                    services.put("compute", tuple.getT3());
                    services.put("network", tuple.getT4());
                    response.put("services", services);
                    return response;
                });
    }

    private Mono<Map<String, Object>> pingService(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(1000))
                .GET()
                .build();

        return Mono.fromFuture(httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                .map(response -> {
                    Map<String, Object> statusMap = new HashMap<>();
                    statusMap.put("url", url);
                    if (response.statusCode() == 200) {
                        statusMap.put("status", "UP");
                    } else {
                        statusMap.put("status", "DOWN");
                        statusMap.put("statusCode", response.statusCode());
                    }
                    return statusMap;
                })
                .onErrorResume(ex -> {
                    Map<String, Object> statusMap = new HashMap<>();
                    statusMap.put("status", "DOWN");
                    statusMap.put("url", url);
                    statusMap.put("error", ex.getMessage() != null ? ex.getMessage() : ex.toString());
                    return Mono.just(statusMap);
                });
    }

    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long d = seconds / (24 * 3600);
        long h = (seconds % (24 * 3600)) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%dd %dh %dm %ds", d, h, m, s);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
