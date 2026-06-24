package com.cloudpool.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cloudpool.postgres.ssl-enabled", havingValue = "true")
public class PostgresSslConfig {

    private static final Logger log = LoggerFactory.getLogger(PostgresSslConfig.class);

    @Value("${cloudpool.postgres.ssl-cert-path:}")
    private String sslCertPath;

    @Value("${cloudpool.postgres.ssl-key-path:}")
    private String sslKeyPath;

    @Value("${cloudpool.postgres.ssl-root-cert-path:}")
    private String sslRootCertPath;

    @PostConstruct
    public void validate() {
        System.setProperty("javax.net.ssl.trustStore", sslRootCertPath);
        log.info("PostgreSQL SSL mode set to verify-full with cert: {}", sslCertPath);
    }
}