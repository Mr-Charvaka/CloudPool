package com.cloudpool.util;

import lombok.extern.slf4j.Slf4j;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DockerPostgresProvisioner {

    private static final String CONTAINER_PREFIX = "cloudpool-db-";
    private static final String POSTGRES_IMAGE = "postgres:15-alpine";

    public static synchronized int provisionOrStartContainer(UUID projectId, String password) {
        String containerName = CONTAINER_PREFIX + projectId.toString().toLowerCase();
        log.info("Provisioning or starting PostgreSQL container for project: {}", projectId);

        try {
            boolean exists = checkContainerExists(containerName);
            if (!exists) {
                log.info("Container {} does not exist. Creating new container...", containerName);
                runCommand("docker", "run", "--name", containerName, "-p", "5432", "-e", "POSTGRES_PASSWORD=" + password, "-d", POSTGRES_IMAGE);
            } else {
                log.info("Container {} already exists.", containerName);
                boolean running = checkContainerRunning(containerName);
                if (!running) {
                    log.info("Container {} is not running. Starting container...", containerName);
                    runCommand("docker", "start", containerName);
                } else {
                    log.info("Container {} is already running.", containerName);
                }
            }

            // Find host port
            int hostPort = getContainerPort(containerName);
            if (hostPort <= 0) {
                throw new com.cloudpool.exception.CloudPoolException("Could not find mapped host port for container " + containerName);
            }

            log.info("Container {} mapped to host port {}. Waiting for PostgreSQL to be ready...", containerName, hostPort);
            waitForPostgres(hostPort);
            log.info("PostgreSQL in container {} is ready for connections on port {}", containerName, hostPort);
            return hostPort;

        } catch (Exception e) {
            log.error("Failed to provision/start Docker container for project {}: {}", projectId, e.getMessage(), e);
            throw new com.cloudpool.exception.CloudPoolException("DBaaS container provisioning failed: " + e.getMessage(), e);
        }
    }

    private static boolean checkContainerExists(String containerName) throws Exception {
        String output = runCommandWithOutput("docker", "ps", "-a", "-q", "--filter", "name=^" + containerName + "$");
        return output != null && !output.trim().isEmpty();
    }

    private static boolean checkContainerRunning(String containerName) throws Exception {
        String output = runCommandWithOutput("docker", "ps", "-q", "--filter", "name=^" + containerName + "$");
        return output != null && !output.trim().isEmpty();
    }

    private static int getContainerPort(String containerName) throws Exception {
        String output = runCommandWithOutput("docker", "port", containerName, "5432");
        if (output == null || output.trim().isEmpty()) {
            return -1;
        }
        // Output can have multiple lines (IPv4 and IPv6), read first line
        String line = output.split("\\r?\\n")[0].trim();
        int lastColon = line.lastIndexOf(':');
        if (lastColon != -1) {
            String portStr = line.substring(lastColon + 1).trim();
            return Integer.parseInt(portStr);
        }
        return -1;
    }

    private static void waitForPostgres(int port) {
        long start = System.currentTimeMillis();
        long timeoutMs = 20000; // 20 seconds timeout
        boolean ready = false;
        while (System.currentTimeMillis() - start < timeoutMs) {
            try (Socket socket = new Socket("localhost", port)) {
                ready = true;
                // Give Postgres engine half a second extra to complete internal startup
                Thread.sleep(1000);
                break;
            } catch (Exception e) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new com.cloudpool.exception.CloudPoolException(ie);
                }
            }
        }
        if (!ready) {
            throw new com.cloudpool.exception.CloudPoolException("PostgreSQL port " + port + " did not become ready in 20 seconds");
        }
    }

    private static void runCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[Docker CLI] {}", line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new com.cloudpool.exception.CloudPoolException("Command failed with exit code: " + exitCode);
        }
    }

    private static String runCommandWithOutput(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        process.waitFor();
        return sb.toString().trim();
    }
}

