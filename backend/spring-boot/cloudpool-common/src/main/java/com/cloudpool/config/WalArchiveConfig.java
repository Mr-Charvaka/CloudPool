package com.cloudpool.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class WalArchiveConfig {

    private static final Logger log = LoggerFactory.getLogger(WalArchiveConfig.class);

    @Value("${cloudpool.postgres.wal-archive-enabled:false}")
    private boolean walArchiveEnabled;

    @Value("${cloudpool.postgres.wal-archive-command:}")
    private String walArchiveCommand;

    @PostConstruct
    public void init() {
        if (!walArchiveEnabled) return;

        String config = String.format("""
            wal_level = replica
            archive_mode = on
            archive_command = '%s'
            archive_timeout = 60
            """, walArchiveCommand.isEmpty()
                ? "aws s3 cp %p s3://cloudpool-backups/wal/%f"
                : walArchiveCommand);

        try {
            Path configPath = Path.of("/var/lib/postgresql/data/postgresql.auto.conf");
            Files.writeString(configPath, config, StandardOpenOption.APPEND);
            log.info("WAL archiving configured at {}", configPath);
        } catch (IOException e) {
            log.warn("Could not write postgresql.auto.conf for WAL archiving: {}", e.getMessage());
        }
    }
}