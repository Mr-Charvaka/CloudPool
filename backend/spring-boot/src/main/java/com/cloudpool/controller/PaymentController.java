package com.cloudpool.controller;

import com.cloudpool.model.PaymentGateway;
import com.cloudpool.model.PaymentTransaction;
import com.cloudpool.model.User;
import com.cloudpool.service.PaymentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/dev/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;

    private User currentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    // ──────────────────────────────────────────────────────────
    //  Gateway Management
    // ──────────────────────────────────────────────────────────

    /** List all registered gateways for the authenticated user */
    @GetMapping("/gateways")
    public ResponseEntity<?> listGateways() {
        UUID userId = currentUser().getId();
        List<PaymentGateway> gateways = paymentService.listGateways(userId);
        // Strip plain keys from response — return masked versions
        List<Map<String, Object>> safeList = gateways.stream().map(gw -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",          gw.getId());
            m.put("displayName", gw.getDisplayName());
            m.put("provider",    gw.getProvider().name());
            m.put("mode",        gw.getMode().name());
            m.put("isActive",    gw.isActive());
            m.put("maskedApiKey", gw.getMaskedApiKey());
            m.put("customBaseUrl", gw.getCustomBaseUrl());
            m.put("createdAt",   gw.getCreatedAt());
            return m;
        }).toList();
        return ResponseEntity.ok(safeList);
    }

    /** Register a new payment gateway */
    @PostMapping("/gateways")
    public ResponseEntity<?> registerGateway(@RequestBody RegisterGatewayRequest req) {
        UUID userId = currentUser().getId();
        try {
            PaymentGateway.Provider provider = PaymentGateway.Provider.valueOf(req.getProvider().toUpperCase());
            PaymentGateway.Mode     mode     = req.getMode() != null
                    ? PaymentGateway.Mode.valueOf(req.getMode().toUpperCase())
                    : PaymentGateway.Mode.TEST;

            PaymentGateway saved = paymentService.registerGateway(
                    userId,
                    req.getDisplayName(),
                    provider,
                    mode,
                    req.getApiKey(),
                    req.getSecretKey(),
                    req.getWebhookSecret(),
                    req.getCustomBaseUrl()
            );
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("id",          saved.getId());
            resp.put("displayName", saved.getDisplayName());
            resp.put("provider",    saved.getProvider().name());
            resp.put("mode",        saved.getMode().name());
            resp.put("message",     "Gateway registered successfully");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid provider: " + req.getProvider()
                    + ". Allowed: STRIPE, RAZORPAY, CUSTOM"));
        } catch (Exception e) {
            log.error("Failed to register gateway: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** Delete a payment gateway */
    @DeleteMapping("/gateways/{gatewayId}")
    public ResponseEntity<?> deleteGateway(@PathVariable UUID gatewayId) {
        UUID userId = currentUser().getId();
        try {
            paymentService.deleteGateway(userId, gatewayId);
            return ResponseEntity.ok(Map.of("message", "Gateway deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Charges
    // ──────────────────────────────────────────────────────────

    /** Create a charge on a specific gateway */
    @PostMapping("/gateways/{gatewayId}/charge")
    public ResponseEntity<?> createCharge(@PathVariable UUID gatewayId,
                                          @RequestBody ChargeRequest req) {
        UUID userId = currentUser().getId();
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Amount must be positive"));
        }
        try {
            String currency = req.getCurrency() != null ? req.getCurrency() : "USD";
            PaymentTransaction txn = paymentService.createCharge(userId, gatewayId,
                    req.getAmount(), currency, req.getDescription());
            return ResponseEntity.ok(Map.of(
                    "transactionId",         txn.getId(),
                    "status",                txn.getStatus().name(),
                    "providerTransactionId", txn.getProviderTransactionId() != null ? txn.getProviderTransactionId() : "",
                    "amount",                txn.getAmount(),
                    "currency",              txn.getCurrency(),
                    "errorMessage",          txn.getErrorMessage() != null ? txn.getErrorMessage() : ""
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Charge failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Dashboard Data
    // ──────────────────────────────────────────────────────────

    /** Paginated transaction history for a gateway */
    @GetMapping("/gateways/{gatewayId}/transactions")
    public ResponseEntity<?> getTransactions(@PathVariable UUID gatewayId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        UUID userId = currentUser().getId();
        try {
            Pageable pageable = PageRequest.of(page, Math.min(size, 100));
            Page<PaymentTransaction> txns = paymentService.getTransactions(userId, gatewayId, pageable);
            return ResponseEntity.ok(txns);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Aggregated stats + live provider data for the gateway dashboard */
    @GetMapping("/gateways/{gatewayId}/stats")
    public ResponseEntity<?> getGatewayStats(@PathVariable UUID gatewayId) {
        UUID userId = currentUser().getId();
        try {
            Map<String, Object> stats = paymentService.getGatewayStats(userId, gatewayId);
            return ResponseEntity.ok(stats);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ──────────────────────────────────────────────────────────
    //  DTOs
    // ──────────────────────────────────────────────────────────

    @Data
    public static class RegisterGatewayRequest {
        private String displayName;
        private String provider;        // STRIPE | RAZORPAY | CUSTOM
        private String mode;            // LIVE | TEST (default TEST)
        private String apiKey;          // Publishable key
        private String secretKey;       // Secret key
        private String webhookSecret;   // Optional
        private String customBaseUrl;   // Only for CUSTOM provider
    }

    @Data
    public static class ChargeRequest {
        private BigDecimal amount;
        private String currency;        // e.g. USD, INR
        private String description;
    }
}
