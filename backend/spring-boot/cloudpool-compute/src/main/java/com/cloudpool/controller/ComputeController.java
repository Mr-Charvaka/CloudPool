package com.cloudpool.controller;

import com.cloudpool.model.*;
import com.cloudpool.service.ComputeService;
import com.cloudpool.service.StorageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import com.cloudpool.publisher.DeploymentPublisher;
import com.cloudpool.event.DeploymentRequestedEvent;

@Slf4j
@RestController
@RequestMapping("/api/compute")
@RequiredArgsConstructor
public class ComputeController {

    private final ComputeService computeService;
    private final DeploymentPublisher deploymentPublisher;
    private final StorageService storageService;

    private User getAuthenticatedUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /* ── STATIC SITES & WEB3 GATEWAY ── */

    @PostMapping("/static")
    public ResponseEntity<StaticSite> deployStaticSite(@Valid @RequestBody StaticSiteRequest request) {
        User user = getAuthenticatedUser();
        StaticSite saved = computeService.deployStaticSite(user, request.getName(), request.getBucketName(), request.getDomain());
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/static")
    public ResponseEntity<List<StaticSite>> listStaticSites() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(computeService.listStaticSites(user));
    }

    @DeleteMapping("/static/{id}")
    public ResponseEntity<?> deleteStaticSite(@PathVariable("id") UUID id) {
        User user = getAuthenticatedUser();
        computeService.deleteStaticSite(user, id);
        return ResponseEntity.ok(Map.of("message", "Static site deleted"));
    }

    @GetMapping("/static/serve/{domain}/**")
    public ResponseEntity<?> serveStaticFile(
            @PathVariable("domain") String domain,
            @RequestParam(value = "path", required = false) String requestPath,
            jakarta.servlet.http.HttpServletRequest request) { 
        
        try {
            StaticSite site = computeService.getStaticSiteByDomain(domain);
            
            String filePath = requestPath;
            if (filePath == null || filePath.isEmpty()) {
                String path = (String) request.getAttribute(org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
                String bestMatchPattern = (String) request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                filePath = new org.springframework.util.AntPathMatcher().extractPathWithinPattern(bestMatchPattern, path);
            }
            
            if (filePath == null || filePath.isEmpty() || filePath.equals("/")) {
                filePath = "index.html";
            }
            
            FileMetadata metadata = storageService.getFileMetadata(site.getUser(), site.getBucketName(), filePath);
            org.springframework.core.io.Resource resource = storageService.downloadFileDirectly(metadata);
            
            String contentType = metadata.getMimeType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("Failed to serve static file for domain: " + domain, e);
            return ResponseEntity.status(404).body("File not found");
        }
    }

    @GetMapping("/dns/gateway/{domain}/**")
    public ResponseEntity<?> web3Gateway(
            @PathVariable("domain") String domain,
            @RequestParam(value = "path", required = false) String requestPath,
            jakarta.servlet.http.HttpServletRequest request) {
        return serveStaticFile(domain, requestPath, request);
    }

    /* ── SERVERLESS FUNCTIONS ── */

    @PostMapping("/serverless")
    public ResponseEntity<ServerlessFunction> deployServerlessFunction(@Valid @RequestBody ServerlessRequest request) {
        User user = getAuthenticatedUser();
        ServerlessFunction saved = computeService.deployServerlessFunction(user.getId(), request.getName(), request.getTriggerRoute(), request.getCode());
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/serverless")
    public ResponseEntity<List<ServerlessFunction>> listServerlessFunctions() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(computeService.listServerlessFunctions(user));
    }

    @PostMapping("/serverless/{id}/execute")
    public java.util.concurrent.CompletableFuture<ResponseEntity<?>> executeServerlessFunction(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) Map<String, Object> params) {
        
        User user = getAuthenticatedUser();
        String paramsJson = "{}";
        if (params != null) {
            try {
                paramsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(params);
            } catch (Exception ignored) {}
        }

        return computeService.executeServerlessFunctionAsync(user.getId(), id, paramsJson)
                .thenApply(result -> ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "executionOutput", result,
                        "timestamp", LocalDateTime.now().toString()
                )));
    }

    @DeleteMapping("/serverless/{id}")
    public ResponseEntity<?> deleteServerlessFunction(@PathVariable("id") UUID id) {
        User user = getAuthenticatedUser();
        computeService.deleteServerlessFunction(user.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Serverless function deleted"));
    }

    /* ── CONTAINERS ── */

    @PostMapping("/container")
    public ResponseEntity<?> deployContainer(@Valid @RequestBody ContainerRequest request) {
        User user = getAuthenticatedUser();
        
        java.util.UUID eventId = java.util.UUID.randomUUID();
        DeploymentRequestedEvent event = DeploymentRequestedEvent.builder()
                .eventId(eventId)
                .userId(user.getId())
                .name(request.getName())
                .dockerImage(request.getDockerImage())
                .cpu(request.getCpu())
                .memory(request.getMemory())
                .replicas(request.getReplicas())
                .timestamp(java.time.Instant.now())
                .build();
                
        deploymentPublisher.requestDeployment(event);
        
        return ResponseEntity.ok(Map.of("message", "Container deployment initiated via event stream", "eventId", eventId));
    }

    @GetMapping("/container")
    public ResponseEntity<List<ContainerDeployment>> listContainers() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(computeService.listContainers(user));
    }

    @DeleteMapping("/container/{id}")
    public ResponseEntity<?> deleteContainer(@PathVariable("id") UUID id) {
        User user = getAuthenticatedUser();
        computeService.deleteContainer(user.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Container deleted"));
    }

    /* ── DTOs ── */

    @Data
    public static class StaticSiteRequest {
        @jakarta.validation.constraints.NotBlank
        private String name;
        private String bucketName;
        private String domain;
    }

    @Data
    public static class ServerlessRequest {
        @jakarta.validation.constraints.NotBlank
        private String name;
        private String triggerRoute;
        private String code;
    }

    @Data
    public static class ContainerRequest {
        @jakarta.validation.constraints.NotBlank
        private String name;
        private String dockerImage;
        private double cpu;
        private int memory;
        private int replicas;
    }
}
