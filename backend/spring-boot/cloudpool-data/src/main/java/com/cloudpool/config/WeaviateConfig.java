package com.cloudpool.config;

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class WeaviateConfig {

    @Value("${cloudpool.weaviate.url:http://localhost:8081}")
    private String weaviateUrl;

    @Value("${cloudpool.weaviate.api-key:}")
    private String apiKey;

    @Bean
    public WeaviateClient weaviateClient() {
        log.info("Initializing Weaviate client for URL: {}", weaviateUrl);

        // Remove prefix scheme for the config constructor
        String host = weaviateUrl.replace("http://", "").replace("https://", "");
        String scheme = weaviateUrl.startsWith("https") ? "https" : "http";
        
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        Config config = new Config(scheme, host, headers);

        WeaviateClient client = new WeaviateClient(config);

        // Test connection asynchronously/safely so we don't block Spring startup
        try {
            var isReady = client.misc().readyChecker().run();
            if (isReady.getResult() != null && isReady.getResult()) {
                log.info("Weaviate client connected successfully to {}", weaviateUrl);
            } else {
                log.warn("Weaviate is not ready at {}. Vector Search features will fall back to local computation.", weaviateUrl);
            }
        } catch (Exception e) {
            log.warn("Could not connect to Weaviate: {}. Vector Search features will fall back to local computation.", e.getMessage());
        }

        return client;
    }
}
