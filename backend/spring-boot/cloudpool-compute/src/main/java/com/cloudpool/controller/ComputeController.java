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

@Slf4j
@RestController
@RequestMapping("/api/compute")
@RequiredArgsConstructor
public class ComputeController {

    private final ComputeService computeService;
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
            @RequestParam(value = "path", defaultValue = "index.html") String requestPath) {
        // Implementation remains in StorageService later, or keeping it thin here:
        // TODO: Move file serving payload to StorageService or CDN integration
        return ResponseEntity.status(501).body("Not Implemented in Refactor");
    }

    @GetMapping("/dns/gateway/{domain}/**")
    public ResponseEntity<?> web3Gateway(
            @PathVariable("domain") String domain,
            @RequestParam(value = "path", defaultValue = "index.html") String requestPath) {
        return ResponseEntity.status(501).body("Not Implemented in Refactor");
    }

    /* ── SERVERLESS FUNCTIONS ── */

    @PostMapping("/serverless")
    public ResponseEntity<ServerlessFunction> deployServerlessFunction(@Valid @RequestBody ServerlessRequest request) {
        User user = getAuthenticatedUser();
        ServerlessFunction saved = computeService.deployServerlessFunction(user, request.getName(), request.getTriggerRoute(), request.getCode());
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/serverless")
    public ResponseEntity<List<ServerlessFunction>> listServerlessFunctions() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(computeService.listServerlessFunctions(user));
    }

    @PostMapping("/serverless/{id}/execute")
    public ResponseEntity<?> executeServerlessFunction(
            @PathVariable("id") UUID id,
            @Valid @RequestBody(required = false) Map<String, Object> params) {
        
        User user = getAuthenticatedUser();
        String paramsJson = "{}";
        if (params != null) {
            try {
                paramsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(params);
            } catch (Exception ignored) {}
        }

        String result = computeService.executeServerlessFunction(user, id, paramsJson);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "executionOutput", result,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @DeleteMapping("/serverless/{id}")
    public ResponseEntity<?> deleteServerlessFunction(@PathVariable("id") UUID id) {
        User user = getAuthenticatedUser();
        computeService.deleteServerlessFunction(user, id);
        return ResponseEntity.ok(Map.of("message", "Serverless function deleted"));
    }

    /* ── CONTAINERS ── */

    @PostMapping("/container")
    public ResponseEntity<?> deployContainer(@Valid @RequestBody ContainerRequest request) {
        User user = getAuthenticatedUser();
        ContainerDeployment deployment = computeService.deployContainer(
                user, request.getName(), request.getDockerImage(), request.getCpu(), request.getMemory(), request.getReplicas()
        );
        // Delegate async processing to the named @Bean executor in the service
        computeService.processContainerDeploymentAsync(deployment);
        return ResponseEntity.ok(Map.of("message", "Container deployment initiated", "deploymentId", deployment.getId()));
    }

    @GetMapping("/container")
    public ResponseEntity<List<ContainerDeployment>> listContainers() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(computeService.listContainers(user));
    }

    @DeleteMapping("/container/{id}")
    public ResponseEntity<?> deleteContainer(@PathVariable("id") UUID id) {
        User user = getAuthenticatedUser();
        computeService.deleteContainer(user, id);
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
