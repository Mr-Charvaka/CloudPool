package com.cloudpool.service;

import com.cloudpool.model.PaymentGateway;
import com.cloudpool.model.PaymentTransaction;
import com.cloudpool.repository.PaymentGatewayRepository;
import com.cloudpool.repository.PaymentTransactionRepository;
import com.cloudpool.repository.OutboxEventRepository;
import com.cloudpool.event.DeploymentRequestedEvent;
import com.cloudpool.event.OutboxEvent;
import com.cloudpool.adapter.PaymentGatewayAdapter;
import com.cloudpool.adapter.StripePaymentAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGatewayRepository gatewayRepo;
    private final PaymentTransactionRepository txnRepo;
    private final OutboxEventRepository outboxRepo; // We will create this

    // In a real system, you'd resolve this via factory
    private PaymentGatewayAdapter resolveAdapter(com.cloudpool.model.enums.PaymentProvider provider, String secretKey) {
        if (provider == com.cloudpool.model.enums.PaymentProvider.STRIPE) {
            return new StripePaymentAdapter(secretKey);
        }
        return new PaymentGatewayAdapter() {
            @Override
            public PaymentTransaction createPayment(PaymentTransaction transaction) {
                transaction.setStatus(PaymentTransaction.Status.AUTHORIZED);
                transaction.setProviderTransactionId("sim_" + UUID.randomUUID().toString());
                return transaction;
            }

            @Override
            public PaymentTransaction processCallback(com.cloudpool.model.enums.PaymentProvider cbProvider, String payload) {
                return null;
            }
        };
    }

    @Transactional
    public PaymentTransaction authorizePaymentAndRequestDeployment(
            UUID userId, UUID gatewayId, BigDecimal amount, String currency, String description,
            String idempotencyKey, DeploymentRequestedEvent event) {

        if (idempotencyKey != null) {
            txnRepo.findByIdempotencyKey(idempotencyKey).ifPresent(existingTxn -> {
                throw new com.cloudpool.exception.CloudPoolException("Idempotency conflict");
            });
        }

        PaymentGateway gw = gatewayRepo.findByIdAndUserId(gatewayId, userId)
                .orElseThrow(() -> new com.cloudpool.exception.ResourceNotFoundException("Gateway not found"));

        if (!gw.isActive()) throw new IllegalStateException("Gateway inactive");

        // 1. Authorize Payment
        PaymentTransaction txn = PaymentTransaction.builder()
                .amount(amount)
                .currency(currency)
                .description(description)
                .build();
        txn.setGateway(gw);
        txn.setIdempotencyKey(idempotencyKey);
        PaymentGatewayAdapter adapter = resolveAdapter(com.cloudpool.model.enums.PaymentProvider.valueOf(gw.getProvider().name()), gw.getSecretKey());
        PaymentTransaction processed = adapter.createPayment(txn);
        PaymentTransaction savedTxn = txnRepo.save(processed);

        log.info("Payment Authorized: {}", savedTxn.getId());

        // 2. Transactional Outbox Pattern
        try {
            String payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventId(event.getEventId())
                    .aggregateType("DEPLOYMENT")
                    .aggregateId(event.getCorrelationId().toString())
                    .eventType("DeploymentRequestedEvent")
                    .payload(payload)
                    .createdAt(Instant.now())
                    .status(OutboxEvent.OutboxStatus.PENDING)
                    .build();
            outboxRepo.save(outboxEvent);
            log.info("Saved event to Outbox: {}", event.getEventId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }

        return savedTxn;
    }

    @Transactional
    public void capturePayment(UUID transactionId) {
        PaymentTransaction txn = txnRepo.findById(transactionId)
                .orElseThrow(() -> new com.cloudpool.exception.ResourceNotFoundException("Txn not found"));
        
        if (txn.getGateway().getProvider() == PaymentGateway.Provider.STRIPE) {
            StripePaymentAdapter adapter = new StripePaymentAdapter(txn.getGateway().getSecretKey());
            adapter.capturePayment(txn);
        } else {
            txn.setStatus(PaymentTransaction.Status.CAPTURED);
        }
        
        txnRepo.save(txn);
        log.info("Payment Captured: {}", transactionId);
    }

    @Transactional
    public void voidPayment(UUID transactionId) {
        PaymentTransaction txn = txnRepo.findById(transactionId)
                .orElseThrow(() -> new com.cloudpool.exception.ResourceNotFoundException("Txn not found"));
        
        if (txn.getGateway().getProvider() == PaymentGateway.Provider.STRIPE) {
            StripePaymentAdapter adapter = new StripePaymentAdapter(txn.getGateway().getSecretKey());
            adapter.voidPayment(txn);
        } else {
            txn.setStatus(PaymentTransaction.Status.VOIDED);
        }
        
        txnRepo.save(txn);
        log.info("Payment Voided: {}", transactionId);
    }

    public List<PaymentGateway> listGateways(UUID userId) {
        return gatewayRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public PaymentGateway registerGateway(UUID userId, String displayName, PaymentGateway.Provider provider, PaymentGateway.Mode mode, String apiKey, String apiSecret, String webhookSecret, String endpoint) {
        PaymentGateway gw = PaymentGateway.builder()
                .userId(userId)
                .displayName(displayName)
                .provider(provider)
                .mode(mode)
                .apiKey(apiKey)
                .secretKey(apiSecret)
                .webhookSecret(webhookSecret)
                .customBaseUrl(endpoint)
                .isActive(true)
                .build();
        return gatewayRepo.save(gw);
    }

    @Transactional
    public void deleteGateway(UUID userId, UUID gatewayId) {
        gatewayRepo.findByIdAndUserId(gatewayId, userId).ifPresent(gatewayRepo::delete);
    }

    @Transactional
    public PaymentTransaction createCharge(UUID userId, UUID gatewayId, BigDecimal amount, String currency, String description) {
        PaymentGateway gw = gatewayRepo.findByIdAndUserId(gatewayId, userId)
                .orElseThrow(() -> new com.cloudpool.exception.ResourceNotFoundException("Gateway not found"));
        PaymentTransaction txn = PaymentTransaction.builder()
                .amount(amount)
                .currency(currency)
                .description(description)
                .status(PaymentTransaction.Status.AUTHORIZED)
                .build();
        txn.setGateway(gw);
        return txnRepo.save(txn);
    }

    public List<PaymentTransaction> getTransactions(UUID userId, UUID gatewayId, org.springframework.data.domain.Pageable pageable) {
        return txnRepo.findByGatewayIdOrderByCreatedAtDesc(gatewayId, pageable).getContent();
    }

    public Map<String, Object> getGatewayStats(UUID userId, UUID gatewayId) {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("totalTransactions", txnRepo.countByGatewayId(gatewayId));
        stats.put("successfulTransactions", 0L);
        return stats;
    }
}
