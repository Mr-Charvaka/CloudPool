-- V16: Enable statement timeout to kill long-running queries
-- This prevents runaway analytical queries or unbounded scans from exhausting database resources

ALTER ROLE CURRENT_USER SET statement_timeout = '30s';
