package com.cloudpool.service;

import com.cloudpool.model.PaymentGateway;
import com.cloudpool.model.PaymentTransaction;
import com.cloudpool.repository.PaymentGatewayRepository;
import com.cloudpool.repository.PaymentTransactionRepository;
import com.cloudpool.repository.OutboxEventRepository;
import com.cloudpool.event.DeploymentRequestedEvent;
import com.cloudpool.event.OutboxEvent;
import com.cloudpool.adapter.PaymentGatewayAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGatewayRepository gatewayRepo;
    private final PaymentTransactionRepository txnRepo;
    private final OutboxEventRepository outboxRepo; // We will create this

    // In a real system, you'd resolve this via factory
    private PaymentGatewayAdapter resolveAdapter(com.cloudpool.model.enums.PaymentProvider provider) {
        return new PaymentGatewayAdapter() {
            @Override
            public PaymentTransaction charge(PaymentGateway gw, BigDecimal amount, String currency, String description) {
                return PaymentTransaction.builder()
                        .amount(amount)
                        .currency(currency)
                        .description(description)
                        .status(PaymentTransaction.Status.AUTHORIZED)
                        .providerTransactionId("sim_" + UUID.randomUUID().toString())
                        .build();
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
        PaymentGatewayAdapter adapter = resolveAdapter(gw.getProvider());
        PaymentTransaction txn = adapter.charge(gw, amount, currency, description);
        txn.setGateway(gw);
        txn.setIdempotencyKey(idempotencyKey);
        PaymentTransaction savedTxn = txnRepo.save(txn);

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
        // Call Stripe Capture
        txn.setStatus(PaymentTransaction.Status.CAPTURED);
        txnRepo.save(txn);
        log.info("Payment Captured: {}", transactionId);
    }

    @Transactional
    public void voidPayment(UUID transactionId) {
        PaymentTransaction txn = txnRepo.findById(transactionId)
                .orElseThrow(() -> new com.cloudpool.exception.ResourceNotFoundException("Txn not found"));
        // Call Stripe Void
        txn.setStatus(PaymentTransaction.Status.VOIDED);
        txnRepo.save(txn);
        log.info("Payment Voided: {}", transactionId);
    }
}
