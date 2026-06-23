package com.cloudpool.controller;

import com.cloudpool.service.DrapTunnelService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tunnels")
@RequiredArgsConstructor
public class TunnelController {

    private final DrapTunnelService drapTunnelService;

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startTunnel(@Valid @RequestBody TunnelRequest request) {
        drapTunnelService.startTunnel(request.getPort(), request.getSubdomain());
        return ResponseEntity.ok(Map.of(
                "status", "STARTED",
                "message", "Tunnel initiated for " + request.getSubdomain() + " -> localhost:" + request.getPort(),
                "public_url", "https://" + request.getSubdomain() + ".empirebot.in"
        ));
    }

    @PostMapping("/stop/{subdomain}")
    public ResponseEntity<Map<String, String>> stopTunnel(@PathVariable String subdomain) {
        drapTunnelService.stopTunnel(subdomain);
        return ResponseEntity.ok(Map.of(
                "status", "STOPPED",
                "message", "Tunnel terminated for " + subdomain
        ));
    }

    @GetMapping("/status/{subdomain}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String subdomain) {
        boolean active = drapTunnelService.isTunnelActive(subdomain);
        return ResponseEntity.ok(Map.of(
                "subdomain", subdomain,
                "active", active
        ));
    }

    @Data
    public static class TunnelRequest {
        @Min(1)
        @Max(65535)
        private int port;

        @NotBlank
        private String subdomain;
    }
}
