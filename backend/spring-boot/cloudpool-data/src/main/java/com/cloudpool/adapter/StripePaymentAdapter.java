package com.cloudpool.adapter;

import com.cloudpool.model.PaymentTransaction;
import com.cloudpool.model.enums.PaymentProvider;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Slf4j
public class StripePaymentAdapter implements PaymentGatewayAdapter {

    private final String secretKey;

    public StripePaymentAdapter(String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public PaymentTransaction createPayment(PaymentTransaction transaction) {
        Stripe.apiKey = this.secretKey;
        try {
            long amountInCents = transaction.getAmount().multiply(new BigDecimal("100")).longValue();
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(transaction.getCurrency() != null ? transaction.getCurrency().toLowerCase() : "usd")
                    .setDescription(transaction.getDescription())
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            
            transaction.setStatus(PaymentTransaction.Status.AUTHORIZED);
            transaction.setProviderTransactionId(intent.getId());
        } catch (Exception e) {
            log.error("Failed to create Stripe payment", e);
            transaction.setStatus(PaymentTransaction.Status.FAILED);
            throw new RuntimeException("Payment creation failed", e);
        }
        return transaction;
    }

    @Override
    public PaymentTransaction processCallback(PaymentProvider provider, String payload) {
        return null; // Webhooks handled separately
    }

    public void capturePayment(PaymentTransaction transaction) {
        Stripe.apiKey = this.secretKey;
        try {
            PaymentIntent intent = PaymentIntent.retrieve(transaction.getProviderTransactionId());
            intent.capture();
            transaction.setStatus(PaymentTransaction.Status.CAPTURED);
        } catch (Exception e) {
            log.error("Failed to capture Stripe payment", e);
            throw new RuntimeException("Capture failed", e);
        }
    }

    public void voidPayment(PaymentTransaction transaction) {
        Stripe.apiKey = this.secretKey;
        try {
            PaymentIntent intent = PaymentIntent.retrieve(transaction.getProviderTransactionId());
            intent.cancel();
            transaction.setStatus(PaymentTransaction.Status.VOIDED);
        } catch (Exception e) {
            log.error("Failed to void Stripe payment", e);
            throw new RuntimeException("Void failed", e);
        }
    }
}
