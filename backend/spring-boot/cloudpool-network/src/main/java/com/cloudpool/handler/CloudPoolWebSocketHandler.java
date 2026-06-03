package com.cloudpool.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudPoolWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private static final Map<String, Set<WebSocketSession>> SESSIONS = 
        new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connected: {}", session.getId());
        
        String userId = extractUserId(session);
        SESSIONS.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
            .add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) 
            throws Exception {
        log.debug("Received WebSocket message: {}", message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) 
            throws Exception {
        log.info("WebSocket disconnected: {}", session.getId());
        
        String userId = extractUserId(session);
        Set<WebSocketSession> sessions = SESSIONS.get(userId);
        if (sessions != null) {
            sessions.remove(session);
        }
    }

    /**
     * Broadcast message to all connected clients of a user
     */
    public void broadcastToUser(String userId, String message) {
        Set<WebSocketSession> sessions = SESSIONS.getOrDefault(userId, new HashSet<>());
        
        sessions.forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                log.error("Error sending WebSocket message", e);
            }
        });
    }

    /**
     * Extract user ID from session
     */
    private String extractUserId(WebSocketSession session) {
        return session.getPrincipal() != null ? 
            session.getPrincipal().getName() : "anonymous";
    }
}
