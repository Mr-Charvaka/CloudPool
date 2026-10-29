package com.cloudpool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.cloudpool.config.ConsoleStompConfig;
import com.cloudpool.dto.ConsoleLogEvent;
import com.cloudpool.handler.ConsoleStompAuthInterceptor;
import com.cloudpool.logging.ConsoleLogAppenderRegistrar;
import com.cloudpool.model.User;
import com.cloudpool.model.enums.Role;
import com.cloudpool.repository.UserRepository;
import com.cloudpool.security.JwtUtils;
import com.cloudpool.service.CacheService;
import com.cloudpool.service.ConsoleLogService;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketMessagingAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Drives the real STOMP endpoint over a WebSocket: handshake, JWT in the CONNECT frame, subscription
 * and delivery of a log line produced by the application.
 */
@SpringBootTest(
        classes = ConsoleLogStreamEndToEndTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsoleLogStreamEndToEndTest {

    @Configuration
    @ImportAutoConfiguration({
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebSocketServletAutoConfiguration.class,
            WebSocketMessagingAutoConfiguration.class,
            JacksonAutoConfiguration.class})
    @Import({
            ConsoleStompConfig.class,
            ConsoleStompAuthInterceptor.class,
            ConsoleLogService.class,
            ConsoleLogAppenderRegistrar.class})
    static class TestApp {
    }

    @MockBean private JwtUtils jwtUtils;
    @MockBean private UserRepository userRepository;
    @MockBean private CacheService cacheService;

    @Value("${local.server.port}")
    private int port;

    private WebSocketStompClient client;

    @BeforeEach
    void setUp() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build());
        client.setMessageConverter(converter);
        stubToken("admin-token", "admin@example.com", Role.ADMIN);
        stubToken("user-token", "user@example.com", Role.USER);
    }

    private void stubToken(String token, String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setRole(role);
        user.setActive(true);
        when(cacheService.isTokenBlacklisted(token)).thenReturn(false);
        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(jwtUtils.getEmailFromToken(token)).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
    }

    private String url() {
        return "ws://localhost:" + port + "/ws/console";
    }

    private StompHeaders connectHeaders(String token) {
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("Authorization", "Bearer " + token);
        }
        return headers;
    }

    private CompletableFuture<String> connectExpectingFailure(String token) {
        CompletableFuture<String> failure = new CompletableFuture<>();
        client.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders(token), new StompSessionHandlerAdapter() {
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                failure.complete("ERROR frame: " + headers.getFirst("message"));
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                failure.complete("transport error: " + exception.getMessage());
            }
        });
        return failure;
    }

    @Test
    void anAdminReceivesApplicationLogLinesInRealTime() throws Exception {
        CompletableFuture<Throwable> clientError = new CompletableFuture<>();
        StompSession session = client
                .connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders("admin-token"), new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(StompSession s, org.springframework.messaging.simp.stomp.StompCommand command,
                            StompHeaders headers, byte[] payload, Throwable exception) {
                        clientError.complete(exception);
                    }
                })
                .get(10, TimeUnit.SECONDS);
        assertTrue(session.isConnected());

        BlockingQueue<ConsoleLogEvent> received = new LinkedBlockingQueue<>();
        session.subscribe(ConsoleLogService.TOPIC, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ConsoleLogEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((ConsoleLogEvent) payload);
            }
        });
        Thread.sleep(500); // let the broker register the subscription

        org.slf4j.Logger appLogger = LoggerFactory.getLogger("com.example.deployments");
        ConsoleLogEvent match = null;
        long deadline = System.currentTimeMillis() + 10_000;
        while (match == null && System.currentTimeMillis() < deadline) {
            appLogger.info("e2e-marker deployment {} finished", 42);
            ConsoleLogEvent event = received.poll(500, TimeUnit.MILLISECONDS);
            if (event != null && event.message().contains("e2e-marker deployment 42 finished")) {
                match = event;
            }
        }

        assertTrue(!clientError.isDone(), "client failed to handle a frame: " + clientError.getNow(null));
        assertNotNull(match, "the log line should reach the subscribed admin");
        assertEquals("INFO", match.level());
        assertEquals("com.example.deployments", match.logger());
        assertNotNull(match.timestamp());
        session.disconnect();
    }

    @Test
    void connectingWithoutATokenIsRejected() throws Exception {
        String failure = connectExpectingFailure(null).get(10, TimeUnit.SECONDS);

        assertNotNull(failure);
    }

    @Test
    void connectingAsANonAdminIsRejected() throws Exception {
        String failure = connectExpectingFailure("user-token").get(10, TimeUnit.SECONDS);

        assertNotNull(failure);
    }
}
