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
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class PubSubWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // channelName -> Set of sessions
    private final Map<String, Set<WebSocketSession>> channelSubscribers = new ConcurrentHashMap<>();
    
    // sessionId -> Set of channelNames
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Pub/Sub Client connected: {}", session.getId());
        sessionSubscriptions.put(session.getId(), Collections.newSetFromMap(new ConcurrentHashMap<>()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode payload = objectMapper.readTree(message.getPayload());
            String action = payload.has("action") ? payload.get("action").asText() : "";
            String channel = payload.has("channel") ? payload.get("channel").asText() : "";
            String projectId = payload.has("projectId") ? payload.get("projectId").asText() : "";

            if (projectId.isBlank()) {
                log.warn("Missing projectId in Pub/Sub message");
                return;
            }

            String scopedChannel = projectId + ":" + channel;

            if ("subscribe".equalsIgnoreCase(action) && !channel.isBlank()) {
                channelSubscribers.computeIfAbsent(scopedChannel, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(session);
                sessionSubscriptions.get(session.getId()).add(scopedChannel);
                log.info("Session {} subscribed to channel '{}'", session.getId(), scopedChannel);
            } else if ("unsubscribe".equalsIgnoreCase(action) && !channel.isBlank()) {
                Set<WebSocketSession> subs = channelSubscribers.get(scopedChannel);
                if (subs != null) {
                    subs.remove(session);
                }
                sessionSubscriptions.get(session.getId()).remove(scopedChannel);
                log.info("Session {} unsubscribed from channel '{}'", session.getId(), scopedChannel);
            }
        } catch (Exception e) {
            log.warn("Invalid message received on Pub/Sub: {}", message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Pub/Sub Client disconnected: {}", session.getId());
        Set<String> channels = sessionSubscriptions.remove(session.getId());
        if (channels != null) {
            for (String channel : channels) {
                Set<WebSocketSession> subs = channelSubscribers.get(channel);
                if (subs != null) {
                    subs.remove(session);
                }
            }
        }
    }

    public void broadcast(String channel, String messagePayload) {
        Set<WebSocketSession> sessions = channelSubscribers.get(channel);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        TextMessage msg = new TextMessage(messagePayload);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(msg);
                } catch (IOException e) {
                    log.error("Failed to send message to session {}", session.getId());
                }
            }
        }
    }
}
