package com.cloudpool.handler;

import com.cloudpool.model.User;
import com.cloudpool.model.enums.Role;
import com.cloudpool.repository.UserRepository;
import com.cloudpool.security.JwtUtils;
import com.cloudpool.service.CacheService;
import com.cloudpool.service.ConsoleLogService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP sessions for the developer console. Browsers cannot set headers on the
 * WebSocket handshake, so the JWT travels in the STOMP CONNECT frame ({@code Authorization:
 * Bearer ...}). Only active admins may connect or subscribe, only to the system log topic, and
 * clients are never allowed to publish.
 */
@Component
@RequiredArgsConstructor
public class ConsoleStompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final CacheService cacheService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            accessor.setUser(authenticate(accessor));
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscription(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            throw new MessagingException("Publishing to the console is not allowed");
        }
        return message;
    }

    private Principal authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new MessagingException("Authentication required");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();

        try {
            if (cacheService.isTokenBlacklisted(token) || !jwtUtils.validateToken(token)) {
                throw new MessagingException("Authentication required");
            }
            User user = userRepository.findByEmail(jwtUtils.getEmailFromToken(token))
                    .filter(User::isActive)
                    .orElseThrow(() -> new MessagingException("Authentication required"));
            if (user.getRole() != Role.ADMIN) {
                throw new MessagingException("Administrator role required");
            }
            return new UsernamePasswordAuthenticationToken(
                    user.getEmail(), null, List.of(new SimpleGrantedAuthority(ADMIN_AUTHORITY)));
        } catch (MessagingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MessagingException("Authentication required");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        boolean admin = principal instanceof Authentication authentication
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        if (!admin) {
            throw new MessagingException("Administrator role required");
        }
        if (!ConsoleLogService.TOPIC.equals(accessor.getDestination())) {
            throw new MessagingException("Unknown destination");
        }
    }
}
