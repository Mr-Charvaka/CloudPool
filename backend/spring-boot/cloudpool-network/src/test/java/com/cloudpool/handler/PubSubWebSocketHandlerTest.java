package com.cloudpool.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PubSubWebSocketHandlerTest {

    @Mock private WebSocketSession session;
    @Mock private WebSocketSession subscriberSession;

    private PubSubWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PubSubWebSocketHandler();
        lenient().when(session.getId()).thenReturn("session-main");
        lenient().when(subscriberSession.getId()).thenReturn("session-sub");
        lenient().when(subscriberSession.isOpen()).thenReturn(true);
    }

    @Test
    void afterConnectionEstablished_shouldTrackSession() {
        handler.afterConnectionEstablished(session);
    }

    @Test
    void handleTextMessage_subscribe_shouldAddToChannel() throws Exception {
        handler.afterConnectionEstablished(session);

        String msg = "{\"action\":\"subscribe\",\"channel\":\"events\",\"projectId\":\"proj-1\"}";
        handler.handleTextMessage(session, new TextMessage(msg));
    }

    @Test
    void handleTextMessage_unsubscribe_shouldRemoveFromChannel() throws Exception {
        handler.afterConnectionEstablished(session);

        String subMsg = "{\"action\":\"subscribe\",\"channel\":\"events\",\"projectId\":\"proj-1\"}";
        handler.handleTextMessage(session, new TextMessage(subMsg));

        String unsubMsg = "{\"action\":\"unsubscribe\",\"channel\":\"events\",\"projectId\":\"proj-1\"}";
        handler.handleTextMessage(session, new TextMessage(unsubMsg));
    }

    @Test
    void handleTextMessage_missingProjectId_shouldWarnAndReturn() throws Exception {
        String msg = "{\"action\":\"subscribe\",\"channel\":\"events\",\"projectId\":\"\"}";
        handler.handleTextMessage(session, new TextMessage(msg));
    }

    @Test
    void handleTextMessage_invalidJson_shouldNotThrow() throws Exception {
        handler.handleTextMessage(session, new TextMessage("not json"));
    }

    @Test
    void broadcast_withSubscribers_shouldDeliver() throws Exception {
        handler.afterConnectionEstablished(subscriberSession);

        String subMsg = "{\"action\":\"subscribe\",\"channel\":\"alerts\",\"projectId\":\"proj-1\"}";
        handler.handleTextMessage(subscriberSession, new TextMessage(subMsg));

        handler.broadcast("proj-1:alerts", "{\"msg\":\"test\"}");
        verify(subscriberSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcast_noSubscribers_shouldDoNothing() {
        handler.broadcast("empty-channel", "{\"msg\":\"test\"}");
    }

    @Test
    void afterConnectionClosed_shouldCleanSubscriptions() throws Exception {
        handler.afterConnectionEstablished(subscriberSession);

        String subMsg = "{\"action\":\"subscribe\",\"channel\":\"cleanup\",\"projectId\":\"proj-1\"}";
        handler.handleTextMessage(subscriberSession, new TextMessage(subMsg));

        handler.afterConnectionClosed(subscriberSession, CloseStatus.NORMAL);

        handler.broadcast("proj-1:cleanup", "{\"msg\":\"should-not-reach\"}");
        verify(subscriberSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcast_sessionClosed_shouldSkip() throws Exception {
        when(subscriberSession.isOpen()).thenReturn(false);
        handler.afterConnectionEstablished(subscriberSession);

        String subMsg = "{\"action\":\"subscribe\",\"channel\":\"offline\",\"projectId\":\"proj-1\"}";
        handler.handleTextMessage(subscriberSession, new TextMessage(subMsg));

        handler.broadcast("proj-1:offline", "{\"msg\":\"test\"}");
        verify(subscriberSession, never()).sendMessage(any(TextMessage.class));
    }
}
