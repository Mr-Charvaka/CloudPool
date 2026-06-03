package com.cloudpool.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("debug")
@ConditionalOnProperty(name = "debug.enabled", havingValue = "true")
public class DebugConfig {

    // Enable additional logging
    // Enable metrics
    // Enable health checks
}
