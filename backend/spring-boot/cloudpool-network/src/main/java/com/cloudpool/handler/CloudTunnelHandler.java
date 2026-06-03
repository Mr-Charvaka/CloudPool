package com.cloudpool.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class CloudTunnelHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // tunnelId -> WebSocketSession (CLI client)
    private final Map<String, WebSocketSession> activeTunnels = new ConcurrentHashMap<>();
    
    // requestId -> CompletableFuture (HTTP request waiting for CLI response)
    private final Map<String, CompletableFuture<TunnelResponse>> pendingRequests = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Cloud Tunnel CLI connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode payload = objectMapper.readTree(message.getPayload());
            String action = payload.has("action") ? payload.get("action").asText() : "";
            
            // CLI registers a tunnel
            if ("register".equalsIgnoreCase(action)) {
                String tunnelId = payload.get("tunnelId").asText();
                activeTunnels.put(tunnelId, session);
                log.info("Tunnel '{}' registered to session {}", tunnelId, session.getId());
                return;
            }

            // CLI returns HTTP response for a pending request
            if ("response".equalsIgnoreCase(action)) {
                String requestId = payload.get("requestId").asText();
                CompletableFuture<TunnelResponse> future = pendingRequests.remove(requestId);
                if (future != null) {
                    TunnelResponse response = new TunnelResponse();
                    response.setStatusCode(payload.get("statusCode").asInt());
                    response.setBody(payload.has("body") ? payload.get("body").asText() : "");
                    // Also parse headers if needed
                    future.complete(response);
                } else {
                    log.warn("Received response for unknown requestId: {}", requestId);
                }
            }
        } catch (Exception e) {
            log.warn("Cloud Tunnel error parsing message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Cloud Tunnel CLI disconnected: {}", session.getId());
        activeTunnels.values().remove(session);
    }

    public CompletableFuture<TunnelResponse> forwardHttpRequest(String tunnelId, String method, String uri, String headers, String body) {
        WebSocketSession session = activeTunnels.get(tunnelId);
        if (session == null || !session.isOpen()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Tunnel is offline"));
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<TunnelResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        try {
            String requestPayload = objectMapper.writeValueAsString(Map.of(
                    "action", "request",
                    "requestId", requestId,
                    "method", method,
                    "uri", uri,
                    "headers", headers,
                    "body", body != null ? body : ""
            ));
            session.sendMessage(new TextMessage(requestPayload));
        } catch (IOException e) {
            pendingRequests.remove(requestId);
            future.completeExceptionally(e);
        }

        return future;
    }

    public static class TunnelResponse {
        private int statusCode;
        private String body;
        private String headers;

        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getHeaders() { return headers; }
        public void setHeaders(String headers) { this.headers = headers; }
    }
}
