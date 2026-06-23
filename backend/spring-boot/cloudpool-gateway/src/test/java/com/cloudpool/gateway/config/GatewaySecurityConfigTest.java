package com.cloudpool.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.adapter.ForwardedHeaderTransformer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class GatewaySecurityConfigTest {

    @Test
    void corsConfigurationSource_shouldBeCreated() {
        GatewaySecurityConfig config = new GatewaySecurityConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", new String[]{"http://localhost:3000"});
        CorsConfigurationSource source = config.corsConfigurationSource();
        assertNotNull(source);
    }

    @Test
    void forwardedHeaderTransformer_shouldBeCreated() {
        GatewaySecurityConfig config = new GatewaySecurityConfig();
        ForwardedHeaderTransformer transformer = config.forwardedHeaderTransformer();
        assertNotNull(transformer);
    }

    @Test
    void corsConfig_shouldThrowOnWildcard() {
        assertThrows(IllegalArgumentException.class, () -> {
            GatewaySecurityConfig config = new GatewaySecurityConfig();
            ReflectionTestUtils.setField(config, "allowedOrigins", new String[]{"*"});
            config.corsConfigurationSource();
        });
    }
}
