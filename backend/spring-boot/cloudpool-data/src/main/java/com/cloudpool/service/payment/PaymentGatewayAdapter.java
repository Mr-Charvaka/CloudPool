package com.cloudpool.service.payment;

import com.cloudpool.model.PaymentGateway;
import com.cloudpool.model.PaymentTransaction;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Strategy interface for payment provider adapters.
 * Each provider (Stripe, Razorpay, Custom) implements this to encapsulate
 * provider-specific API calls.
 */
public interface PaymentGatewayAdapter {

    /**
     * Create a charge / payment intent on the provider side.
     * The returned PaymentTransaction will have status set by the adapter.
     */
    PaymentTransaction charge(PaymentGateway gateway, BigDecimal amount,
                               String currency, String description);

    /**
     * Fetch live stats from the provider dashboard API.
     * Returns a map of metric name → value suitable for the frontend dashboard.
     */
    Map<String, Object> fetchProviderStats(PaymentGateway gateway);
}
