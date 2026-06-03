package com.cloudpool.config;

import com.cloudpool.handler.CloudPoolWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final CloudPoolWebSocketHandler webSocketHandler;
    private final com.cloudpool.handler.PubSubWebSocketHandler pubSubWebSocketHandler;
    private final com.cloudpool.handler.CloudTunnelHandler cloudTunnelHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws/events")
            .setAllowedOrigins("*");
        
        registry.addHandler(pubSubWebSocketHandler, "/ws/pubsub")
            .setAllowedOrigins("*");

        registry.addHandler(cloudTunnelHandler, "/ws/tunnel")
            .setAllowedOrigins("*");
    }
}
