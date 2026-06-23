package com.cloudpool.controller;

import com.cloudpool.handler.PubSubWebSocketHandler;
import com.cloudpool.model.User;
import com.cloudpool.service.ProjectService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/pubsub")
@RequiredArgsConstructor

public class PubSubController {

    private final PubSubWebSocketHandler pubSubHandler;
    private final ProjectService projectService;

    private void validateProjectAccess(UUID projectId) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        projectService.getProject(projectId, user.getId()); // Throws if unauthorized
    }

    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcast(
            @PathVariable UUID projectId,
            @Valid @RequestBody BroadcastRequest request) {
        validateProjectAccess(projectId);
        try {
            // Scope channels by projectId to ensure tenants don't overlap
            String scopedChannel = projectId.toString() + ":" + request.getChannel();
            
            // Format payload
            String payloadStr = String.format("{\"channel\":\"%s\", \"message\":%s}", request.getChannel(), request.getPayloadJson());
            
            pubSubHandler.broadcast(scopedChannel, payloadStr);
            return ResponseEntity.ok(Map.of("message", "Broadcasted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Data
    public static class BroadcastRequest {
        @NotBlank(message = "Channel cannot be blank")
        private String channel;
        
        @NotBlank(message = "Payload JSON cannot be blank")
        private String payloadJson; // Must be valid JSON string
    }
}
