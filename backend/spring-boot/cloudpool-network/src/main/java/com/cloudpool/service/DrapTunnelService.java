package com.cloudpool.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class DrapTunnelService {

    @Value("${cloudpool.drap.executable-path:}")
    private String drapExecutablePath;

    @Value("${cloudpool.drap.enabled:false}")
    private boolean drapEnabled;

    // Track active processes by subdomain mapping
    private final Map<String, Process> activeTunnels = new ConcurrentHashMap<>();

    public void startTunnel(int localPort, String subdomain) {
        if (!drapEnabled) {
            throw new IllegalStateException("DRAP Tunneling is disabled via configuration.");
        }

        if (activeTunnels.containsKey(subdomain)) {
            Process existing = activeTunnels.get(subdomain);
            if (existing.isAlive()) {
                log.info("DRAP tunnel for subdomain '{}' is already running.", subdomain);
                return;
            } else {
                activeTunnels.remove(subdomain);
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    drapExecutablePath,
                    String.valueOf(localPort),
                    subdomain
            );
            pb.redirectErrorStream(true);

            log.info("Launching DRAP Tunnel: {} {} {}", drapExecutablePath, localPort, subdomain);
            Process process = pb.start();
            activeTunnels.put(subdomain, process);

            // Stream the Rust output into SLF4J logs
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("[DRAP-{}] {}", subdomain, line);
                    }
                } catch (Exception e) {
                    log.error("Failed to read DRAP output for subdomain {}: {}", subdomain, e.getMessage());
                }
            });
            outputReader.setDaemon(true);
            outputReader.start();

        } catch (Exception e) {
            log.error("Failed to start DRAP tunnel for subdomain {}: {}", subdomain, e.getMessage(), e);
            throw new RuntimeException("Failed to start DRAP tunnel: " + e.getMessage());
        }
    }

    public void stopTunnel(String subdomain) {
        Process process = activeTunnels.remove(subdomain);
        if (process != null) {
            if (process.isAlive()) {
                log.info("Stopping DRAP tunnel for subdomain '{}'", subdomain);
                process.destroy();
                try {
                    // Give it a moment to terminate gracefully
                    boolean terminated = process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                    if (!terminated) {
                        log.warn("DRAP tunnel for subdomain '{}' did not terminate gracefully, forcing kill.", subdomain);
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            }
        } else {
            log.warn("No active DRAP tunnel found for subdomain '{}' to stop.", subdomain);
        }
    }

    public boolean isTunnelActive(String subdomain) {
        Process process = activeTunnels.get(subdomain);
        return process != null && process.isAlive();
    }

    @PreDestroy
    public void cleanup() {
        log.info("Shutting down all active DRAP tunnels...");
        activeTunnels.keySet().forEach(this::stopTunnel);
    }
}
