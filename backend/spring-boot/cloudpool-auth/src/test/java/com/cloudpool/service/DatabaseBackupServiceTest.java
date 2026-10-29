package com.cloudpool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatabaseBackupServiceTest {

    @TempDir
    Path backupDir;

    @TempDir
    Path dbDir;

    // BACKUP TO requires a persistent database, so an in-memory H2 cannot be used here.
    private DataSource h2DataSource() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:file:" + dbDir.resolve("testdb-" + UUID.randomUUID()).toString().replace("\\", "/"),
                "sa", "");
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE sample (id INT PRIMARY KEY)");
            s.execute("INSERT INTO sample VALUES (1)");
        }
        return ds;
    }

    @Test
    void backupCreatesNonEmptyZipInConfiguredDirectory() throws Exception {
        DatabaseBackupService service = new DatabaseBackupService(h2DataSource(), backupDir.toString());

        Path backup = service.createBackup();

        assertTrue(Files.exists(backup));
        assertTrue(Files.size(backup) > 0);
        assertEquals(backupDir.toAbsolutePath().normalize(), backup.getParent());
        assertTrue(backup.getFileName().toString().matches("cloudpooldb-\\d{8}-\\d{6}-\\d{3}\\.zip"));
    }

    @Test
    void backupCreatesDirectoryWhenMissing() throws Exception {
        Path nested = backupDir.resolve("nested").resolve("backups");
        DatabaseBackupService service = new DatabaseBackupService(h2DataSource(), nested.toString());

        Path backup = service.createBackup();

        assertTrue(Files.exists(backup));
    }

    @Test
    void backupIsRejectedForNonH2Databases() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(ds.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        DatabaseBackupService service = new DatabaseBackupService(ds, backupDir.toString());

        assertThrows(IllegalStateException.class, service::createBackup);
    }
}
