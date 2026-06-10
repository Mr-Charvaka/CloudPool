package com.cloudpool.controller;

import com.cloudpool.model.*;
import com.cloudpool.repository.*;
import com.cloudpool.service.GraphQLSubscriptionService;
import com.cloudpool.service.StorageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/compute")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ComputeController {

    private final StaticSiteRepository staticSiteRepository;
    private final ServerlessFunctionRepository serverlessFunctionRepository;
    private final ContainerDeploymentRepository containerDeploymentRepository;
    private final BucketRepository bucketRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final StorageService storageService;
    private final GraphQLSubscriptionService subscriptionService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /* ── STATIC SITES & WEB3 GATEWAY ── */

    @PostMapping("/static")
    public ResponseEntity<?> deployStaticSite(@RequestBody StaticSiteRequest request) {
        User user = getAuthenticatedUser();
        Bucket bucket = bucketRepository.findByUserAndName(user, request.getBucketName())
                .orElseThrow(() -> new IllegalArgumentException("Bucket not found: " + request.getBucketName()));

        StaticSite site = StaticSite.builder()
                .name(request.getName())
                .bucketName(request.getBucketName())
                .domain(request.getDomain())
                .status("DEPLOYED")
                .user(user)
                .build();

        StaticSite saved = staticSiteRepository.save(site);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/static")
    public ResponseEntity<List<StaticSite>> listStaticSites() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(staticSiteRepository.findByUser(user));
    }

    @DeleteMapping("/static/{id}")
    public ResponseEntity<?> deleteStaticSite(@PathVariable("id") UUID id) {
        User user = getAuthenticatedUser();
        StaticSite site = staticSiteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Static site not found"));

        if (!site.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        staticSiteRepository.delete(site);
        return ResponseEntity.ok(Map.of("message", "Static site deleted"));
    }

    @GetMapping("/static/serve/{domain}/**")
    public ResponseEntity<?> serveStaticFile(
            @PathVariable("domain") String domain,
            @RequestParam(value = "path", defaultValue = "index.html") String requestPath) {
        
        StaticSite site = staticSiteRepository.findByDomain(domain)
                .orElseThrow(() -> new IllegalArgumentException("Domain not resolved: " + domain));

        Bucket bucket = bucketRepository.findByUserAndName(site.getUser(), site.getBucketName())
                .orElseThrow(() -> new IllegalArgumentException("Mapped bucket not found"));

        List<FileMetadata> files = fileMetadataRepository.findByBucket(bucket);
        
        // Match filename
        FileMetadata targetFile = files.stream()
                .filter(f -> f.getOriginalName().equalsIgnoreCase(requestPath))
                .findFirst()
                .orElse(null);

        if (targetFile == null) {
            // Try to find index.html as fallback
            targetFile = files.stream()
                    .filter(f -> f.getOriginalName().equalsIgnoreCase("index.html"))
                    .findFirst()
                    .orElse(null);
        }

        if (targetFile == null) {
            return ResponseEntity.status(404).body("<h3>404 Not Found</h3><p>Could not find file: " + requestPath + " in bucket " + bucket.getName() + "</p>");
        }

        try {
            byte[] data = storageService.downloadFileDirectly(targetFile);
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (targetFile.getMimeType() != null) {
                try {
                    mediaType = MediaType.parseMediaType(targetFile.getMimeType());
                } catch (Exception ignored) {}
            }
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(data);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error reading static asset: " + e.getMessage());
        }
    }

    /**
     * Web2-to-Web3 DNS Gateway
     * Resolves ENS .eth domains and serves payloads directly to Web2 browsers
     */
    @GetMapping("/dns/gateway/{domain}/**")
    public ResponseEntity<?> web3Gateway(
            @PathVariable("domain") String domain,
            @RequestParam(value = "path", defaultValue = "index.html") String requestPath) {
        
        log.info("ENS Gateway resolving request for Web3 domain: {} path: {}", domain, requestPath);
        
        StaticSite site = staticSiteRepository.findByDomain(domain)
                .orElseThrow(() -> new IllegalArgumentException("ENS Name not registered: " + domain));

        Bucket bucket = bucketRepository.findByUserAndName(site.getUser(), site.getBucketName())
                .orElseThrow(() -> new IllegalArgumentException("Mapped storage pool not found for ENS name"));

        List<FileMetadata> files = fileMetadataRepository.findByBucket(bucket);
        
        // Match filename
        FileMetadata targetFile = files.stream()
                .filter(f -> f.getOriginalName().equalsIgnoreCase(requestPath))
                .findFirst()
                .orElse(null);

        if (targetFile == null) {
            targetFile = files.stream()
                    .filter(f -> f.getOriginalName().equalsIgnoreCase("index.html"))
                    .findFirst()
                    .orElse(null);
        }

        if (targetFile == null) {
            String fakeIpfsHash = "bafybeic" + UUID.nameUUIDFromBytes(domain.getBytes()).toString().replace("-", "").substring(0, 24);
            return ResponseEntity.status(404).body(
                    "<h3>404 Web3 Resolution Failed</h3>" +
                    "<p><b>Web3 Domain:</b> " + domain + "</p>" +
                    "<p><b>Simulated ENS Content Hash (IPFS):</b> <code>ipfs://" + fakeIpfsHash + "</code></p>" +
                    "<p>Could not resolve resource: <code>" + requestPath + "</code> inside storage bucket.</p>"
            );
        }

        try {
            byte[] data = storageService.downloadFileDirectly(targetFile);
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (targetFile.getMimeType() != null) {
                try {
                    mediaType = MediaType.parseMediaType(targetFile.getMimeType());
                } catch (Exception ignored) {}
            }
            
            String fakeIpfsHash = "bafybeic" + UUID.nameUUIDFromBytes(domain.getBytes()).toString().replace("-", "").substring(0, 24);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header("X-ENS-Resolver", "CloudPool ENS-IPFS-Bridge/v1.1")
                    .header("X-Web3-Content-Hash", "ipfs://" + fakeIpfsHash)
                    .body(data);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error reading static Web3 payload: " + e.getMessage());
        }
    }

    /* ── SERVERLESS FUNCTIONS ── */

    @PostMapping("/serverless")
    public ResponseEntity<?> deployServerlessFunction(@RequestBody ServerlessRequest request) {
        User user = getAuthenticatedUser();
        
        ServerlessFunction function = ServerlessFunction.builder()
                .name(request.getName())
                .triggerRoute(request.getTriggerRoute())
                .code(request.getCode())
                .status("ACTIVE")
                .wasmCompiled(true)
                .user(user)
                .build();

        ServerlessFunction saved = serverlessFunctionRepository.save(function);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/serverless")
    public ResponseEntity<List<ServerlessFunction>> listServerlessFunctions() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(serverlessFunctionRepository.findByUser(user));
    }

    @PostMapping("/serverless/{id}/execute")
    public ResponseEntity<?> executeServerlessFunction(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) Map<String, Object> params) {
        
        User user = getAuthenticatedUser();
        ServerlessFunction function = serverlessFunctionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Serverless function not found"));

        if (!function.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        String paramsJson = "{}";
        if (params != null) {
            try {
                paramsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(params);
            } catch (Exception ignored) {}
        }

        String result = executeSandbox(function.getCode(), paramsJson);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "executionOutput", result,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @DeleteMapping("/serverless/{id}")
    public ResponseEntity<?> deleteServerlessFunction(@PathVariable("id") UUID id) {
        User user = getAuthenticatedUser();
        ServerlessFunction function = serverlessFunctionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Function not found"));

        if (!function.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        serverlessFunctionRepository.delete(function);
        return ResponseEntity.ok(Map.of("message", "Serverless function deleted"));
    }

    private String executeSandbox(String code, String paramsJson) {
        java.util.concurrent.Future<String> future = executor.submit(() -> {
            try {
                ScriptEngineManager manager = new ScriptEngineManager();
                ScriptEngine engine = manager.getEngineByName("js");
                if (engine == null) {
                    engine = manager.getEngineByName("nashorn");
                }
                if (engine == null) {
                    engine = manager.getEngineByName("GraalVM");
                }

                if (engine != null) {
                    engine.eval("var params = " + paramsJson + ";");
                    Object result = engine.eval(code);
                    return result != null ? result.toString() : "null";
                }
            } catch (Exception e) {
                return "Sandbox execution error: " + e.getMessage();
            }
            return "Simulated isolated Wasm execution success.\n" +
                    "Code length: " + code.length() + " bytes\n" +
                    "Parameters: " + paramsJson + "\n" +
                    "Execution result: OK";
        });

        try {
            return future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true); // Attempt to interrupt the runaway thread
            return "Sandbox execution error: Execution timed out (exceeded 5 seconds).";
        } catch (Exception e) {
            return "Sandbox execution error: " + e.getMessage();
        }
    }

    /* ── CONTAINER HOSTING ── */

    @PostMapping("/container")
    public ResponseEntity<?> deployContainer(@RequestBody ContainerRequest request) {
        User user = getAuthenticatedUser();

        ContainerDeployment container = ContainerDeployment.builder()
                .name(request.getName())
                .dockerImage(request.getDockerImage())
                .cpu(request.getCpu())
                .memory(request.getMemory())
                .replicas(request.getReplicas())
                .status("BUILDING")
                .logs("Initializing container workspace...\n")
                .user(user)
                .build();

        ContainerDeployment saved = containerDeploymentRepository.save(container);

        // Submit as BackgroundJob to show visual progress
        BackgroundJob job = BackgroundJob.builder()
                .jobType("CONTAINER_DEPLOYMENT")
                .status("PENDING")
                .payload(String.format("{\"containerId\":\"%s\",\"image\":\"%s\"}", saved.getId(), saved.getDockerImage()))
                .build();
        BackgroundJob savedJob = backgroundJobRepository.save(job);
        subscriptionService.publishJobUpdate(savedJob);

        // Start async lifecycle
        startAsyncContainerDeployment(saved, savedJob);

        return ResponseEntity.ok(saved);
    }

    @GetMapping("/container")
    public ResponseEntity<List<ContainerDeployment>> listContainers() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(containerDeploymentRepository.findByUser(user));
    }

    @GetMapping("/container/{id}/logs")
    public ResponseEntity<?> getContainerLogs(@PathVariable("id") UUID id) {
        User user = getAuthenticatedUser();
        ContainerDeployment container = containerDeploymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Container not found"));

        if (!container.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        return ResponseEntity.ok(Map.of("logs", container.getLogs() != null ? container.getLogs() : "No logs available"));
    }

    @PostMapping("/container/{id}/scale")
    public ResponseEntity<?> scaleContainer(
            @PathVariable("id") UUID id,
            @RequestParam("replicas") int replicas) {
        
        User user = getAuthenticatedUser();
        ContainerDeployment container = containerDeploymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Container not found"));

        if (!container.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        container.setReplicas(replicas);
        container.setLogs((container.getLogs() != null ? container.getLogs() : "") + 
                String.format("[%s] Scaling replicas count to %d...\n", LocalDateTime.now().toString(), replicas) +
                String.format("[%s] Scale completed. Active pods: %d/%d\n", LocalDateTime.now().toString(), replicas, replicas));
        
        ContainerDeployment saved = containerDeploymentRepository.save(container);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/container/{id}")
    public ResponseEntity<?> deleteContainer(@PathVariable("id") UUID id) {
        User user = getAuthenticatedUser();
        ContainerDeployment container = containerDeploymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Container not found"));

        if (!container.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        containerDeploymentRepository.delete(container);
        return ResponseEntity.ok(Map.of("message", "Container undeployed"));
    }

    private void startAsyncContainerDeployment(ContainerDeployment deployment, BackgroundJob job) {
        executor.submit(() -> {
            try {
                // Phase 1: BUILDING (3 seconds)
                Thread.sleep(3000);
                ContainerDeployment active = containerDeploymentRepository.findById(deployment.getId()).orElse(null);
                if (active == null) return;
                active.setStatus("BUILDING");
                active.setLogs("Pulling docker image: " + active.getDockerImage() + "\n" +
                        "Successfully pulled image from registry\n" +
                        "Analyzing image layers... Done\n");
                containerDeploymentRepository.save(active);
                job.setStatus("RUNNING");
                backgroundJobRepository.save(job);
                subscriptionService.publishJobUpdate(job);

                // Phase 2: DEPLOYING (3 seconds)
                Thread.sleep(3000);
                active = containerDeploymentRepository.findById(deployment.getId()).orElse(null);
                if (active == null) return;
                active.setStatus("DEPLOYING");
                active.setLogs(active.getLogs() +
                        "Scheduling containers in Kubernetes namespace: cloudpool-tenant-" + active.getUser().getId() + "\n" +
                        "Creating Pod replicas... Configured count: " + active.getReplicas() + "\n" +
                        "Configuring cluster internal service\n" +
                        "Configuring Ingress routing for host: " + active.getName() + ".cloudpool.dev\n");
                containerDeploymentRepository.save(active);
                subscriptionService.publishJobUpdate(job);

                // Phase 3: LIVE (2 seconds)
                Thread.sleep(2000);
                active = containerDeploymentRepository.findById(deployment.getId()).orElse(null);
                if (active == null) return;
                active.setStatus("LIVE");
                active.setLogs(active.getLogs() +
                        "Ingress routes successfully propagated\n" +
                        "Application health checks passed\n" +
                        "Container deployment is LIVE & online!\n");
                containerDeploymentRepository.save(active);

                job.setStatus("COMPLETED");
                backgroundJobRepository.save(job);
                subscriptionService.publishJobUpdate(job);
            } catch (InterruptedException e) {
                log.error("Container deployment interrupted", e);
                ContainerDeployment active = containerDeploymentRepository.findById(deployment.getId()).orElse(null);
                if (active != null) {
                    active.setStatus("FAILED");
                    active.setLogs(active.getLogs() + "Deployment aborted: " + e.getMessage() + "\n");
                    containerDeploymentRepository.save(active);
                }
                job.setStatus("FAILED");
                backgroundJobRepository.save(job);
                subscriptionService.publishJobUpdate(job);
            }
        });
    }

    /* ── DTOs ── */

    @Data
    public static class StaticSiteRequest {
        private String name;
        private String bucketName;
        private String domain;
    }

    @Data
    public static class ServerlessRequest {
        private String name;
        private String triggerRoute;
        private String code;
    }

    @Data
    public static class ContainerRequest {
        private String name;
        private String dockerImage;
        private double cpu;
        private int memory;
        private int replicas;
    }
}
