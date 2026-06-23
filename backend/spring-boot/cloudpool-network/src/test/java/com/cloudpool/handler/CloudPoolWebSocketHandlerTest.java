package com.cloudpool.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudPoolWebSocketHandlerTest {

    @Mock private WebSocketSession session;
    @Mock private Principal principal;

    private CloudPoolWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CloudPoolWebSocketHandler(new ObjectMapper());
    }

    @Test
    void afterConnectionEstablished_withPrincipal_shouldTrackSession() throws Exception {
        when(session.getId()).thenReturn("session-1");
        when(session.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("user-1");

        handler.afterConnectionEstablished(session);

        verify(session).getId();
    }

    @Test
    void afterConnectionEstablished_withoutPrincipal_shouldTrackAsAnonymous() throws Exception {
        when(session.getId()).thenReturn("session-2");
        when(session.getPrincipal()).thenReturn(null);

        handler.afterConnectionEstablished(session);
    }

    @Test
    void afterConnectionClosed_shouldRemoveSession() throws Exception {
        when(session.getId()).thenReturn("session-1");
        when(session.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("user-1");

        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    }

    @Test
    void handleTextMessage_shouldLogMessage() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"test\":true}"));
    }

    @Test
    void broadcastToUser_withNoSessions_shouldDoNothing() {
        handler.broadcastToUser("nonexistent-user", "{\"msg\":\"test\"}");
    }

    @Test
    void broadcastToUser_withSession_shouldSendMessage() throws Exception {
        when(session.getId()).thenReturn("session-1");
        when(session.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("user-1");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        handler.broadcastToUser("user-1", "{\"msg\":\"hello\"}");

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastToUser_withClosedSession_shouldSkip() throws Exception {
        when(session.getId()).thenReturn("session-1");
        when(session.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("user-1");
        when(session.isOpen()).thenReturn(false);

        handler.afterConnectionEstablished(session);
        handler.broadcastToUser("user-1", "{\"msg\":\"hello\"}");

        verify(session, never()).sendMessage(any(TextMessage.class));
    }
}
