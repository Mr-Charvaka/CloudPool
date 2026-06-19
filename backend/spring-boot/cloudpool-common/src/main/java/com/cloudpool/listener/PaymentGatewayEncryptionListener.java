package com.cloudpool.listener;

import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import com.cloudpool.model.PaymentGateway;
import com.cloudpool.util.EncryptionUtil;
import com.cloudpool.util.SpringContextHolder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
            byte[] enc = util.encrypt(gw.getPlainApiKey().getBytes(StandardCharsets.UTF_8));
            gw.setEncryptedApiKey(Base64.getEncoder().encodeToString(enc));
        }
        if (gw.getPlainSecretKey() != null && !gw.getPlainSecretKey().isBlank()) {
            byte[] enc = util.encrypt(gw.getPlainSecretKey().getBytes(StandardCharsets.UTF_8));
            gw.setEncryptedSecretKey(Base64.getEncoder().encodeToString(enc));
        }
        if (gw.getPlainWebhookSecret() != null && !gw.getPlainWebhookSecret().isBlank()) {
            byte[] enc = util.encrypt(gw.getPlainWebhookSecret().getBytes(StandardCharsets.UTF_8));
            gw.setEncryptedWebhookSecret(Base64.getEncoder().encodeToString(enc));
        }
        log.debug("PaymentGateway credentials encrypted for gateway '{}'", gw.getDisplayName());
    }

    @PostLoad
    public void decryptSecrets(PaymentGateway gw) {
        EncryptionUtil util = enc();
        if (util == null) return;

        try {
            if (gw.getEncryptedApiKey() != null && !gw.getEncryptedApiKey().isBlank()) {
                byte[] dec = util.decrypt(Base64.getDecoder().decode(gw.getEncryptedApiKey()));
                gw.setPlainApiKey(new String(dec, StandardCharsets.UTF_8));
            }
            if (gw.getEncryptedSecretKey() != null && !gw.getEncryptedSecretKey().isBlank()) {
                byte[] dec = util.decrypt(Base64.getDecoder().decode(gw.getEncryptedSecretKey()));
                gw.setPlainSecretKey(new String(dec, StandardCharsets.UTF_8));
            }
            if (gw.getEncryptedWebhookSecret() != null && !gw.getEncryptedWebhookSecret().isBlank()) {
                byte[] dec = util.decrypt(Base64.getDecoder().decode(gw.getEncryptedWebhookSecret()));
                gw.setPlainWebhookSecret(new String(dec, StandardCharsets.UTF_8));
            }
            log.debug("PaymentGateway credentials decrypted for gateway '{}'", gw.getDisplayName());
        } catch (Exception e) {
            log.error("Failed to decrypt PaymentGateway credentials for '{}': {}", gw.getDisplayName(), e.getMessage());
        }
    }
}
