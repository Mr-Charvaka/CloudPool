package com.cloudpool.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// Note: @CrossOrigin removed — CORS is handled globally in GatewaySecurityConfig
@RestController
public class HealthController {

    private final WebClient webClient;

    @Value("${cloudpool.services.auth.url:http://localhost:8082}")
    private String authUrl;

    @Value("${cloudpool.services.data.url:http://localhost:8083}")
    private String dataUrl;

    @Value("${cloudpool.services.compute.url:http://localhost:8084}")
    private String computeUrl;

    @Value("${cloudpool.services.network.url:http://localhost:8085}")
    private String networkUrl;

    public HealthController(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(256 * 1024))
                .build();
    }

    @GetMapping({"/health", "/api/health"})
    public Mono<Map<String, Object>> getHealth(
            @RequestParam(value = "details", defaultValue = "false") boolean details,
            @RequestParam(value = "advanced", defaultValue = "false") boolean advanced) {

        boolean showAdvanced = details || advanced;

        long startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        if (!showAdvanced) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "UP");
            response.put("uptime", formatUptime(uptimeMs));
            response.put("uptimeMs", uptimeMs);
            response.put("startTime", Instant.ofEpochMilli(startTime).toString());
            return Mono.just(response);
        }

        // System stats
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();

        Map<String, Object> systemStats = new HashMap<>();
        systemStats.put("availableProcessors", runtime.availableProcessors());
        systemStats.put("systemLoadAverage", ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage());
        systemStats.put("jvmTotalMemory", formatBytes(totalMemory));
        systemStats.put("jvmFreeMemory", formatBytes(freeMemory));
        systemStats.put("jvmMaxMemory", formatBytes(runtime.maxMemory()));
        systemStats.put("jvmUsedMemory", formatBytes(totalMemory - freeMemory));

        // Fan-out service health pings concurrently via WebClient (reactive, no blocking)
        Mono<Map<String, Object>> authHealth    = pingService("auth",    authUrl    + "/actuator/health");
        Mono<Map<String, Object>> dataHealth    = pingService("data",    dataUrl    + "/actuator/health");
        Mono<Map<String, Object>> computeHealth = pingService("compute", computeUrl + "/actuator/health");
        Mono<Map<String, Object>> networkHealth = pingService("network", networkUrl + "/actuator/health");

        return Mono.zip(authHealth, dataHealth, computeHealth, networkHealth)
                .map(tuple -> {
                    // Build response entirely inside the lambda — no shared mutable state
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "UP");
                    response.put("uptime", formatUptime(uptimeMs));
                    response.put("uptimeMs", uptimeMs);
                    response.put("startTime", Instant.ofEpochMilli(startTime).toString());
                    response.put("system", systemStats);

                    Map<String, Object> services = new HashMap<>();
                    services.put("auth",    tuple.getT1());
                    services.put("data",    tuple.getT2());
                    services.put("compute", tuple.getT3());
                    services.put("network", tuple.getT4());
                    response.put("services", services);
                    return response;
                });
    }

    private Mono<Map<String, Object>> pingService(String name, String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                // Prevent WebClient from throwing WebClientResponseException on 4xx/5xx.
                // Instead convert non-2xx into a controlled error that onErrorResume handles below.
                .onStatus(status -> !status.is2xxSuccessful(),
                          resp -> Mono.error(new RuntimeException("HTTP " + resp.statusCode().value())))
                .toBodilessEntity()
                .timeout(Duration.ofMillis(1000))
                .map(resp -> {
                    Map<String, Object> statusMap = new HashMap<>();
                    statusMap.put("name", name);
                    statusMap.put("status", "UP");
                    return statusMap;
                })
                .onErrorResume(ex -> {
                    // Catches: connection refused, timeout, DNS failure, non-2xx HTTP responses
                    Map<String, Object> statusMap = new HashMap<>();
                    statusMap.put("name", name);
                    statusMap.put("status", "DOWN");
                    statusMap.put("error", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
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
