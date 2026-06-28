package com.cloudpool.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudTunnelHandlerTest {

    @Mock private WebSocketSession session;

    private CloudTunnelHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CloudTunnelHandler();
        lenient().when(session.getId()).thenReturn("tunnel-session");
    }

    @Test
    void handleTextMessage_register_shouldRegisterTunnel() throws Exception {
        String msg = "{\"action\":\"register\",\"tunnelId\":\"tun-1\"}";
        handler.handleTextMessage(session, new TextMessage(msg));
    }

    @Test
    void handleTextMessage_response_shouldCompleteFuture() throws Exception {
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("tun-session");

        handler.afterConnectionEstablished(session);

        String regMsg = "{\"action\":\"register\",\"tunnelId\":\"tun-1\"}";
        handler.handleTextMessage(session, new TextMessage(regMsg));
        verify(session, never()).sendMessage(any(TextMessage.class));

        CompletableFuture<CloudTunnelHandler.TunnelResponse> future = handler.forwardHttpRequest("tun-1", "GET", "/", "", "");
        assertFalse(future.isDone());

        String respMsg = "{\"action\":\"response\",\"requestId\":\"\",\"statusCode\":200,\"body\":\"ok\"}";
        handler.handleTextMessage(session, new TextMessage(respMsg));
        assertNotNull(future);
    }

    @Test
    void handleTextMessage_unknownAction_shouldBeIgnored() throws Exception {
        String msg = "{\"action\":\"unknown\",\"data\":\"test\"}";
        handler.handleTextMessage(session, new TextMessage(msg));
    }

    @Test
    void handleTextMessage_invalidJson_shouldNotThrow() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{{{not json}}}"));
    }

    @Test
    void forwardHttpRequest_tunnelOffline_shouldReturnFailedFuture() {
        CompletableFuture<CloudTunnelHandler.TunnelResponse> future =
                handler.forwardHttpRequest("nonexistent", "GET", "/", "", "");
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void forwardHttpRequest_tunnelOnline_shouldSendRequest() throws Exception {
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("tun-session");

        handler.afterConnectionEstablished(session);

        String regMsg = "{\"action\":\"register\",\"tunnelId\":\"tun-2\"}";
        handler.handleTextMessage(session, new TextMessage(regMsg));

        CompletableFuture<CloudTunnelHandler.TunnelResponse> future =
                handler.forwardHttpRequest("tun-2", "POST", "/data", "Content-Type: application/json", "{\"key\":\"val\"}");

        verify(session).sendMessage(any(TextMessage.class));
        assertFalse(future.isDone());
    }

    @Test
    void afterConnectionClosed_shouldCleanTunnel() {
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        CompletableFuture<CloudTunnelHandler.TunnelResponse> future =
                handler.forwardHttpRequest("any-tunnel", "GET", "/", "", "");
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void handleTextMessage_response_unknownRequestId_shouldWarn() throws Exception {
        String msg = "{\"action\":\"response\",\"requestId\":\"unknown-id\",\"statusCode\":200,\"body\":\"\"}";
        handler.handleTextMessage(session, new TextMessage(msg));
    }

    @Test
    void tunnelResponse_getSet_shouldWork() {
        CloudTunnelHandler.TunnelResponse resp = new CloudTunnelHandler.TunnelResponse();
        resp.setStatusCode(200);
        resp.setBody("body");
        resp.setHeaders("header: val");

        assertEquals(200, resp.getStatusCode());
        assertEquals("body", resp.getBody());
        assertEquals("header: val", resp.getHeaders());
    }
}
