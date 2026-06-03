package com.cloudpool.service;

import com.cloudpool.model.PaymentGateway;
import com.cloudpool.model.PaymentTransaction;
import com.cloudpool.repository.PaymentGatewayRepository;
import com.cloudpool.repository.PaymentTransactionRepository;
import com.cloudpool.service.payment.CustomAdapter;
import com.cloudpool.service.payment.PaymentGatewayAdapter;
import com.cloudpool.service.payment.RazorpayAdapter;
import com.cloudpool.service.payment.StripeAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGatewayRepository  gatewayRepo;
    private final PaymentTransactionRepository txnRepo;

    // Adapters
    private final StripeAdapter    stripeAdapter;
    private final RazorpayAdapter  razorpayAdapter;
    private final CustomAdapter    customAdapter;

    // ─────────────────────────────── Gateway Management ────────

    @Transactional
    public PaymentGateway registerGateway(UUID userId,
                                          String displayName,
                                          PaymentGateway.Provider provider,
                                          PaymentGateway.Mode mode,
                                          String apiKey,
                                          String secretKey,
                                          String webhookSecret,
                                          String customBaseUrl) {
        PaymentGateway gw = PaymentGateway.builder()
                .userId(userId)
                .displayName(displayName)
                .provider(provider)
                .mode(mode)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .webhookSecret(webhookSecret)
                .customBaseUrl(customBaseUrl)
                .isActive(true)
                .build();
        PaymentGateway saved = gatewayRepo.save(gw);
        log.info("Registered payment gateway '{}' ({}) for user {}", displayName, provider, userId);
        return saved;
    }

    public List<PaymentGateway> listGateways(UUID userId) {
        return gatewayRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void deleteGateway(UUID userId, UUID gatewayId) {
        PaymentGateway gw = gatewayRepo.findByIdAndUserId(gatewayId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Gateway not found"));
        gatewayRepo.delete(gw);
        log.info("Deleted payment gateway {} for user {}", gatewayId, userId);
    }

    // ─────────────────────────────── Charging ──────────────────

    @Transactional
    public PaymentTransaction createCharge(UUID userId,
                                           UUID gatewayId,
                                           BigDecimal amount,
                                           String currency,
                                           String description) {
        PaymentGateway gw = gatewayRepo.findByIdAndUserId(gatewayId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Gateway not found or not owned by user"));

        if (!gw.isActive()) {
            throw new IllegalStateException("Gateway '" + gw.getDisplayName() + "' is inactive");
        }

        PaymentGatewayAdapter adapter = resolveAdapter(gw.getProvider());
        PaymentTransaction txn = adapter.charge(gw, amount, currency, description);
        txn.setGateway(gw); // re-attach managed entity
        PaymentTransaction saved = txnRepo.save(txn);
        log.info("Charge {} {} via {} → status={}", amount, currency, gw.getProvider(), saved.getStatus());
        return saved;
    }

    // ─────────────────────────────── Queries ───────────────────

    public Page<PaymentTransaction> getTransactions(UUID userId, UUID gatewayId, Pageable pageable) {
        // Verify ownership
        gatewayRepo.findByIdAndUserId(gatewayId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Gateway not found"));
        return txnRepo.findByGatewayIdOrderByCreatedAtDesc(gatewayId, pageable);
    }

    public Map<String, Object> getGatewayStats(UUID userId, UUID gatewayId) {
        PaymentGateway gw = gatewayRepo.findByIdAndUserId(gatewayId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Gateway not found"));

        long total   = txnRepo.countByGatewayId(gatewayId);
        long success = txnRepo.countByGatewayIdAndStatus(gatewayId, PaymentTransaction.Status.SUCCESS);
        long failed  = txnRepo.countByGatewayIdAndStatus(gatewayId, PaymentTransaction.Status.FAILED);
        BigDecimal volume = txnRepo.sumSuccessfulAmountByGatewayId(gatewayId);
        double successRate = total > 0 ? (double) success / total * 100.0 : 0.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("gatewayId",       gatewayId.toString());
        stats.put("displayName",     gw.getDisplayName());
        stats.put("provider",        gw.getProvider().name());
        stats.put("mode",            gw.getMode().name());
        stats.put("totalTransactions", total);
        stats.put("successCount",    success);
        stats.put("failedCount",     failed);
        stats.put("successRatePct",  Math.round(successRate * 10) / 10.0);
        stats.put("totalVolumeSuccess", volume);
        stats.put("currency",        "mixed"); // per-transaction currency

        // Optionally enrich with live provider stats
        try {
            Map<String, Object> providerStats = resolveAdapter(gw.getProvider()).fetchProviderStats(gw);
            stats.put("providerStats", providerStats);
        } catch (Exception e) {
            log.warn("Could not fetch live provider stats for gateway {}: {}", gatewayId, e.getMessage());
            stats.put("providerStats", Map.of("error", "Could not fetch live provider stats"));
        }

        return stats;
    }

    // ─────────────────────────────── Internal ──────────────────

    private PaymentGatewayAdapter resolveAdapter(PaymentGateway.Provider provider) {
        return switch (provider) {
            case STRIPE    -> stripeAdapter;
            case RAZORPAY  -> razorpayAdapter;
            case CUSTOM    -> customAdapter;
        };
    }
}
