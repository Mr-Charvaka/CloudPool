package com.cloudpool.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.cloudpool.model.User;
import com.cloudpool.model.enums.Role;
import com.cloudpool.repository.UserRepository;
import com.cloudpool.security.JwtUtils;
import com.cloudpool.service.CacheService;
import com.cloudpool.service.ConsoleLogService;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class ConsoleStompAuthInterceptorTest {

    private static final String TOKEN = "valid.jwt.token";

    @Mock private JwtUtils jwtUtils;
    @Mock private UserRepository userRepository;
    @Mock private CacheService cacheService;

    private ConsoleStompAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ConsoleStompAuthInterceptor(jwtUtils, userRepository, cacheService);
    }

    private User user(Role role, boolean active) {
        User user = new User();
        user.setEmail("dev@example.com");
        user.setRole(role);
        user.setActive(active);
        return user;
    }

    private Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> frame(StompCommand command, String destination, Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setSessionId("session-1");
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Principal admin() {
        return new UsernamePasswordAuthenticationToken(
                "dev@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Principal regularUser() {
        return new UsernamePasswordAuthenticationToken(
                "dev@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private void stubValidToken(User user) {
        when(cacheService.isTokenBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtils.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtils.getEmailFromToken(TOKEN)).thenReturn("dev@example.com");
        when(userRepository.findByEmail("dev@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void connectWithAnAdminTokenAuthenticatesTheSession() {
        stubValidToken(user(Role.ADMIN, true));

        Message<?> result = interceptor.preSend(connect("Bearer " + TOKEN), null);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        Principal principal = accessor.getUser();
        assertNotNull(principal);
        assertEquals("dev@example.com", principal.getName());
    }

    @Test
    void connectWithoutAuthorizationHeaderIsRejected() {
        assertThrows(MessagingException.class, () -> interceptor.preSend(connect(null), null));
    }

    @Test
    void connectWithoutBearerPrefixIsRejected() {
        assertThrows(MessagingException.class, () -> interceptor.preSend(connect(TOKEN), null));
    }

    @Test
    void connectWithAnInvalidTokenIsRejected() {
        when(cacheService.isTokenBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtils.validateToken(TOKEN)).thenReturn(false);

        assertThrows(MessagingException.class, () -> interceptor.preSend(connect("Bearer " + TOKEN), null));
    }

    @Test
    void connectWithABlacklistedTokenIsRejected() {
        when(cacheService.isTokenBlacklisted(TOKEN)).thenReturn(true);

        assertThrows(MessagingException.class, () -> interceptor.preSend(connect("Bearer " + TOKEN), null));
    }

    @Test
    void connectWithANonAdminUserIsRejected() {
        stubValidToken(user(Role.USER, true));

        assertThrows(MessagingException.class, () -> interceptor.preSend(connect("Bearer " + TOKEN), null));
    }

    @Test
    void connectWithASuspendedAdminIsRejected() {
        stubValidToken(user(Role.ADMIN, false));

        assertThrows(MessagingException.class, () -> interceptor.preSend(connect("Bearer " + TOKEN), null));
    }

    @Test
    void connectForAnUnknownUserIsRejected() {
        when(cacheService.isTokenBlacklisted(TOKEN)).thenReturn(false);
        when(jwtUtils.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtils.getEmailFromToken(TOKEN)).thenReturn("ghost@example.com");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(MessagingException.class, () -> interceptor.preSend(connect("Bearer " + TOKEN), null));
    }

    @Test
    void adminCanSubscribeToTheSystemTopic() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, ConsoleLogService.TOPIC, admin());

        assertSame(message, interceptor.preSend(message, null));
    }

    @Test
    void subscribingToAnyOtherDestinationIsRejected() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, "/topic/console/other", admin());

        assertThrows(MessagingException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void subscribingWithoutAnAuthenticatedUserIsRejected() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, ConsoleLogService.TOPIC, null);

        assertThrows(MessagingException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void subscribingAsANonAdminIsRejected() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE, ConsoleLogService.TOPIC, regularUser());

        assertThrows(MessagingException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void clientsCannotPublishMessages() {
        Message<byte[]> message = frame(StompCommand.SEND, ConsoleLogService.TOPIC, admin());

        assertThrows(MessagingException.class, () -> interceptor.preSend(message, null));
    }

    @Test
    void disconnectIsAlwaysAllowed() {
        Message<byte[]> message = frame(StompCommand.DISCONNECT, null, null);

        assertSame(message, interceptor.preSend(message, null));
    }
}
