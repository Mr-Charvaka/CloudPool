package com.cloudpool.service;

import com.cloudpool.event.DeploymentRequestedEvent;
import com.cloudpool.model.PaymentGateway;
import com.cloudpool.model.PaymentTransaction;
import com.cloudpool.repository.OutboxEventRepository;
import com.cloudpool.repository.PaymentGatewayRepository;
import com.cloudpool.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentGatewayRepository gatewayRepo;

    @Mock
    private PaymentTransactionRepository txnRepo;

    @Mock
    private OutboxEventRepository outboxRepo;

    @InjectMocks
    private PaymentService paymentService;

    private UUID userId;
    private UUID gatewayId;
    private PaymentGateway gateway;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        gatewayId = UUID.randomUUID();
        
        gateway = PaymentGateway.builder()
                .id(gatewayId)
                .userId(userId)
                .provider(PaymentGateway.Provider.RAZORPAY) // Use non-Stripe to hit the simulated mock adapter
                .isActive(true)
                .build();
    }

    @Test
    void testAuthorizePaymentAndRequestDeployment_SuccessSimulated() {
        DeploymentRequestedEvent event = DeploymentRequestedEvent.builder()
                .eventId(UUID.randomUUID())
                .correlationId(UUID.randomUUID())
                .build();

        when(gatewayRepo.findByIdAndUserId(gatewayId, userId)).thenReturn(Optional.of(gateway));
        when(txnRepo.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction txn = invocation.getArgument(0);
            txn.setId(UUID.randomUUID());
            return txn;
        });

        PaymentTransaction result = paymentService.authorizePaymentAndRequestDeployment(
                userId, gatewayId, new BigDecimal("10.00"), "USD", "Desc", null, event
        );

        assertNotNull(result);
        assertEquals(PaymentTransaction.Status.AUTHORIZED, result.getStatus());
        assertTrue(result.getProviderTransactionId().startsWith("sim_"));
        
        verify(outboxRepo, times(1)).save(any());
    }

    @Test
    void testAuthorizePayment_GatewayInactive() {
        gateway.setActive(false);
        when(gatewayRepo.findByIdAndUserId(gatewayId, userId)).thenReturn(Optional.of(gateway));

        assertThrows(IllegalStateException.class, () -> {
            paymentService.authorizePaymentAndRequestDeployment(
                    userId, gatewayId, new BigDecimal("10.00"), "USD", "Desc", null, null
            );
        });
    }

    @Test
    void testAuthorizePayment_IdempotencyConflict() {
        String idempKey = "test-key-123";
        when(txnRepo.findByIdempotencyKey(idempKey)).thenReturn(Optional.of(new PaymentTransaction()));

        assertThrows(com.cloudpool.exception.CloudPoolException.class, () -> {
            paymentService.authorizePaymentAndRequestDeployment(
                    userId, gatewayId, new BigDecimal("10.00"), "USD", "Desc", idempKey, null
            );
        });
    }
}
