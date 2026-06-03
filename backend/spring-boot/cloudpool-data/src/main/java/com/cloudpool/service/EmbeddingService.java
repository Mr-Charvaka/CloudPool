package com.cloudpool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    @Value("${cloudpool.openai.api-key:}")
    private String apiKey;

    @Value("${cloudpool.openai.model:text-embedding-ada-002}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generate vector embedding for input text
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[1536];
        }

        if (apiKey == null || apiKey.trim().isEmpty() || "your-openai-key-here".equals(apiKey)) {
            log.warn("OpenAI API key not configured. Returning dummy embedding (1536 dims).");
            return generateMockEmbedding(text);
        }

        int maxRetries = 3;
        int attempt = 0;
        long backoffMs = 1000;

        while (attempt < maxRetries) {
            try {
                String url = "https://api.openai.com/v1/embeddings";

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(apiKey);

                Map<String, Object> body = new HashMap<>();
                body.put("input", text);
                body.put("model", model);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    JsonNode embeddingNode = root.path("data").get(0).path("embedding");
                    
                    float[] vector = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        vector[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    return vector;
                } else {
                    int statusCode = response.getStatusCode().value();
                    if (statusCode == 429 || statusCode == 503 || statusCode >= 500) {
                        attempt++;
                        if (attempt >= maxRetries) {
                            throw new RuntimeException("Failed to fetch embedding: HTTP status " + statusCode);
                        }
                        log.warn("Transient error from OpenAI (HTTP {}), retrying attempt {}/{} in {} ms...",
                                statusCode, attempt, maxRetries, backoffMs);
                        Thread.sleep(backoffMs);
                        backoffMs *= 2;
                    } else {
                        throw new RuntimeException("Failed to fetch embedding: HTTP status " + response.getStatusCode());
                    }
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Embedding generation interrupted", e);
                }
                attempt++;
                if (attempt >= maxRetries) {
                    log.error("Failed to generate embedding after {} attempts: {}", maxRetries, e.getMessage());
                    throw new RuntimeException("Error generating embedding from OpenAI: " + e.getMessage(), e);
                }
                log.warn("Error calling OpenAI (attempt {}/{}), retrying in {} ms: {}",
                        attempt, maxRetries, backoffMs, e.getMessage());
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
                backoffMs *= 2;
            }
        }
        return generateMockEmbedding(text);
    }

    private float[] generateMockEmbedding(String text) {
        float[] vector = new float[1536];
        // Generate pseudo-random vector based on string hash for consistency
        int hash = text.hashCode();
        java.util.Random rand = new java.util.Random(hash);
        for (int i = 0; i < 1536; i++) {
            vector[i] = rand.nextFloat() * 2.0f - 1.0f;
        }
        // Normalize the vector
        float magnitude = 0.0f;
        for (float val : vector) {
            magnitude += val * val;
        }
        magnitude = (float) Math.sqrt(magnitude);
        if (magnitude > 0) {
            for (int i = 0; i < 1536; i++) {
                vector[i] /= magnitude;
            }
        }
        return vector;
    }
}
