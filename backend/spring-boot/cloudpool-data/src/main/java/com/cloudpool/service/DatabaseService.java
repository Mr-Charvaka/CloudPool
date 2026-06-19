package com.cloudpool.service;

import com.cloudpool.model.DatabaseConnection;
import com.cloudpool.model.DevTable;
import com.cloudpool.model.DevTableField;
import com.cloudpool.repository.DatabaseConnectionRepository;
import com.cloudpool.repository.DevTableFieldRepository;
import com.cloudpool.repository.DevTableRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseService {

    private final DevTableRepository devTableRepository;
    private final DevTableFieldRepository devTableFieldRepository;
    private final DatabaseConnectionRepository databaseConnectionRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");
    private static final Set<String> ALLOWED_TYPES = Set.of("VARCHAR", "INTEGER", "BOOLEAN", "DOUBLE", "TEXT");

    private JdbcTemplate getJdbcTemplateForProject(UUID projectId) {
        if (projectId == null) {
            return jdbcTemplate;
        }
        Optional<DatabaseConnection> activeConnOpt = databaseConnectionRepository.findByProjectIdAndDbType(projectId, "POSTGRESQL");
        if (activeConnOpt.isPresent() && activeConnOpt.get().isActive()) {
            DatabaseConnection conn = activeConnOpt.get();
            try {
                org.springframework.jdbc.datasource.DriverManagerDataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource();
                String host = conn.getHost();
                String dbName = conn.getDatabaseName();
                int port = conn.getPort();

                boolean isH2 = dbName.toLowerCase().contains("h2") || dbName.toLowerCase().contains("cloudpooldb");
                if (!isH2) {
                    try {
                        String password = conn.getPassword();
                        if (password == null || password.trim().isEmpty()) {
                            password = "postgres";
                        }
                        int dockerPort = com.cloudpool.util.DockerPostgresProvisioner.provisionOrStartContainer(projectId, password);
                        if (dockerPort != port) {
                            log.info("Docker container port changed from {} to {} for project {}. Updating repository.", port, dockerPort, projectId);
                            port = dockerPort;
                            conn.setPort(port);
                            databaseConnectionRepository.save(conn);
                        }
                    } catch (Exception dockerEx) {
                        log.error("Failed ensuring Docker container running for project {}: {}", projectId, dockerEx.getMessage());
                    }
                }

                if (isH2) {
                    dataSource.setDriverClassName("org.h2.Driver");
                    dataSource.setUrl("jdbc:h2:" + dbName);
                } else {
                    dataSource.setDriverClassName("org.postgresql.Driver");
                    dataSource.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + dbName);
                }
                dataSource.setUsername(conn.getUsername());
                dataSource.setPassword(conn.getPassword());
                return new JdbcTemplate(dataSource);
            } catch (Exception e) {
                log.error("Failed to construct dynamic PostgreSQL/H2 connection: {}", e.getMessage());
            }
        }
        return jdbcTemplate;
    }

    public static String cleanIdentifier(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Identifier cannot be null or empty.");
        }
        String clean = value.trim().toLowerCase();
        if (!IDENTIFIER_PATTERN.matcher(clean).matches()) {
            throw new IllegalArgumentException("Invalid identifier pattern.");
        }
        StringBuilder sb = new StringBuilder();
        for (char c : clean.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Transactional
    public DevTable createTable(UUID userId, UUID projectId, String name, String displayName, String description, List<FieldRequest> fields) {
        // Validate table name
        String cleanName = cleanIdentifier(name);

        // Generate clean physical name
        String userIdStr = cleanIdentifier(userId.toString().replace("-", "_"));
        String physicalName = "dev_tbl_" + userIdStr + "_" + cleanName;

        // Check if table metadata or physical table already exists
        if (devTableRepository.findByProjectIdAndName(projectId, physicalName).isPresent()) {
            throw new IllegalArgumentException("Table with name '" + name + "' already exists in this project.");
        }

        // Validate fields
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("At least one field schema must be provided.");
        }

        List<FieldRequest> validatedFields = new ArrayList<>();
        Set<String> fieldNames = new HashSet<>();
        for (FieldRequest field : fields) {
            if (field == null) {
                throw new IllegalArgumentException("Field schema cannot be null.");
            }
            if (field.getFieldName() == null || field.getFieldName().trim().isEmpty()) {
                throw new IllegalArgumentException("Field name cannot be null or empty.");
            }
            if (field.getFieldType() == null || field.getFieldType().trim().isEmpty()) {
                throw new IllegalArgumentException("Field type cannot be null or empty.");
            }

            String fieldName = cleanIdentifier(field.getFieldName());
            if (fieldNames.contains(fieldName)) {
                throw new IllegalArgumentException("Duplicate field name '" + fieldName + "'.");
            }
            fieldNames.add(fieldName);

            String fieldType = field.getFieldType().trim().toUpperCase();
            if (!ALLOWED_TYPES.contains(fieldType)) {
                throw new IllegalArgumentException("Unsupported field type '" + field.getFieldType() + "'. Supported types: " + ALLOWED_TYPES);
            }

            validatedFields.add(new FieldRequest(fieldName, fieldType, field.isRequired()));
        }

        // Construct DDL
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(cleanIdentifier(physicalName)).append(" (");
        ddl.append("id VARCHAR(36) PRIMARY KEY");

        for (FieldRequest field : validatedFields) {
            ddl.append(", ").append(cleanIdentifier(field.getFieldName())).append(" ");
            
            switch (field.getFieldType()) {
                case "VARCHAR":
                    ddl.append("VARCHAR(255)");
                    break;
                case "TEXT":
                    ddl.append("TEXT");
                    break;
                case "INTEGER":
                    ddl.append("INTEGER");
                    break;
                case "DOUBLE":
                    ddl.append("DOUBLE");
                    break;
                case "BOOLEAN":
                    ddl.append("BOOLEAN");
                    break;
                default:
                    throw new IllegalStateException("Unexpected field type: " + field.getFieldType());
            }

            if (field.isRequired()) {
                ddl.append(" NOT NULL");
            }
        }
        ddl.append(")");

        log.info("Executing DDL: {}", ddl);
        try {
            getJdbcTemplateForProject(projectId).execute(ddl.toString());
        } catch (Exception e) {
            log.error("Failed to create physical table: {}", e.getMessage(), e);
            throw new com.cloudpool.exception.CloudPoolException("Database error: Could not create table structure. " + e.getMessage());
        }

        // Save metadata
        DevTable devTable = DevTable.builder()
                .userId(userId)
                .projectId(projectId)
                .name(physicalName)
                .displayName(displayName != null ? displayName.trim() : name)
                .description(description)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        DevTable savedTable = devTableRepository.save(devTable);

        for (FieldRequest field : validatedFields) {
            DevTableField devTableField = DevTableField.builder()
                    .table(savedTable)
                    .fieldName(field.getFieldName())
                    .fieldType(field.getFieldType())
                    .isRequired(field.isRequired())
                    .build();
            devTableFieldRepository.save(devTableField);
        }

        return savedTable;
    }

    public List<DevTable> listTables(UUID userId, UUID projectId) {
        if (projectId != null) {
            return devTableRepository.findByProjectId(projectId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        }
        return devTableRepository.findByUserId(userId, org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    public DevTable getTable(UUID tableId, UUID userId) {
        DevTable devTable = devTableRepository.findById(tableId)
                .orElseThrow(() -> new NoSuchElementException("Table not found"));
        if (!devTable.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to requested table");
        }
        return devTable;
    }

    public List<DevTableField> getTableFields(UUID tableId, UUID userId) {
        // Ensure ownership
        getTable(tableId, userId);
        return devTableFieldRepository.findByTableId(tableId);
    }

    @Transactional
    public void deleteTable(UUID tableId, UUID userId) {
        DevTable devTable = getTable(tableId, userId);

        String physicalName = devTable.getName();
        String dropDdl = "DROP TABLE IF EXISTS " + cleanIdentifier(physicalName);

        log.info("Executing DDL: {}", dropDdl);
        try {
            getJdbcTemplateForProject(devTable.getProjectId()).execute(dropDdl);
        } catch (Exception e) {
            log.error("Failed to drop physical table: {}", e.getMessage(), e);
            throw new com.cloudpool.exception.CloudPoolException("Database error: Could not drop table structure. " + e.getMessage());
        }

        devTableFieldRepository.deleteByTableId(tableId);
        devTableRepository.delete(devTable);
    }

    @Transactional
    public Map<String, Object> insertRecord(UUID tableId, Map<String, Object> data, UUID userId) {
        DevTable devTable = getTable(tableId, userId);
        List<DevTableField> fields = devTableFieldRepository.findByTableId(tableId);

        String recordId = UUID.randomUUID().toString();
        Map<String, Object> recordData = new LinkedHashMap<>();
        recordData.put("id", recordId);

        List<String> insertColumns = new ArrayList<>();
        insertColumns.add("id");
        List<Object> insertValues = new ArrayList<>();
        insertValues.add(recordId);

        for (DevTableField field : fields) {
            String colName = field.getFieldName();
            Object value = data.get(colName);

            if (value == null || String.valueOf(value).trim().isEmpty()) {
                if (field.isRequired()) {
                    throw new IllegalArgumentException("Field '" + colName + "' is required.");
                }
                // Skip or insert null
                insertColumns.add(colName);
                insertValues.add(null);
                recordData.put(colName, null);
                continue;
            }

            // Convert and validate input type
            Object typedValue;
            try {
                switch (field.getFieldType()) {
                    case "INTEGER":
                        typedValue = Integer.parseInt(String.valueOf(value));
                        break;
                    case "DOUBLE":
                        typedValue = Double.parseDouble(String.valueOf(value));
                        break;
                    case "BOOLEAN":
                        String strVal = String.valueOf(value).trim().toLowerCase();
                        if ("true".equals(strVal) || "1".equals(strVal) || "yes".equals(strVal)) {
                            typedValue = Boolean.TRUE;
                        } else if ("false".equals(strVal) || "0".equals(strVal) || "no".equals(strVal)) {
                            typedValue = Boolean.FALSE;
                        } else {
                            throw new IllegalArgumentException("Invalid boolean format.");
                        }
                        break;
                    case "VARCHAR":
                    case "TEXT":
                    default:
                        typedValue = String.valueOf(value);
                        break;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Field '" + colName + "' must be of type " + field.getFieldType() + ".");
            }

            insertColumns.add(colName);
            insertValues.add(typedValue);
            recordData.put(colName, typedValue);
        }

        // Build dynamic INSERT SQL
        List<String> cleanColumns = insertColumns.stream().map(DatabaseService::cleanIdentifier).collect(Collectors.toList());
        String columnsSql = String.join(", ", cleanColumns);
        String placeholdersSql = insertColumns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String insertSql = "INSERT INTO " + cleanIdentifier(devTable.getName()) + " (" + columnsSql + ") VALUES (" + placeholdersSql + ")";

        log.info("Executing DML: {} with values {}", insertSql, insertValues);
        try {
            getJdbcTemplateForProject(devTable.getProjectId()).update(insertSql, insertValues.toArray());
        } catch (Exception e) {
            log.error("Failed to insert record: {}", e.getMessage(), e);
            throw new com.cloudpool.exception.CloudPoolException("Database error: Could not insert record. " + e.getMessage());
        }

        return recordData;
    }

    public List<Map<String, Object>> queryRecords(UUID tableId, UUID userId) {
        DevTable devTable = getTable(tableId, userId);
        String selectSql = "SELECT * FROM " + cleanIdentifier(devTable.getName());

        log.info("Executing DML: {}", selectSql);
        try {
            return getJdbcTemplateForProject(devTable.getProjectId()).queryForList(selectSql);
        } catch (Exception e) {
            log.error("Failed to query records: {}", e.getMessage(), e);
            throw new com.cloudpool.exception.CloudPoolException("Database error: Could not fetch records. " + e.getMessage());
        }
    }

    @Transactional
    public void deleteRecord(UUID tableId, String recordId, UUID userId) {
        DevTable devTable = getTable(tableId, userId);
        String deleteSql = "DELETE FROM " + cleanIdentifier(devTable.getName()) + " WHERE id = ?";

        log.info("Executing DML: {} with id={}", deleteSql, recordId);
        try {
            getJdbcTemplateForProject(devTable.getProjectId()).update(deleteSql, recordId);
        } catch (Exception e) {
            log.error("Failed to delete record: {}", e.getMessage(), e);
            throw new com.cloudpool.exception.CloudPoolException("Database error: Could not delete record. " + e.getMessage());
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldRequest {
        private String fieldName;
        private String fieldType;
        private boolean isRequired;
    }
}

