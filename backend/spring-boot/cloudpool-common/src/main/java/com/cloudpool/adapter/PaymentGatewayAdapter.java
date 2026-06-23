package com.cloudpool.adapter;

import com.cloudpool.model.enums.PaymentProvider;
import com.cloudpool.model.PaymentTransaction;

public interface PaymentGatewayAdapter {
    PaymentTransaction createPayment(PaymentTransaction transaction);
    PaymentTransaction processCallback(PaymentProvider provider, String payload);
}
