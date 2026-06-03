package com.cloudpool.listener;

import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import com.cloudpool.model.PaymentGateway;
import com.cloudpool.util.EncryptionUtil;
import com.cloudpool.util.SpringContextHolder;

@Slf4j
public class PaymentGatewayEncryptionListener {

    private EncryptionUtil enc() {
        return SpringContextHolder.getBean(EncryptionUtil.class);
    }

    @PrePersist
    @PreUpdate
    public void encryptSecrets(PaymentGateway gw) {
        EncryptionUtil util = enc();
        if (util == null) return;

        if (gw.getPlainApiKey() != null && !gw.getPlainApiKey().isBlank()) {
            gw.setEncryptedApiKey(util.encrypt(gw.getPlainApiKey()));
        }
        if (gw.getPlainSecretKey() != null && !gw.getPlainSecretKey().isBlank()) {
            gw.setEncryptedSecretKey(util.encrypt(gw.getPlainSecretKey()));
        }
        if (gw.getPlainWebhookSecret() != null && !gw.getPlainWebhookSecret().isBlank()) {
            gw.setEncryptedWebhookSecret(util.encrypt(gw.getPlainWebhookSecret()));
        }
        log.debug("PaymentGateway credentials encrypted for gateway '{}'", gw.getDisplayName());
    }

    @PostLoad
    public void decryptSecrets(PaymentGateway gw) {
        EncryptionUtil util = enc();
        if (util == null) return;

        try {
            if (gw.getEncryptedApiKey() != null && !gw.getEncryptedApiKey().isBlank()) {
                gw.setPlainApiKey(util.decrypt(gw.getEncryptedApiKey()));
            }
            if (gw.getEncryptedSecretKey() != null && !gw.getEncryptedSecretKey().isBlank()) {
                gw.setPlainSecretKey(util.decrypt(gw.getEncryptedSecretKey()));
            }
            if (gw.getEncryptedWebhookSecret() != null && !gw.getEncryptedWebhookSecret().isBlank()) {
                gw.setPlainWebhookSecret(util.decrypt(gw.getEncryptedWebhookSecret()));
            }
            log.debug("PaymentGateway credentials decrypted for gateway '{}'", gw.getDisplayName());
        } catch (Exception e) {
            log.error("Failed to decrypt PaymentGateway credentials for '{}': {}", gw.getDisplayName(), e.getMessage());
        }
    }
}
