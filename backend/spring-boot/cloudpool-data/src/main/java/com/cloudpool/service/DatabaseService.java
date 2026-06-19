package com.cloudpool.service;

import com.cloudpool.model.DevTable;
import com.cloudpool.model.DevTableField;
import com.cloudpool.repository.DevTableFieldRepository;
import com.cloudpool.repository.DevTableRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseService {

    private final DevTableRepository devTableRepository;
    private final DevTableFieldRepository devTableFieldRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");
    private static final Set<String> ALLOWED_TYPES = Set.of("VARCHAR", "INTEGER", "BOOLEAN", "DOUBLE", "TEXT");

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
        String cleanName = cleanIdentifier(name);
        String userIdStr = cleanIdentifier(userId.toString().replace("-", "_"));
        String physicalName = "dev_tbl_" + userIdStr + "_" + cleanName;

        if (devTableRepository.findByProjectIdAndName(projectId, physicalName).isPresent()) {
            throw new IllegalArgumentException("Table with name '" + name + "' already exists in this project.");
        }

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

        // We no longer execute physical DDL statements. We use the unified tenant_data JSONB table.
        log.info("Creating virtual table metadata: {}", physicalName);

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
        getTable(tableId, userId);
        return devTableFieldRepository.findByTableId(tableId);
    }

    @Transactional
    public void deleteTable(UUID tableId, UUID userId) {
        DevTable devTable = getTable(tableId, userId);

        log.info("Deleting tenant_data for table: {}", devTable.getId());
        try {
            jdbcTemplate.update("DELETE FROM tenant_data WHERE table_id = ?", devTable.getId().toString());
        } catch (Exception e) {
            log.error("Failed to delete tenant records: {}", e.getMessage(), e);
            throw new com.cloudpool.exception.CloudPoolException("Database error: Could not drop records. " + e.getMessage());
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

        for (DevTableField field : fields) {
            String colName = field.getFieldName();
            Object value = data.get(colName);

            if (value == null || String.valueOf(value).trim().isEmpty()) {
                if (field.isRequired()) {
                    throw new IllegalArgumentException("Field '" + colName + "' is required.");
                }
                recordData.put(colName, null);
                continue;
            }

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

            recordData.put(colName, typedValue);
        }

        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(recordData);
        } catch (JsonProcessingException e) {
            throw new com.cloudpool.exception.CloudPoolException("Failed to serialize record data", e);
        }

        String insertSql = "INSERT INTO tenant_data (id, project_id, table_id, user_id, data) VALUES (?, ?, ?, ?, ?::jsonb)";
        
        try {
            // For H2 testing fallback (H2 supports FORMAT JSON, but standard string works as JSON)
            // If postgres, ?::jsonb works. For cross-compatibility in tests, let's just insert as string if H2 or use cast.
            jdbcTemplate.update(insertSql, recordId, devTable.getProjectId().toString(), tableId.toString(), userId.toString(), jsonData);
        } catch (org.springframework.jdbc.BadSqlGrammarException e) {
            // Fallback for H2 database in unit tests which doesn't support ::jsonb cast natively
            String h2InsertSql = "INSERT INTO tenant_data (id, project_id, table_id, user_id, data) VALUES (?, ?, ?, ?, ?)";
            jdbcTemplate.update(h2InsertSql, recordId, devTable.getProjectId().toString(), tableId.toString(), userId.toString(), jsonData);
        } catch (Exception e) {
            log.error("Failed to insert record: {}", e.getMessage(), e);
            throw new com.cloudpool.exception.CloudPoolException("Database error: Could not insert record. " + e.getMessage());
        }

        return recordData;
    }

    public List<Map<String, Object>> queryRecords(UUID tableId, UUID userId) {
        DevTable devTable = getTable(tableId, userId);
        String selectSql = "SELECT data FROM tenant_data WHERE table_id = ?";

        try {
            List<String> jsonList = jdbcTemplate.queryForList(selectSql, String.class, tableId.toString());
            List<Map<String, Object>> results = new ArrayList<>();
            for (String json : jsonList) {
                Map<String, Object> map = objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                results.add(map);
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to query records: {}", e.getMessage(), e);
            throw new com.cloudpool.exception.CloudPoolException("Database error: Could not fetch records. " + e.getMessage());
        }
    }

    @Transactional
    public void deleteRecord(UUID tableId, String recordId, UUID userId) {
        DevTable devTable = getTable(tableId, userId);
        String deleteSql = "DELETE FROM tenant_data WHERE id = ? AND table_id = ?";

        try {
            jdbcTemplate.update(deleteSql, recordId, tableId.toString());
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
