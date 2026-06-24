package com.cloudpool.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

@Configuration
@ConditionalOnBean(javax.sql.DataSource.class)
@ConditionalOnProperty(name = "cloudpool.datasource.slow-query-threshold-ms", havingValue = "true", matchIfMissing = false)
public class SlowQueryLoggingConfig {

    private static final Logger log = LoggerFactory.getLogger(SlowQueryLoggingConfig.class);

    private final DataSource dataSource;

    public SlowQueryLoggingConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void init() {
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.addDataSourceProperty("logSlowQueries", "true");
            hikari.addDataSourceProperty("slowQueryLoggingThresholdMs", "2000");
            log.info("HikariCP slow query logging enabled (threshold: 2000ms)");
        }
    }
}