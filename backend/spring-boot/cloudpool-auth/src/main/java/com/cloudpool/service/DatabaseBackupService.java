package com.cloudpool.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Creates snapshots of the local H2 database using H2's native {@code BACKUP TO} command.
 */
@Service
public class DatabaseBackupService {

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final DataSource dataSource;
    private final Path backupDirectory;

    public DatabaseBackupService(
            DataSource dataSource,
            @Value("${cloudpool.backup.directory:../data/backups}") String backupDirectory) {
        this.dataSource = dataSource;
        this.backupDirectory = Paths.get(backupDirectory).toAbsolutePath().normalize();
    }

    /**
     * Writes a zip snapshot of the database to the configured backup directory.
     *
     * @return the path of the created backup file
     * @throws IllegalStateException if the datasource is not an H2 database
     */
    public Path createBackup() {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (!"H2".equalsIgnoreCase(product)) {
                throw new IllegalStateException("Manual backup is only supported for H2, found: " + product);
            }

            Files.createDirectories(backupDirectory);
            // The file name is generated server-side, so no user input reaches the SQL string.
            Path target = backupDirectory.resolve("cloudpooldb-" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".zip");
            String sqlPath = target.toString().replace("\\", "/").replace("'", "''");

            try (Statement statement = connection.createStatement()) {
                statement.execute("BACKUP TO '" + sqlPath + "'");
            }
            return target;
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Database backup failed", e);
        }
    }
}
