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
 * Razorpay adapter — uses the Razorpay REST API directly (no SDK dependency).
 * Creates an Order (the server-side step; the frontend then shows the Razorpay
 * checkout widget to collect the actual payment).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayAdapter implements PaymentGatewayAdapter {

    private static final String RAZORPAY_API = "https://api.razorpay.com/v1";
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
            String keyId     = gateway.getPlainApiKey()    != null ? gateway.getPlainApiKey()    : gateway.getEncryptedApiKey();
            String keySecret = gateway.getPlainSecretKey() != null ? gateway.getPlainSecretKey() : gateway.getEncryptedSecretKey();

            // Razorpay amounts are in smallest currency unit (paise for INR)
            long amountPaise = amount.multiply(BigDecimal.valueOf(100)).longValue();
            String body = objectMapper.writeValueAsString(Map.of(
                    "amount",   amountPaise,
                    "currency", currency.toUpperCase(),
                    "receipt",  "cp_" + System.currentTimeMillis(),
                    "notes",    Map.of("description", description != null ? description : "CloudPool payment")
            ));

            String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret)
                    .getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RAZORPAY_API + "/orders"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
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
                log.info("[Razorpay] Order created: {}", json.get("id"));
            } else {
                txn.setStatus(PaymentTransaction.Status.FAILED);
                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) json.get("error");
                txn.setErrorMessage(error != null ? String.valueOf(error.get("description")) : "Razorpay API error");
                log.warn("[Razorpay] Order creation failed: {}", txn.getErrorMessage());
            }
        } catch (Exception e) {
            txn.setStatus(PaymentTransaction.Status.FAILED);
            txn.setErrorMessage(e.getMessage());
            log.error("[Razorpay] Exception during charge: {}", e.getMessage());
        }
        return txn;
    }

    @Override
    public Map<String, Object> fetchProviderStats(PaymentGateway gateway) {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            String keyId     = gateway.getPlainApiKey()    != null ? gateway.getPlainApiKey()    : gateway.getEncryptedApiKey();
            String keySecret = gateway.getPlainSecretKey() != null ? gateway.getPlainSecretKey() : gateway.getEncryptedSecretKey();

            String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret)
                    .getBytes(StandardCharsets.UTF_8));

            // Fetch last 10 payments summary
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RAZORPAY_API + "/payments?count=1"))
                    .header("Authorization", "Basic " + auth)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> json = objectMapper.readValue(response.body(), Map.class);
                stats.put("razorpay_payments", json);
                stats.put("provider", "RAZORPAY");
                stats.put("mode", gateway.getMode().name());
            }
        } catch (Exception e) {
            log.warn("[Razorpay] Could not fetch payments: {}", e.getMessage());
            stats.put("error", e.getMessage());
        }
        return stats;
    }
}
