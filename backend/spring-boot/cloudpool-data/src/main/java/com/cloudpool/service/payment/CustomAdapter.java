package com.cloudpool.service.payment;

import com.cloudpool.model.PaymentGateway;
import com.cloudpool.model.PaymentTransaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic adapter for any custom payment provider.
 * Uses the gateway's customBaseUrl to POST a JSON charge payload.
 * Expects the provider to return { "id": "...", "status": "..." } on 200.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAdapter implements PaymentGatewayAdapter {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    @Override
    public PaymentTransaction charge(PaymentGateway gateway, BigDecimal amount,
                                     String currency, String description) {
        PaymentTransaction txn = PaymentTransaction.builder()
                .gateway(gateway)
                .amount(amount)
                .currency(currency.toUpperCase())
                .description(description)
                .status(PaymentTransaction.Status.PENDING)
                .build();
        try {
            String baseUrl = gateway.getCustomBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                txn.setStatus(PaymentTransaction.Status.FAILED);
                txn.setErrorMessage("Custom provider base URL is not configured");
                return txn;
            }

            String apiKey = gateway.getPlainApiKey() != null ? gateway.getPlainApiKey() : gateway.getEncryptedApiKey();
            String body = objectMapper.writeValueAsString(Map.of(
                    "amount",      amount,
                    "currency",    currency,
                    "description", description != null ? description : ""
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl.stripTrailing() + "/charges"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            txn.setProviderResponse(response.body());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                @SuppressWarnings("unchecked")
                Map<String, Object> json = objectMapper.readValue(response.body(), Map.class);
                txn.setStatus(PaymentTransaction.Status.SUCCESS);
                txn.setProviderTransactionId(String.valueOf(json.getOrDefault("id", "custom-" + System.currentTimeMillis())));
                log.info("[Custom] Charge created at {}: {}", baseUrl, txn.getProviderTransactionId());
            } else {
                txn.setStatus(PaymentTransaction.Status.FAILED);
                txn.setErrorMessage("HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (Exception e) {
            txn.setStatus(PaymentTransaction.Status.FAILED);
            txn.setErrorMessage(e.getMessage());
            log.error("[Custom] Exception during charge: {}", e.getMessage());
        }
        return txn;
    }

    @Override
    public Map<String, Object> fetchProviderStats(PaymentGateway gateway) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("provider", "CUSTOM");
        stats.put("baseUrl", gateway.getCustomBaseUrl());
        stats.put("mode", gateway.getMode().name());
        stats.put("note", "Custom provider — connect your own stats endpoint");
        return stats;
    }
}
