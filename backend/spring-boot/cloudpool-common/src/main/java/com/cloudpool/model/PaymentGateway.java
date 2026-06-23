package com.cloudpool.model;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.cloudpool.listener.PaymentGatewayEncryptionListener;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_gateways")
@Data
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@EntityListeners(PaymentGatewayEncryptionListener.class)
public class PaymentGateway {

    public enum Provider { STRIPE, RAZORPAY, CUSTOM }
    public enum Mode     { LIVE, TEST }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode = Mode.TEST;

    // ── encrypted columns (stored to DB) ──────────────────────
    @Column(name = "encrypted_api_key")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String encryptedApiKey;

    @Column(name = "encrypted_secret_key")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String encryptedSecretKey;

    @Column(name = "encrypted_webhook_secret")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String encryptedWebhookSecret;

    // ── transient plaintext (not stored, used by adapters) ─────
    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private transient String plainApiKey;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private transient String plainSecretKey;

    @Transient
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private transient String plainWebhookSecret;

    // ── optional field for CUSTOM provider ────────────────────
    private String customBaseUrl;

    @Column(nullable = false)
    private boolean isActive = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Builder
    public PaymentGateway(UUID id, UUID userId, String displayName, Provider provider, Mode mode,
                          String apiKey, String secretKey, String webhookSecret,
                          String customBaseUrl, Boolean isActive) {
        this.id = id;
        this.userId = userId;
        this.displayName = displayName;
        this.provider = provider;
        this.mode = mode != null ? mode : Mode.TEST;
        this.plainApiKey = apiKey;
        this.encryptedApiKey = apiKey;
        this.plainSecretKey = secretKey;
        this.encryptedSecretKey = secretKey;
        this.plainWebhookSecret = webhookSecret;
        this.encryptedWebhookSecret = webhookSecret;
        this.customBaseUrl = customBaseUrl;
        this.isActive = isActive != null ? isActive : true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Convenience: returns the decrypted (plaintext) key if available, else the encrypted DB value
    public String getSecretKey() {
        return plainSecretKey != null ? plainSecretKey : encryptedSecretKey;
    }

    public String getApiKey() {
        return plainApiKey != null ? plainApiKey : encryptedApiKey;
    }

    // Safe masked representation for API responses
    public String getMaskedApiKey() {
        String key = plainApiKey != null ? plainApiKey : encryptedApiKey;
        if (key == null || key.length() < 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
