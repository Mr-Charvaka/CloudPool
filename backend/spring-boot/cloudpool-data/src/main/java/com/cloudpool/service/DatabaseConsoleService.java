package com.cloudpool.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseConsoleService {

    private static final Pattern SQL_COMMENT_PATTERN = Pattern.compile("(--|#|/\\*).*", Pattern.DOTALL);
    private static final Pattern MULTI_STMT_PATTERN = Pattern.compile(".*;\\s*(select|insert|update|delete|drop|create|alter|truncate)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SELECT_ONLY_PATTERN = Pattern.compile("^\\s*select\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final JdbcTemplate jdbcTemplate;
    private final MetricsService metricsService;

    private JdbcTemplate getJdbcTemplateForConnection(com.cloudpool.model.DatabaseConnection conn) {
        if (conn == null) {
            return jdbcTemplate;
        }
        try {
            org.springframework.jdbc.datasource.DriverManagerDataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource();
            if ("POSTGRESQL".equalsIgnoreCase(conn.getDbType())) {
                String dbName = conn.getDatabaseName();
                if (dbName != null && !dbName.matches("^[a-zA-Z0-9_\\-]+$")) {
                    throw new IllegalArgumentException("Invalid database name. Only alphanumeric characters, underscores, and hyphens are allowed.");
                }
                dataSource.setDriverClassName("org.postgresql.Driver");
                dataSource.setUrl("jdbc:postgresql://" + conn.getHost() + ":" + conn.getPort() + "/" + dbName);
            } else {
                return jdbcTemplate;
            }
            dataSource.setUsername(conn.getUsername());
            dataSource.setPassword(conn.getPassword());
            return new JdbcTemplate(dataSource);
        } catch (Exception e) {
            throw new com.cloudpool.exception.CloudPoolException("Failed to construct dynamic PostgreSQL connection: " + e.getMessage(), e);
        }
    }

    private void validateH2Query(String sql, com.cloudpool.model.User user) {
        if (user == null) {
            throw new SecurityException("Unauthorized SQL query execution");
        }
        if (com.cloudpool.model.enums.Role.ADMIN == user.getRole()) {
            return; // Admin can execute anything on local H2
        }

        String trimmed = sql.trim();
        String upperSql = trimmed.toUpperCase();

        // Whitelist: only SELECT queries are allowed for non-admin users
        if (!SELECT_ONLY_PATTERN.matcher(trimmed).matches()) {
            throw new SecurityException("Access denied: Only SELECT queries are allowed.");
        }

        // Reject multi-statement injection by strictly forbidding semicolons inside the query
        int semiIndex = trimmed.indexOf(';');
        if (semiIndex >= 0 && semiIndex != trimmed.length() - 1) {
            throw new SecurityException("Access denied: Semicolons are not allowed inside queries (multi-statement protection).");
        }

        // Reject SQL comments (--, /* */, #) used for comment-based injection
        if (SQL_COMMENT_PATTERN.matcher(trimmed).find()) {
            throw new SecurityException("Access denied: SQL comments are not allowed.");
        }

        // Reject dangerous H2 keywords and functions to prevent RCE, SSRF, and LFI
        String[] dangerousKeywords = {
            "RUNSCRIPT", "ALIAS", "CALL", "CSVREAD", "CSVWRITE", "FILE_READ", 
            "FILE_WRITE", "LINK_SCHEMA", "MEMORY_FREE", "MEMORY_USED", 
            "JAVA_OBJECT_SERIALIZER", "SET", "SYSTEM_USER"
        };
        for (String keyword : dangerousKeywords) {
            if (Pattern.compile("\\b" + keyword + "\\b", Pattern.CASE_INSENSITIVE).matcher(trimmed).find()) {
                throw new SecurityException("Access denied: Dangerous keyword or function '" + keyword + "' is not allowed.");
            }
        }

        String cleanSql = trimmed.toLowerCase();

        // List of prohibited system/metadata table names
        List<String> prohibitedTables = Arrays.asList(
            "users", "database_connections", "buckets", "file_metadata",
            "background_jobs", "api_keys", "flyway_schema_history",
            "dev_tables", "dev_table_fields", "vector_collections",
            "vector_documents", "api_key_usage_logs", "information_schema",
            "pg_"
        );

        for (String table : prohibitedTables) {
            String regex = "\\b" + Pattern.quote(table) + "\\b";
            if (Pattern.compile(regex).matcher(cleanSql).find()) {
                throw new SecurityException("Access denied: Accessing system metadata table '" + table + "' is prohibited.");
            }
        }

        // Verify that any table referenced with "dev_tbl_" strictly belongs to this user
        String userIdStr = user.getId().toString().replace("-", "_");
        String expectedPrefix = "dev_tbl_" + userIdStr + "_";

        Pattern devTblPattern = Pattern.compile("\\bdev_tbl_[a-zA-Z0-9_]*\\b");
        var matcher = devTblPattern.matcher(cleanSql);
        boolean foundAny = false;
        while (matcher.find()) {
            foundAny = true;
            String tableName = matcher.group();
            if (!tableName.startsWith(expectedPrefix)) {
                throw new SecurityException("Access denied: You are not allowed to access table '" + tableName + "'.");
            }
        }

        if (!foundAny && !cleanSql.matches("(?i)^select\\s+\\d+(\\s+as\\s+\\w+)?\\s*$") && !cleanSql.startsWith("show") && !cleanSql.startsWith("pragma") && !cleanSql.startsWith("explain")) {
            throw new SecurityException("Access denied: SQL console queries on H2 must specify a dynamic table belonging to your account.");
        }
    }

    public QueryResult executeQuery(String sql) {
        return executeQuery(sql, null, null);
    }

    public QueryResult executeQuery(String sql, com.cloudpool.model.DatabaseConnection conn, com.cloudpool.model.User user) {
        long startTime = System.currentTimeMillis();
        QueryResult result;
        try {
            if (conn == null) {
                try {
                    validateH2Query(sql, user);
                } catch (SecurityException e) {
                    QueryResult errResult = new QueryResult();
                    errResult.setSuccess(false);
                    errResult.setColumns(Collections.singletonList("ERROR"));
                    errResult.setRows(Collections.singletonList(Map.of("ERROR", e.getMessage())));
                    errResult.setMessage(e.getMessage());
                    return errResult;
                }
            }

            String cleanSql = sql.trim().toUpperCase();
            JdbcTemplate targetTemplate = getJdbcTemplateForConnection(conn);

            if (cleanSql.startsWith("SELECT") || cleanSql.startsWith("SHOW") || cleanSql.startsWith("PRAGMA") || cleanSql.startsWith("EXPLAIN")) {
                result = targetTemplate.query(sql, rs -> {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(metaData.getColumnName(i));
                    }

                    List<Map<String, Object>> rows = new ArrayList<>();
                    int rowCount = 0;
                    while (rs.next() && rowCount < 100) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(metaData.getColumnName(i), rs.getObject(i));
                        }
                        rows.add(row);
                        rowCount++;
                    }

                    QueryResult res = new QueryResult();
                    res.setSuccess(true);
                    res.setColumns(columns);
                    res.setRows(rows);
                    res.setAffectedRows(rowCount);
                    res.setMessage("Query executed successfully. Returned " + rowCount + " rows.");
                    return res;
                });
            } else {
                int affectedRows = targetTemplate.update(sql);
                QueryResult okResult = new QueryResult();
                okResult.setSuccess(true);
                okResult.setColumns(Collections.singletonList("STATUS"));
                okResult.setRows(Collections.singletonList(Map.of("STATUS", "SUCCESS")));
                okResult.setAffectedRows(affectedRows);
                okResult.setMessage("Query executed successfully. Affected rows: " + affectedRows);
                result = okResult;
            }
        } catch (Exception e) {
            QueryResult errResult = new QueryResult();
            errResult.setSuccess(false);
            errResult.setColumns(Collections.singletonList("ERROR"));
            errResult.setRows(Collections.singletonList(Map.of("ERROR", e.getMessage())));
            errResult.setMessage(e.getMessage());
            result = errResult;
        } finally {
            metricsService.recordQueryTime(System.currentTimeMillis() - startTime);
        }
        return result;
    }

    @Data
    public static class QueryResult {
        private boolean success;
        private List<String> columns = new ArrayList<>();
        private List<Map<String, Object>> rows = new ArrayList<>();
        private int affectedRows;
        private String message;
    }
}

