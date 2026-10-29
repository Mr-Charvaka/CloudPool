package com.cloudpool.config;

import com.cloudpool.handler.ConsoleStompAuthInterceptor;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP endpoint that streams backend logs to the developer console. The handshake is open
 * because browsers cannot authenticate it; every STOMP session is authenticated and authorized in
 * {@link ConsoleStompAuthInterceptor}.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class ConsoleStompConfig implements WebSocketMessageBrokerConfigurer {

    private final ConsoleStompAuthInterceptor authInterceptor;

    @Value("${cloudpool.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/console")
                .setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
