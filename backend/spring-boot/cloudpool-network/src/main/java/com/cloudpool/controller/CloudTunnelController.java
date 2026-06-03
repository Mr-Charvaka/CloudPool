package com.cloudpool.controller;

import com.cloudpool.handler.CloudTunnelHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tunnels/{tunnelId}")
@RequiredArgsConstructor
@Slf4j
public class CloudTunnelController {

    private final CloudTunnelHandler cloudTunnelHandler;

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS})
    public ResponseEntity<String> proxy(
            @PathVariable String tunnelId,
            HttpServletRequest request) throws IOException {

        String prefix = "/tunnels/" + tunnelId;
        String uri = request.getRequestURI();
        if (uri.startsWith(prefix)) {
            uri = uri.substring(prefix.length());
        }
        if (uri.isEmpty()) uri = "/";
        if (request.getQueryString() != null) {
            uri += "?" + request.getQueryString();
        }

        String method = request.getMethod();
        String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
        
        // Simplified headers aggregation
        StringBuilder headersBuilder = new StringBuilder();
        request.getHeaderNames().asIterator().forEachRemaining(headerName -> 
            headersBuilder.append(headerName).append(": ").append(request.getHeader(headerName)).append("\n")
        );

        try {
            CloudTunnelHandler.TunnelResponse tunnelResponse = cloudTunnelHandler
                    .forwardHttpRequest(tunnelId, method, uri, headersBuilder.toString(), body)
                    .get(30, TimeUnit.SECONDS);

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.add("X-CloudPool-Tunnel", "true");

            return new ResponseEntity<>(
                    tunnelResponse.getBody(),
                    responseHeaders,
                    HttpStatus.valueOf(tunnelResponse.getStatusCode())
            );

        } catch (TimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body("Tunnel timeout");
        } catch (ExecutionException e) {
            log.error("Execution error on forwarding request for tunnel ID {}: ", tunnelId, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Tunnel connection failed due to gateway execution error");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Thread interrupted");
        }
    }
}
