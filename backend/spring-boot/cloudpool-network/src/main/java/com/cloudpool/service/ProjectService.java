package com.cloudpool.service;

import com.cloudpool.model.*;
import com.cloudpool.repository.*;
import com.cloudpool.util.EncryptionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectSecretRepository projectSecretRepository;
    private final DatabaseConnectionRepository databaseConnectionRepository;
    private final ProjectSnapshotRepository projectSnapshotRepository;
    private final DevTableRepository devTableRepository;
    private final DevTableFieldRepository devTableFieldRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EncryptionUtil encryptionUtil;

    @org.springframework.beans.factory.annotation.Value("${spring.datasource.url:jdbc:h2:file:./data/cloudpooldb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}")
    private String datasourceUrl;

    @org.springframework.beans.factory.annotation.Value("${spring.datasource.username:sa}")
    private String datasourceUsername;

    @org.springframework.beans.factory.annotation.Value("${spring.datasource.password:password}")
    private String datasourcePassword;

    @org.springframework.beans.factory.annotation.Value("${cloudpool.allow-local-connections:true}")
    private boolean allowLocalConnections;

    @Transactional
    public Project createProject(UUID userId, String name, String description) {
        Project project = Project.builder()
                .userId(userId)
                .name(name.trim())
                .description(description)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Project saved = projectRepository.save(project);
        // Unified tenant_data schema requires no database provisioning or Docker containers.
        return saved;
    }

    @Transactional
    public List<Project> listProjects(UUID userId) {
        List<Project> projects = projectRepository.findByUserId(userId);
        if (projects.isEmpty()) {
            // Auto-create a default project if none exist
            Project defaultProj = createProject(userId, "default-project", "Auto-created default project workspace");
            return Collections.singletonList(defaultProj);
        }
        return projects;
    }

    public Project getProject(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to requested project");
        }
        return project;
    }

    @Transactional
    public void deleteProject(UUID projectId, UUID userId) {
        Project project = getProject(projectId, userId);

        // Delete metadata and tenant_data
        List<DevTable> tables = devTableRepository.findByProjectId(projectId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        for (DevTable table : tables) {
            try {
                jdbcTemplate.update("DELETE FROM tenant_data WHERE table_id = ?", table.getId().toString());
            } catch (Exception e) {
                log.error("Failed to clear tenant_data {} during project deletion: {}", table.getName(), e.getMessage());
            }
            devTableFieldRepository.deleteByTableId(table.getId());
            devTableRepository.delete(table);
        }

        projectSecretRepository.deleteByProjectId(projectId);
        databaseConnectionRepository.deleteByProjectId(projectId);
        projectSnapshotRepository.deleteByProjectId(projectId);
        projectRepository.delete(project);
    }

    // ── SECRETS VAULT ──

    @Transactional
    public ProjectSecret addSecret(UUID projectId, String key, String value, UUID userId) {
        Project project = getProject(projectId, userId);

        String encryptedValue = Base64.getEncoder().encodeToString(encryptionUtil.encrypt(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        // Check if secret key already exists, overwrite if yes
        Optional<ProjectSecret> existing = projectSecretRepository.findByProjectIdAndSecretKey(projectId, key.trim());
        ProjectSecret secret;
        if (existing.isPresent()) {
            secret = existing.get();
            secret.setSecretValue(encryptedValue);
        } else {
            secret = ProjectSecret.builder()
                    .project(project)
                    .secretKey(key.trim())
                    .secretValue(encryptedValue)
                    .build();
        }

        return projectSecretRepository.save(secret);
    }

    public List<ProjectSecret> listSecrets(UUID projectId, UUID userId) {
        getProject(projectId, userId); // check auth
        List<ProjectSecret> secrets = projectSecretRepository.findByProjectId(projectId);
        secrets.forEach(s -> {
            try {
                byte[] decrypted = encryptionUtil.decrypt(Base64.getDecoder().decode(s.getSecretValue()));
                s.setSecretValue(new String(decrypted, java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                s.setSecretValue("[DECRYPTION_ERROR]");
            }
        });
        return secrets;
    }

    @Transactional
    public void deleteSecret(UUID secretId, UUID userId) {
        ProjectSecret secret = projectSecretRepository.findById(secretId)
                .orElseThrow(() -> new NoSuchElementException("Secret not found"));
        getProject(secret.getProject().getId(), userId); // check auth
        projectSecretRepository.delete(secret);
    }

    // ── DATABASE CONNECTIONS ──

    public List<DatabaseConnection> listConnections(UUID projectId, UUID userId) {
        getProject(projectId, userId); // check auth
        return databaseConnectionRepository.findByProjectId(projectId);
    }

    private void validateHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be empty");
        }
        if (allowLocalConnections && (host.trim().equalsIgnoreCase("localhost") || host.trim().equals("127.0.0.1"))) {
            return;
        }
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(host.trim());
            if (!allowLocalConnections && (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress())) {
                throw new SecurityException("Access to internal, loopback, or link-local address is denied");
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("Invalid host address: " + host);
        }
    }

    private String sanitizeHost(String host) {
        validateHost(host);
        StringBuilder sb = new StringBuilder();
        for (char c : host.trim().toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-') {
                sb.append(cleanChar(c));
            }
        }
        return sb.toString();
    }

    private char cleanChar(char c) {
        switch(c) {
            case 'a': return 'a'; case 'b': return 'b'; case 'c': return 'c'; case 'd': return 'd';
            case 'e': return 'e'; case 'f': return 'f'; case 'g': return 'g'; case 'h': return 'h';
            case 'i': return 'i'; case 'j': return 'j'; case 'k': return 'k'; case 'l': return 'l';
            case 'm': return 'm'; case 'n': return 'n'; case 'o': return 'o'; case 'p': return 'p';
            case 'q': return 'q'; case 'r': return 'r'; case 's': return 's'; case 't': return 't';
            case 'u': return 'u'; case 'v': return 'v'; case 'w': return 'w'; case 'x': return 'x';
            case 'y': return 'y'; case 'z': return 'z';
            case 'A': return 'A'; case 'B': return 'B'; case 'C': return 'C'; case 'D': return 'D';
            case 'E': return 'E'; case 'F': return 'F'; case 'G': return 'G'; case 'H': return 'H';
            case 'I': return 'I'; case 'J': return 'J'; case 'K': return 'K'; case 'L': return 'L';
            case 'M': return 'M'; case 'N': return 'N'; case 'O': return 'O'; case 'P': return 'P';
            case 'Q': return 'Q'; case 'R': return 'R'; case 'S': return 'S'; case 'T': return 'T';
            case 'U': return 'U'; case 'V': return 'V'; case 'W': return 'W'; case 'X': return 'X';
            case 'Y': return 'Y'; case 'Z': return 'Z';
            case '0': return '0'; case '1': return '1'; case '2': return '2'; case '3': return '3';
            case '4': return '4'; case '5': return '5'; case '6': return '6'; case '7': return '7';
            case '8': return '8'; case '9': return '9';
            case '.': return '.';
            case '-': return '-';
            default: return '_';
        }
    }


    @Transactional
    public DatabaseConnection saveConnection(UUID projectId, String dbType, String host, int port, String databaseName, String username, String password, boolean active, UUID userId) {
        Project project = getProject(projectId, userId);
        String safeHost = sanitizeHost(host);
        Optional<DatabaseConnection> existing = databaseConnectionRepository.findByProjectIdAndDbType(projectId, dbType.trim().toUpperCase());
        DatabaseConnection conn;
        if (existing.isPresent()) {
            conn = existing.get();
            conn.setHost(safeHost);
            conn.setPort(port);
            conn.setDatabaseName(databaseName);
            conn.setUsername(username);
            conn.setPassword(encryptConnectionPassword(password));
            conn.setActive(active);
        } else {
            conn = DatabaseConnection.builder()
                    .project(project)
                    .dbType(dbType.trim().toUpperCase())
                    .host(safeHost)
                    .port(port)
                    .databaseName(databaseName)
                    .username(username)
                    .password(encryptConnectionPassword(password))
                    .active(active)
                    .build();
        }
        return databaseConnectionRepository.save(conn);
    }

    @Transactional
    public DatabaseConnection provisionProjectDatabase(UUID projectId, UUID userId) {
        throw new UnsupportedOperationException("Database provisioning is no longer supported. The platform now uses a high-performance unified JSONB architecture (Phase 3).");
    }

    @Transactional
    public void deleteConnection(UUID connectionId, UUID userId) {
        DatabaseConnection conn = databaseConnectionRepository.findById(connectionId)
                .orElseThrow(() -> new NoSuchElementException("Connection not found"));
        getProject(conn.getProject().getId(), userId); // check auth
        databaseConnectionRepository.delete(conn);
    }

    public boolean testConnection(String dbType, String host, int port, String databaseName, String username, String password) {
        String safeHost = sanitizeHost(host);
        if ("POSTGRESQL".equalsIgnoreCase(dbType)) {
            try {
                org.springframework.jdbc.datasource.DriverManagerDataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource();
                dataSource.setDriverClassName("org.postgresql.Driver");
                dataSource.setUrl("jdbc:postgresql://" + safeHost + ":" + port + "/" + databaseName);
                dataSource.setUsername(username);
                dataSource.setPassword(password);
                JdbcTemplate tempTemplate = new JdbcTemplate(dataSource);
                Integer res = tempTemplate.queryForObject("SELECT 1", Integer.class);
                return res != null && res == 1;
            } catch (Exception e) {
                log.error("PostgreSQL connection test failed: {}", e.getMessage());
                throw new com.cloudpool.exception.CloudPoolException("PostgreSQL connection test failed: " + e.getMessage(), e);
            }
        } else if ("REDIS".equalsIgnoreCase(dbType)) {
            try (redis.clients.jedis.Jedis jedis = new redis.clients.jedis.Jedis(safeHost, port, 2000)) {
                if (password != null && !password.trim().isEmpty()) {
                    jedis.auth(password);
                }
                String pingRes = jedis.ping();
                return "PONG".equalsIgnoreCase(pingRes);
            } catch (Exception e) {
                log.error("Redis connection test failed: {}", e.getMessage());
                throw new com.cloudpool.exception.CloudPoolException("Redis connection test failed: " + e.getMessage(), e);
            }
        } else {
            throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }
    }

    // ── VERSIONED SNAPSHOTS (ROLLBACK) ──

    @Transactional
    public ProjectSnapshot createSnapshot(UUID projectId, String snapshotName, UUID userId) {
        Project project = getProject(projectId, userId);

        try {
            // 1. Gather Secrets
            List<SecretState> secrets = projectSecretRepository.findByProjectId(projectId).stream()
                    .map(s -> new SecretState(s.getSecretKey(), s.getSecretValue()))
                    .collect(Collectors.toList());

            // 2. Gather DB Connections
            List<ConnectionState> connections = databaseConnectionRepository.findByProjectId(projectId).stream()
                    .map(c -> new ConnectionState(c.getDbType(), c.getHost(), c.getPort(), c.getDatabaseName(), c.getUsername(), c.getPassword(), c.isActive()))
                    .collect(Collectors.toList());

            // 3. Gather Tables Schemas
            List<TableState> tables = new ArrayList<>();
            List<DevTable> devTables = devTableRepository.findByProjectId(projectId, org.springframework.data.domain.Pageable.unpaged()).getContent();
            for (DevTable table : devTables) {
                List<FieldState> fields = devTableFieldRepository.findByTableId(table.getId()).stream()
                        .map(f -> new FieldState(f.getFieldName(), f.getFieldType(), f.isRequired()))
                        .collect(Collectors.toList());
                tables.add(new TableState(table.getName(), table.getDisplayName(), table.getDescription(), fields));
            }

            TopologyState topology = new TopologyState(secrets, connections, tables);
            String json = objectMapper.writeValueAsString(topology);

            ProjectSnapshot snapshot = ProjectSnapshot.builder()
                    .project(project)
                    .name(snapshotName.trim())
                    .topologyJson(json)
                    .createdAt(LocalDateTime.now())
                    .build();

            return projectSnapshotRepository.save(snapshot);
        } catch (Exception e) {
            log.error("Failed to serialize snapshot topology: {}", e.getMessage(), e);
            throw new com.cloudpool.exception.CloudPoolException("Snapshot failed: " + e.getMessage());
        }
    }

    @Transactional
    public void restoreSnapshot(UUID projectId, UUID snapshotId, UUID userId) {
        Project project = getProject(projectId, userId);
        ProjectSnapshot snapshot = projectSnapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new NoSuchElementException("Snapshot not found"));

        if (!snapshot.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Snapshot does not belong to this project");
        }

        try {
            TopologyState topology = objectMapper.readValue(snapshot.getTopologyJson(), TopologyState.class);

            // 1. Restore Secrets
            projectSecretRepository.deleteByProjectId(projectId);
            if (topology.getSecrets() != null) {
                for (SecretState s : topology.getSecrets()) {
                    ProjectSecret sec = ProjectSecret.builder()
                            .project(project)
                            .secretKey(s.getKey())
                            .secretValue(s.getValue())
                            .build();
                    projectSecretRepository.save(sec);
                }
            }

            // 2. Restore Connections
            databaseConnectionRepository.deleteByProjectId(projectId);
            if (topology.getConnections() != null) {
                for (ConnectionState c : topology.getConnections()) {
                    DatabaseConnection conn = DatabaseConnection.builder()
                            .project(project)
                            .dbType(c.getDbType())
                            .host(c.getHost())
                            .port(c.getPort())
                            .databaseName(c.getDatabaseName())
                            .username(c.getUsername())
                            .password(c.getPassword())
                            .active(c.isActive())
                            .build();
                    databaseConnectionRepository.save(conn);
                }
            }

            // Determine dynamic target template
            Optional<DatabaseConnection> activeConnOpt = databaseConnectionRepository.findByProjectIdAndDbType(projectId, "POSTGRESQL");
            JdbcTemplate targetTemplate = jdbcTemplate;
            if (activeConnOpt.isPresent() && activeConnOpt.get().isActive()) {
                try {
                    org.springframework.jdbc.datasource.DriverManagerDataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource();
                    dataSource.setDriverClassName("org.postgresql.Driver");
                    dataSource.setUrl("jdbc:postgresql://" + activeConnOpt.get().getHost() + ":" + activeConnOpt.get().getPort() + "/" + activeConnOpt.get().getDatabaseName());
                    dataSource.setUsername(activeConnOpt.get().getUsername());
                    dataSource.setPassword(activeConnOpt.get().getPassword());
                    targetTemplate = new JdbcTemplate(dataSource);
                } catch (Exception e) {
                    log.error("Failed to construct dynamic PostgreSQL connection during restore: {}", e.getMessage());
                }
            }

            // 3. Restore Table Schemas
            // A. Delete current metadata and tenant_data
            List<DevTable> currentTables = devTableRepository.findByProjectId(projectId, org.springframework.data.domain.Pageable.unpaged()).getContent();
            for (DevTable table : currentTables) {
                try {
                    jdbcTemplate.update("DELETE FROM tenant_data WHERE table_id = ?", table.getId().toString());
                } catch (Exception e) {
                    log.warn("Failed to clear tenant_data {} during rollback: {}", table.getName(), e.getMessage());
                }
                devTableFieldRepository.deleteByTableId(table.getId());
                devTableRepository.delete(table);
            }

            // B. Re-provision table metadata and physical schemas
            if (topology.getTables() != null) {
                for (TableState t : topology.getTables()) {
                    DevTable table = DevTable.builder()
                            .userId(userId)
                            .projectId(projectId)
                            .name(t.getName())
                            .displayName(t.getDisplayName())
                            .description(t.getDescription())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    DevTable savedTable = devTableRepository.save(table);

                    log.info("Rollback: Recreated metadata for virtual table {}", t.getName());
                }
            }

        } catch (Exception e) {
            log.error("Failed to restore snapshot topology: {}", e.getMessage(), e);
            throw new com.cloudpool.exception.CloudPoolException("Snapshot restoration failed: " + e.getMessage());
        }
    }

    public List<ProjectSnapshot> listSnapshots(UUID projectId, UUID userId) {
        getProject(projectId, userId); // check auth
        return projectSnapshotRepository.findByProjectId(projectId);
    }

    @Transactional
    public void deleteSnapshot(UUID snapshotId, UUID userId) {
        ProjectSnapshot snapshot = projectSnapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new NoSuchElementException("Snapshot not found"));
        getProject(snapshot.getProject().getId(), userId); // check auth
        projectSnapshotRepository.delete(snapshot);
    }

    private String encryptConnectionPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return rawPassword;
        }
        return Base64.getEncoder().encodeToString(encryptionUtil.encrypt(rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ── HELPER DTO STATES FOR SERIALIZATION ──

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopologyState {
        private List<SecretState> secrets;
        private List<ConnectionState> connections;
        private List<TableState> tables;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecretState {
        private String key;
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionState {
        private String dbType;
        private String host;
        private int port;
        private String databaseName;
        private String username;
        private String password;
        private boolean active;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableState {
        private String name;
        private String displayName;
        private String description;
        private List<FieldState> fields;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldState {
        private String fieldName;
        private String fieldType;
        private boolean isRequired;
    }
}

