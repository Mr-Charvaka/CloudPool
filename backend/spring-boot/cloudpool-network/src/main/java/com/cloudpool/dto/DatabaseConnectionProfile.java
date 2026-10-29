package com.cloudpool.dto;

import com.cloudpool.model.DatabaseConnection;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only view of a {@link DatabaseConnection} that is safe to keep in Redis: it never carries
 * the password, encrypted or not, and it holds no reference to the JPA entity graph.
 */
public record DatabaseConnectionProfile(
        UUID id,
        UUID projectId,
        String dbType,
        String host,
        int port,
        String databaseName,
        String username,
        boolean active,
        LocalDateTime createdAt) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static DatabaseConnectionProfile from(DatabaseConnection connection) {
        return new DatabaseConnectionProfile(
                connection.getId(),
                connection.getProject() != null ? connection.getProject().getId() : null,
                connection.getDbType(),
                connection.getHost(),
                connection.getPort(),
                connection.getDatabaseName(),
                connection.getUsername(),
                connection.isActive(),
                connection.getCreatedAt());
    }
}
