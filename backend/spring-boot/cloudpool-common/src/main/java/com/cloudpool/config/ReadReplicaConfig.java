package com.cloudpool.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "cloudpool.datasource.read-replica-url")
public class ReadReplicaConfig {

    private static final Logger log = LoggerFactory.getLogger(ReadReplicaConfig.class);

    @Value("${spring.datasource.url}")
    private String primaryUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${cloudpool.datasource.read-replica-url}")
    private String readReplicaUrl;

    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("primaryDataSource") DataSource primary,
            @Qualifier("readReplicaDataSource") DataSource replica) {
        Map<Object, Object> targets = new HashMap<>();
        targets.put("primary", primary);
        targets.put("replica", replica);

        AbstractRoutingDataSource routing = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return ReadReplicaContext.isReadOnly() ? "replica" : "primary";
            }
        };
        routing.setDefaultTargetDataSource(primary);
        routing.setTargetDataSources(targets);
        log.info("Read replica routing configured: primary={}, replica={}", primaryUrl, readReplicaUrl);
        return routing;
    }

    @Bean
    public DataSource primaryDataSource() {
        return createDataSource(primaryUrl);
    }

    @Bean
    public DataSource readReplicaDataSource() {
        return createDataSource(readReplicaUrl);
    }

    private DataSource createDataSource(String jdbcUrl) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1200000);
        config.addDataSourceProperty("sslmode", "require");
        return new HikariDataSource(config);
    }
}