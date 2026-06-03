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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stripe adapter — uses the Stripe REST API directly (no SDK dependency).
 * Creates a PaymentIntent and returns real provider data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripeAdapter implements PaymentGatewayAdapter {

    private static final String STRIPE_API = "https://api.stripe.com/v1";
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
            String secretKey = gateway.getPlainSecretKey() != null
                    ? gateway.getPlainSecretKey()
                    : gateway.getEncryptedSecretKey();

            // Stripe amounts are in smallest currency unit (cents)
            long amountCents = amount.multiply(BigDecimal.valueOf(100)).longValue();
            String body = "amount=" + amountCents
                    + "&currency=" + currency.toLowerCase()
                    + "&payment_method_types[]=card"
                    + (description != null ? "&description=" + description : "")
                    + "&confirm=false";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(STRIPE_API + "/payment_intents"))
                    .header("Authorization", "Basic " + Base64.getEncoder()
                            .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            txn.setProviderResponse(responseBody);

            @SuppressWarnings("unchecked")
            Map<String, Object> json = objectMapper.readValue(responseBody, Map.class);

            if (response.statusCode() == 200) {
                txn.setStatus(PaymentTransaction.Status.SUCCESS);
                txn.setProviderTransactionId(String.valueOf(json.get("id")));
                log.info("[Stripe] PaymentIntent created: {}", json.get("id"));
            } else {
                txn.setStatus(PaymentTransaction.Status.FAILED);
                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) json.get("error");
                txn.setErrorMessage(error != null ? String.valueOf(error.get("message")) : "Stripe API error");
                log.warn("[Stripe] Charge failed: {}", txn.getErrorMessage());
            }
        } catch (Exception e) {
            txn.setStatus(PaymentTransaction.Status.FAILED);
            txn.setErrorMessage(e.getMessage());
            log.error("[Stripe] Exception during charge: {}", e.getMessage());
        }
        return txn;
    }

    @Override
    public Map<String, Object> fetchProviderStats(PaymentGateway gateway) {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            String secretKey = gateway.getPlainSecretKey() != null
                    ? gateway.getPlainSecretKey()
                    : gateway.getEncryptedSecretKey();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(STRIPE_API + "/balance"))
                    .header("Authorization", "Basic " + Base64.getEncoder()
                            .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8)))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> json = objectMapper.readValue(response.body(), Map.class);
                stats.put("stripe_balance", json);
                stats.put("provider", "STRIPE");
                stats.put("mode", gateway.getMode().name());
            }
        } catch (Exception e) {
            log.warn("[Stripe] Could not fetch balance: {}", e.getMessage());
            stats.put("error", e.getMessage());
        }
        return stats;
    }
}
